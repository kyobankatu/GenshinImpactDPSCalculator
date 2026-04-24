import torch
from torch import nn


class RecurrentPolicy(nn.Module):
    def __init__(self, observation_size, hidden_size, action_size):
        super().__init__()
        self.observation_size = observation_size
        self.hidden_size = hidden_size
        self.action_size = action_size

        self.encoder = nn.Sequential(
            nn.Linear(observation_size, hidden_size),
            nn.Tanh(),
        )
        self.recurrent = nn.GRUCell(hidden_size, hidden_size)
        self.policy_head = nn.Linear(hidden_size, action_size)
        self.value_head = nn.Linear(hidden_size, 1)

    def forward_step(self, observation, recurrent_state, action_mask):
        encoded = self.encoder(observation)
        hidden = self.recurrent(encoded, recurrent_state)
        logits = self.policy_head(hidden)
        masked_logits = logits.masked_fill(action_mask < 0.5, -1.0e9)
        value = self.value_head(hidden).squeeze(-1)
        return masked_logits, value, hidden

    def act(self, observation, recurrent_state, action_mask, deterministic=False):
        logits, value, hidden = self.forward_step(observation, recurrent_state, action_mask)
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
        }

    def save(self, path, optimizer=None):
        payload = {
            "observation_size": self.observation_size,
            "hidden_size": self.hidden_size,
            "action_size": self.action_size,
            "state_dict": self.state_dict(),
        }
        if optimizer is not None:
            payload["optimizer_state_dict"] = optimizer.state_dict()
        torch.save(payload, path)

    @classmethod
    def load(cls, path, map_location="cpu"):
        payload = torch.load(path, map_location=map_location)
        model = cls(payload["observation_size"], payload["hidden_size"], payload["action_size"])
        model.load_state_dict(payload["state_dict"])
        return model, payload.get("optimizer_state_dict")


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
