import torch
from torch import nn


CHAR_FEATURE_SIZE = 29
GLOBAL_FEATURE_SIZE = 7
NUM_CHARS = 4
PRIVILEGED_OBSERVATION_SIZE = 23


class RecurrentPolicy(nn.Module):
    def __init__(
        self,
        observation_size,
        hidden_size,
        action_size,
        char_feature_size=CHAR_FEATURE_SIZE,
        global_feature_size=GLOBAL_FEATURE_SIZE,
        num_chars=NUM_CHARS,
        privileged_observation_size=PRIVILEGED_OBSERVATION_SIZE,
    ):
        super().__init__()
        self.observation_size = observation_size
        self.hidden_size = hidden_size
        self.action_size = action_size
        self.char_feature_size = char_feature_size
        self.global_feature_size = global_feature_size
        self.num_chars = num_chars
        self.privileged_observation_size = privileged_observation_size

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
        self.critic_privileged_encoder = nn.Sequential(
            nn.Linear(privileged_observation_size, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, hidden_size),
            nn.Tanh(),
        )
        self.value_head = nn.Linear(hidden_size * 2, 1)
        self.auxiliary_head = nn.Sequential(
            nn.Linear(hidden_size, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, privileged_observation_size),
        )

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

    def _encode_privileged(self, privileged_observation, batch_size, device):
        if privileged_observation is None:
            privileged_observation = torch.zeros(
                batch_size,
                self.privileged_observation_size,
                dtype=torch.float32,
                device=device,
            )
        return self.critic_privileged_encoder(privileged_observation)

    def forward_step(
        self,
        observation,
        recurrent_state,
        action_mask,
        privileged_observation=None,
    ):
        context, global_encoding, attention_scores = self._encode(observation)
        gru_input = torch.cat([context, global_encoding], dim=-1)
        hidden = self.recurrent(gru_input, recurrent_state)
        logits = self.policy_head(hidden)
        masked_logits = logits.masked_fill(action_mask < 0.5, -1.0e9)
        privileged_encoding = self._encode_privileged(
            privileged_observation,
            observation.shape[0],
            observation.device,
        )
        value = self.value_head(torch.cat([hidden, privileged_encoding], dim=-1)).squeeze(-1)
        auxiliary_prediction = self.auxiliary_head(hidden)
        return masked_logits, value, hidden, attention_scores, auxiliary_prediction

    def forward_sequence(
        self,
        observations,
        initial_hidden,
        action_masks,
        privileged_observations=None,
        sequence_mask=None,
    ):
        hidden = initial_hidden
        logits_steps = []
        value_steps = []
        attention_steps = []
        auxiliary_steps = []

        for step_index in range(observations.shape[1]):
            privileged_step = (
                None if privileged_observations is None else privileged_observations[:, step_index]
            )
            logits, value, next_hidden, attention_scores, auxiliary_prediction = self.forward_step(
                observations[:, step_index],
                hidden,
                action_masks[:, step_index],
                privileged_observation=privileged_step,
            )
            if sequence_mask is not None:
                valid = (sequence_mask[:, step_index] > 0.5).unsqueeze(-1)
                hidden = torch.where(valid, next_hidden, hidden)
            else:
                hidden = next_hidden
            logits_steps.append(logits)
            value_steps.append(value)
            attention_steps.append(attention_scores)
            auxiliary_steps.append(auxiliary_prediction)

        return (
            torch.stack(logits_steps, dim=1),
            torch.stack(value_steps, dim=1),
            hidden,
            torch.stack(attention_steps, dim=1),
            torch.stack(auxiliary_steps, dim=1),
        )

    def act(self, observation, recurrent_state, action_mask, deterministic=False):
        logits, value, hidden, attention_scores, _ = self.forward_step(
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
            "policy_type": "gru",
            "observation_size": self.observation_size,
            "hidden_size": self.hidden_size,
            "action_size": self.action_size,
            "char_feature_size": self.char_feature_size,
            "global_feature_size": self.global_feature_size,
            "num_chars": self.num_chars,
            "privileged_observation_size": self.privileged_observation_size,
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
        if payload["policy_type"] != "gru":
            raise ValueError(
                "Checkpoint is not a RecurrentPolicy "
                f"(got policy_type={payload['policy_type']})"
            )
        model = cls(
            payload["observation_size"],
            payload["hidden_size"],
            payload["action_size"],
            char_feature_size=payload.get("char_feature_size", CHAR_FEATURE_SIZE),
            global_feature_size=payload.get("global_feature_size", GLOBAL_FEATURE_SIZE),
            num_chars=payload.get("num_chars", NUM_CHARS),
            privileged_observation_size=payload.get("privileged_observation_size", PRIVILEGED_OBSERVATION_SIZE),
        )
        model.load_state_dict(payload["state_dict"])
        return model, payload.get("optimizer_state_dict")

    @staticmethod
    def load_payload(path, map_location="cpu"):
        payload = torch.load(path, map_location=map_location)
        validate_checkpoint_payload(payload, path)
        return payload


class TransformerPolicy(nn.Module):
    """Transformer-based policy with summary-token cross-chunk continuity.

    Maintains the same external interface as RecurrentPolicy: the "recurrent
    state" is a single (B, hidden_size) summary token. During forward_sequence
    the summary is treated as token 0 and observation tokens follow; causal
    masking lets each position attend to the summary plus all earlier tokens
    in the chunk. The last sequence position's output becomes the next
    summary, providing GRU-like cross-chunk continuity through a single
    bottleneck token while gaining full attention within a chunk.
    """

    def __init__(
        self,
        observation_size,
        hidden_size,
        action_size,
        char_feature_size=CHAR_FEATURE_SIZE,
        global_feature_size=GLOBAL_FEATURE_SIZE,
        num_chars=NUM_CHARS,
        privileged_observation_size=PRIVILEGED_OBSERVATION_SIZE,
        num_layers=2,
        num_heads=4,
    ):
        super().__init__()
        self.observation_size = observation_size
        self.hidden_size = hidden_size
        self.action_size = action_size
        self.char_feature_size = char_feature_size
        self.global_feature_size = global_feature_size
        self.num_chars = num_chars
        self.privileged_observation_size = privileged_observation_size
        self.num_layers = num_layers
        self.num_heads = num_heads

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
        # token = concat(context, global) projected to hidden_size
        self.token_projection = nn.Linear(hidden_size * 2, hidden_size)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=hidden_size,
            nhead=num_heads,
            dim_feedforward=hidden_size * 4,
            dropout=0.0,
            batch_first=True,
            activation="gelu",
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)

        self.policy_head = nn.Linear(hidden_size, action_size)
        self.critic_privileged_encoder = nn.Sequential(
            nn.Linear(privileged_observation_size, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, hidden_size),
            nn.Tanh(),
        )
        self.value_head = nn.Linear(hidden_size * 2, 1)
        self.auxiliary_head = nn.Sequential(
            nn.Linear(hidden_size, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, privileged_observation_size),
        )

    def _split_obs(self, observation):
        char_flat = observation[..., : self.num_chars * self.char_feature_size]
        global_obs = observation[..., self.num_chars * self.char_feature_size :]
        leading = observation.shape[:-1]
        char_obs = char_flat.reshape(*leading, self.num_chars, self.char_feature_size)
        return char_obs, global_obs

    def _encode_token(self, observation):
        """Encode one timestep of observation into a single (B, hidden_size) token.

        Returns (token, attention_scores) where attention_scores have shape
        (B, num_chars).
        """
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
        token = self.token_projection(torch.cat([context, global_encoding], dim=-1))
        return token, attention_scores.squeeze(1), global_encoding

    def _encode_privileged(self, privileged_observation, batch_size, device):
        if privileged_observation is None:
            privileged_observation = torch.zeros(
                batch_size,
                self.privileged_observation_size,
                dtype=torch.float32,
                device=device,
            )
        return self.critic_privileged_encoder(privileged_observation)

    @staticmethod
    def _causal_mask(length, device):
        mask = torch.full((length, length), float("-inf"), device=device)
        return torch.triu(mask, diagonal=1)

    def forward_step(
        self,
        observation,
        recurrent_state,
        action_mask,
        privileged_observation=None,
    ):
        token, attention_scores, _ = self._encode_token(observation)
        # Stack [summary, current] and run causal Transformer (length 2).
        # Position 1 attends to positions 0 (summary) and 1 (itself).
        sequence = torch.stack([recurrent_state, token], dim=1)
        causal_mask = self._causal_mask(2, observation.device)
        encoded = self.transformer(sequence, mask=causal_mask, is_causal=True)
        hidden = encoded[:, 1]
        logits = self.policy_head(hidden)
        masked_logits = logits.masked_fill(action_mask < 0.5, -1.0e9)
        privileged_encoding = self._encode_privileged(
            privileged_observation,
            observation.shape[0],
            observation.device,
        )
        value = self.value_head(torch.cat([hidden, privileged_encoding], dim=-1)).squeeze(-1)
        auxiliary_prediction = self.auxiliary_head(hidden)
        return masked_logits, value, hidden, attention_scores, auxiliary_prediction

    def forward_sequence(
        self,
        observations,
        initial_hidden,
        action_masks,
        privileged_observations=None,
        sequence_mask=None,
    ):
        batch_size, seq_len, _ = observations.shape
        device = observations.device

        # Encode all observations into tokens (B, T, H) plus per-step attention scores.
        flat_obs = observations.reshape(batch_size * seq_len, observations.shape[-1])
        flat_token, flat_attn, _ = self._encode_token(flat_obs)
        tokens = flat_token.reshape(batch_size, seq_len, self.hidden_size)
        attention_steps = flat_attn.reshape(batch_size, seq_len, self.num_chars)

        # Prepend the initial summary token. Resulting length = seq_len + 1.
        summary = initial_hidden.unsqueeze(1)
        full_sequence = torch.cat([summary, tokens], dim=1)
        causal_mask = self._causal_mask(seq_len + 1, device)
        encoded = self.transformer(full_sequence, mask=causal_mask, is_causal=True)
        # Skip position 0 (the summary slot) for per-step outputs.
        per_step_hidden = encoded[:, 1:]  # (B, T, H)

        logits = self.policy_head(per_step_hidden)
        masked_logits = logits.masked_fill(action_masks < 0.5, -1.0e9)

        if privileged_observations is None:
            privileged_step_encoding = self._encode_privileged(
                None, batch_size * seq_len, device
            ).reshape(batch_size, seq_len, self.hidden_size)
        else:
            privileged_step_encoding = self.critic_privileged_encoder(
                privileged_observations
            )
        value = self.value_head(
            torch.cat([per_step_hidden, privileged_step_encoding], dim=-1)
        ).squeeze(-1)
        auxiliary_prediction = self.auxiliary_head(per_step_hidden)

        # New summary token: last per-step output. If sequence_mask is given,
        # use the last *valid* position so padding doesn't poison the carry.
        if sequence_mask is None:
            final_hidden = per_step_hidden[:, -1]
        else:
            valid_lengths = sequence_mask.sum(dim=1).long().clamp(min=1)
            indices = (valid_lengths - 1).unsqueeze(-1).unsqueeze(-1).expand(
                -1, 1, self.hidden_size
            )
            final_hidden = per_step_hidden.gather(1, indices).squeeze(1)

        return masked_logits, value, final_hidden, attention_steps, auxiliary_prediction

    def act(self, observation, recurrent_state, action_mask, deterministic=False):
        logits, value, hidden, attention_scores, _ = self.forward_step(
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
            "policy_type": "transformer",
            "observation_size": self.observation_size,
            "hidden_size": self.hidden_size,
            "action_size": self.action_size,
            "char_feature_size": self.char_feature_size,
            "global_feature_size": self.global_feature_size,
            "num_chars": self.num_chars,
            "privileged_observation_size": self.privileged_observation_size,
            "num_layers": self.num_layers,
            "num_heads": self.num_heads,
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
        if payload["policy_type"] != "transformer":
            raise ValueError(
                "Checkpoint is not a TransformerPolicy "
                f"(got policy_type={payload['policy_type']})"
            )
        model = cls(
            payload["observation_size"],
            payload["hidden_size"],
            payload["action_size"],
            char_feature_size=payload.get("char_feature_size", CHAR_FEATURE_SIZE),
            global_feature_size=payload.get("global_feature_size", GLOBAL_FEATURE_SIZE),
            num_chars=payload.get("num_chars", NUM_CHARS),
            privileged_observation_size=payload.get("privileged_observation_size", PRIVILEGED_OBSERVATION_SIZE),
            num_layers=payload.get("num_layers", 2),
            num_heads=payload.get("num_heads", 4),
        )
        model.load_state_dict(payload["state_dict"])
        return model, payload.get("optimizer_state_dict")

    @staticmethod
    def load_payload(path, map_location="cpu"):
        payload = torch.load(path, map_location=map_location)
        validate_checkpoint_payload(payload, path)
        return payload


def validate_checkpoint_payload(payload, path=None):
    required_fields = [
        "policy_type",
        "observation_size",
        "hidden_size",
        "action_size",
        "char_feature_size",
        "global_feature_size",
        "num_chars",
        "privileged_observation_size",
        "state_dict",
    ]
    missing = [field for field in required_fields if field not in payload]
    if missing:
        location = f" checkpoint {path}" if path else " checkpoint"
        raise ValueError(f"Missing required metadata in{location}: {missing}")
    if payload["policy_type"] not in ("gru", "transformer"):
        raise ValueError(f"Unsupported policy_type in checkpoint: {payload['policy_type']!r}")


def build_policy(policy_type, *args, **kwargs):
    if policy_type == "gru":
        return RecurrentPolicy(*args, **kwargs)
    if policy_type == "transformer":
        return TransformerPolicy(*args, **kwargs)
    raise ValueError(f"unknown policy_type: {policy_type!r} (expected 'gru' or 'transformer')")


def load_policy(path, map_location="cpu"):
    payload = torch.load(path, map_location=map_location)
    validate_checkpoint_payload(payload, path)
    policy_type = payload["policy_type"]
    if policy_type == "transformer":
        return TransformerPolicy.load(path, map_location=map_location)
    return RecurrentPolicy.load(path, map_location=map_location)


def compute_advantages(segments, gamma, gae_lambda):
    steps = []
    for segment in segments:
        next_value = segment["bootstrap_value"]
        gae = 0.0
        for step in reversed(segment["steps"]):
            non_terminal = 0.0 if step["done"] else 1.0
            delta = step["reward"] + gamma * next_value * non_terminal - step["value"]
            gae = delta + gamma * gae_lambda * non_terminal * gae
            step["advantage"] = gae
            step["return_target"] = gae + step["value"]
            steps.append(step)
            next_value = step["value"]

    if not steps:
        return segments

    advantages = torch.tensor([step["advantage"] for step in steps], dtype=torch.float32)
    normalized = (advantages - advantages.mean()) / advantages.std(unbiased=False).clamp_min(1e-8)
    for step, advantage in zip(steps, normalized.tolist()):
        step["advantage"] = advantage
    return segments
