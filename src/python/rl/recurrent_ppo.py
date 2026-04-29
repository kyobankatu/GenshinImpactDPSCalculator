import torch
from torch import nn


CHAR_FEATURE_SIZE = 18
GLOBAL_FEATURE_SIZE = 7
NUM_CHARS = 4


class RecurrentPolicy(nn.Module):
    def __init__(
        self,
        observation_size,
        hidden_size,
        action_size,
        char_feature_size=CHAR_FEATURE_SIZE,
        global_feature_size=GLOBAL_FEATURE_SIZE,
        num_chars=NUM_CHARS,
    ):
        super().__init__()
        self.observation_size = observation_size
        self.hidden_size = hidden_size
        self.action_size = action_size
        self.char_feature_size = char_feature_size
        self.global_feature_size = global_feature_size
        self.num_chars = num_chars

        expected_observation_size = char_feature_size * num_chars + global_feature_size
        if observation_size != expected_observation_size:
            raise ValueError(
                "observation_size mismatch: "
                f"expected {expected_observation_size}, got {observation_size}"
            )

        self.char_encoder = nn.Sequential(
            nn.Linear(char_feature_size, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, hidden_size),
            nn.Tanh(),
        )
        self.global_encoder = nn.Sequential(
            nn.Linear(global_feature_size, hidden_size),
            nn.Tanh(),
        )
        self.attention_query = nn.Linear(hidden_size, hidden_size)
        self.attention_scale = hidden_size ** -0.5
        self.recurrent = nn.GRUCell(hidden_size * 2, hidden_size)
        self.policy_head = nn.Linear(hidden_size, action_size)
        self.value_head = nn.Linear(hidden_size, 1)

    def _split_obs(self, observation):
        char_flat = observation[:, : self.num_chars * self.char_feature_size]
        global_obs = observation[:, self.num_chars * self.char_feature_size :]
        char_obs = char_flat.reshape(-1, self.num_chars, self.char_feature_size)
        return char_obs, global_obs

    def _encode(self, observation):
        char_obs, global_obs = self._split_obs(observation)
        batch_size = char_obs.shape[0]

        char_encodings = self.char_encoder(
            char_obs.reshape(batch_size * self.num_chars, self.char_feature_size)
        ).reshape(batch_size, self.num_chars, self.hidden_size)
        global_encoding = self.global_encoder(global_obs)

        query = self.attention_query(global_encoding).unsqueeze(1)
        attention_scores = torch.softmax(
            torch.bmm(query, char_encodings.transpose(1, 2)) * self.attention_scale,
            dim=-1,
        )
        context = torch.bmm(attention_scores, char_encodings).squeeze(1)
        return context, global_encoding, attention_scores.squeeze(1)

    def forward_step(self, observation, recurrent_state, action_mask):
        context, global_encoding, attention_scores = self._encode(observation)
        gru_input = torch.cat([context, global_encoding], dim=-1)
        hidden = self.recurrent(gru_input, recurrent_state)
        logits = self.policy_head(hidden)
        masked_logits = logits.masked_fill(action_mask < 0.5, -1.0e9)
        value = self.value_head(hidden).squeeze(-1)
        return masked_logits, value, hidden, attention_scores

    def act(self, observation, recurrent_state, action_mask, deterministic=False):
        logits, value, hidden, attention_scores = self.forward_step(
            observation, recurrent_state, action_mask
        )
        distribution = torch.distributions.Categorical(logits=logits)
        probabilities = torch.softmax(logits, dim=-1)
        action = torch.argmax(logits, dim=-1) if deterministic else distribution.sample()
        log_probability = distribution.log_prob(action)
        entropy = distribution.entropy()
        return {
            "action": action,
            "log_probability": log_probability,
            "value": value,
            "hidden": hidden,
            "entropy": entropy,
            "probabilities": probabilities,
            "top_probability": probabilities.max(dim=-1).values,
            "attention_scores": attention_scores,
        }

    def save(self, path, optimizer=None, extra_state=None):
        payload = {
            "observation_size": self.observation_size,
            "hidden_size": self.hidden_size,
            "action_size": self.action_size,
            "char_feature_size": self.char_feature_size,
            "global_feature_size": self.global_feature_size,
            "num_chars": self.num_chars,
            "state_dict": self.state_dict(),
        }
        if optimizer is not None:
            payload["optimizer_state_dict"] = optimizer.state_dict()
        if extra_state:
            payload.update(extra_state)
        torch.save(payload, path)

    @classmethod
    def load(cls, path, map_location="cpu"):
        payload = cls.load_payload(path, map_location=map_location)
        model = cls(
            payload["observation_size"],
            payload["hidden_size"],
            payload["action_size"],
            char_feature_size=payload.get("char_feature_size", CHAR_FEATURE_SIZE),
            global_feature_size=payload.get("global_feature_size", GLOBAL_FEATURE_SIZE),
            num_chars=payload.get("num_chars", NUM_CHARS),
        )
        model.load_state_dict(payload["state_dict"])
        return model, payload.get("optimizer_state_dict")

    @staticmethod
    def load_payload(path, map_location="cpu"):
        return torch.load(path, map_location=map_location)


def compute_advantages(segments, gamma, gae_lambda):
    samples = []
    for segment in segments:
        next_value = segment["bootstrap_value"]
        gae = 0.0
        local = []
        for step in reversed(segment["steps"]):
            non_terminal = 0.0 if step["done"] else 1.0
            delta = step["reward"] + gamma * next_value * non_terminal - step["value"]
            gae = delta + gamma * gae_lambda * non_terminal * gae
            local.append(
                {
                    "observation": step["observation"],
                    "recurrent_input": step["recurrent_input"],
                    "action_mask": step["action_mask"],
                    "action": step["action"],
                    "old_log_probability": step["old_log_probability"],
                    "old_value": step["value"],
                    "advantage": gae,
                    "return_target": gae + step["value"],
                }
            )
            next_value = step["value"]
        local.reverse()
        samples.extend(local)

    if not samples:
        return samples

    advantages = torch.tensor([sample["advantage"] for sample in samples], dtype=torch.float32)
    normalized = (advantages - advantages.mean()) / advantages.std().clamp_min(1e-8)
    for sample, advantage in zip(samples, normalized.tolist()):
        sample["advantage"] = advantage
    return samples
