import torch
from torch import nn

from binary_protocol import (
    ACTION_LAYOUT_REVISION,
    CAPABILITY_SCHEMA_REVISION,
    LOADOUT_SCHEMA_REVISION,
    OBSERVATION_SCHEMA_REVISION,
    PRIVILEGED_SCHEMA_REVISION,
)


CHAR_FEATURE_SIZE = 70
GLOBAL_FEATURE_SIZE = 7
NUM_CHARS = 4
PRIVILEGED_OBSERVATION_SIZE = 187
PRIVILEGED_CHAR_FEATURE_SIZE = 46
PRIVILEGED_GLOBAL_FEATURE_SIZE = 3
GLOBAL_ACTION_COUNT = 7
WAIT_ACTION_ID = 6
SWAP_ACTION_OFFSET = 7
ARCHITECTURE_REVISION = 2


class SlotEquivariantBackbone(nn.Module):
    """Shared slot-equivariant actor, critic, policy, and auxiliary heads."""

    def __init__(
        self,
        char_feature_size,
        global_feature_size,
        num_chars,
        hidden_size,
        action_size,
        privileged_observation_size,
    ):
        super().__init__()
        expected_action_size = GLOBAL_ACTION_COUNT + num_chars
        if action_size != expected_action_size:
            raise ValueError(
                f"action_size mismatch: expected {expected_action_size}, got {action_size}"
            )
        expected_privileged_size = (
            PRIVILEGED_CHAR_FEATURE_SIZE * num_chars
            + PRIVILEGED_GLOBAL_FEATURE_SIZE
        )
        if privileged_observation_size != expected_privileged_size:
            raise ValueError(
                "privileged_observation_size mismatch: "
                f"expected {expected_privileged_size}, got {privileged_observation_size}"
            )
        if char_feature_size <= 1:
            raise ValueError("char_feature_size must include the active-character feature")

        self.char_feature_size = char_feature_size
        self.global_feature_size = global_feature_size
        self.num_chars = num_chars
        self.hidden_size = hidden_size
        self.action_size = action_size
        self.privileged_observation_size = privileged_observation_size

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

        self.privileged_char_encoder = nn.Sequential(
            nn.Linear(PRIVILEGED_CHAR_FEATURE_SIZE, hidden_size),
            nn.Tanh(),
        )
        self.privileged_global_encoder = nn.Sequential(
            nn.Linear(PRIVILEGED_GLOBAL_FEATURE_SIZE, hidden_size),
            nn.Tanh(),
        )
        self.privileged_projection = nn.Sequential(
            nn.Linear(hidden_size * 2, hidden_size),
            nn.Tanh(),
        )

        self.global_policy_head = nn.Linear(hidden_size, GLOBAL_ACTION_COUNT)
        self.swap_policy_head = nn.Sequential(
            nn.Linear(hidden_size * 2, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, 1),
        )
        self.auxiliary_char_head = nn.Sequential(
            nn.Linear(hidden_size * 2, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, PRIVILEGED_CHAR_FEATURE_SIZE),
        )
        self.auxiliary_global_head = nn.Linear(
            hidden_size,
            PRIVILEGED_GLOBAL_FEATURE_SIZE,
        )

    def encode_actor(self, observation):
        self.validate_matrix(observation, self.actor_observation_size, "observation")
        char_flat = observation[:, : self.num_chars * self.char_feature_size]
        global_obs = observation[:, self.num_chars * self.char_feature_size :]
        char_obs = char_flat.reshape(
            -1,
            self.num_chars,
            self.char_feature_size,
        )
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
        pooled = torch.bmm(attention_scores, char_encodings).squeeze(1)

        active_weights = char_obs[:, :, 1].clamp(min=0.0, max=1.0)
        active_total = active_weights.sum(dim=1, keepdim=True)
        active_encoding = (
            char_encodings * active_weights.unsqueeze(-1)
        ).sum(dim=1) / active_total.clamp(min=1.0)
        active_encoding = torch.where(
            active_total > 0.5,
            active_encoding,
            pooled,
        )
        invariant_input = torch.cat(
            [pooled, active_encoding, global_encoding],
            dim=-1,
        )
        return invariant_input, char_encodings, attention_scores.squeeze(1)

    def encode_privileged(self, privileged_observation, batch_size, device):
        if privileged_observation is None:
            privileged_observation = torch.zeros(
                batch_size,
                self.privileged_observation_size,
                dtype=torch.float32,
                device=device,
            )
        self.validate_matrix(
            privileged_observation,
            self.privileged_observation_size,
            "privileged_observation",
        )
        char_end = self.num_chars * PRIVILEGED_CHAR_FEATURE_SIZE
        privileged_chars = privileged_observation[:, :char_end].reshape(
            batch_size,
            self.num_chars,
            PRIVILEGED_CHAR_FEATURE_SIZE,
        )
        privileged_global = privileged_observation[:, char_end:]
        char_encoding = self.privileged_char_encoder(privileged_chars).mean(dim=1)
        global_encoding = self.privileged_global_encoder(privileged_global)
        return self.privileged_projection(
            torch.cat([char_encoding, global_encoding], dim=-1)
        )

    def policy_logits(self, hidden, char_encodings):
        global_logits = self.global_policy_head(hidden)
        expanded_hidden = hidden.unsqueeze(-2).expand(
            *hidden.shape[:-1],
            self.num_chars,
            self.hidden_size,
        )
        swap_logits = self.swap_policy_head(
            torch.cat([expanded_hidden, char_encodings], dim=-1)
        ).squeeze(-1)
        return torch.cat([global_logits, swap_logits], dim=-1)

    def auxiliary_prediction(self, hidden, char_encodings):
        expanded_hidden = hidden.unsqueeze(-2).expand(
            *hidden.shape[:-1],
            self.num_chars,
            self.hidden_size,
        )
        char_prediction = self.auxiliary_char_head(
            torch.cat([expanded_hidden, char_encodings], dim=-1)
        ).flatten(start_dim=-2)
        global_prediction = self.auxiliary_global_head(hidden)
        return torch.cat([char_prediction, global_prediction], dim=-1)

    def prepare_action_mask(self, action_mask, sequence_mask=None):
        if action_mask.ndim not in (2, 3):
            raise ValueError("action_mask must have batch or batch/sequence dimensions")
        if action_mask.shape[-1] != self.action_size:
            raise ValueError(
                f"action_mask width mismatch: expected {self.action_size}, "
                f"got {action_mask.shape[-1]}"
            )
        if not torch.isfinite(action_mask).all():
            raise ValueError("action_mask contains non-finite values")
        prepared = action_mask > 0.5
        has_legal_action = prepared.any(dim=-1)
        if sequence_mask is None:
            if not has_legal_action.all():
                raise ValueError("action_mask has no legal action")
            return prepared
        if sequence_mask.shape != action_mask.shape[:-1]:
            raise ValueError("sequence_mask shape does not match action_mask")
        active = sequence_mask > 0.5
        if not (has_legal_action | ~active).all():
            raise ValueError("active sequence step has no legal action")
        padded_without_action = ~has_legal_action & ~active
        if padded_without_action.any():
            prepared = prepared.clone()
            prepared[..., WAIT_ACTION_ID] |= padded_without_action
        return prepared

    def mask_logits(self, logits, prepared_mask):
        if prepared_mask.shape != logits.shape:
            raise ValueError(
                f"action_mask shape {tuple(prepared_mask.shape)} does not match "
                f"logits {tuple(logits.shape)}"
            )
        if not torch.isfinite(logits).all():
            raise ValueError("policy logits contain non-finite values")
        return logits.masked_fill(~prepared_mask, torch.finfo(logits.dtype).min)

    def validate_hidden(self, recurrent_state, batch_size):
        self.validate_matrix(recurrent_state, self.hidden_size, "recurrent_state")
        if recurrent_state.shape[0] != batch_size:
            raise ValueError("recurrent_state batch size does not match observation")

    @property
    def actor_observation_size(self):
        return self.char_feature_size * self.num_chars + self.global_feature_size

    @staticmethod
    def validate_matrix(value, width, name):
        if value.ndim != 2 or value.shape[-1] != width:
            raise ValueError(
                f"{name} shape mismatch: expected [batch, {width}], "
                f"got {tuple(value.shape)}"
            )
        if not torch.isfinite(value).all():
            raise ValueError(f"{name} contains non-finite values")


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

        self.equivariant = SlotEquivariantBackbone(
            char_feature_size,
            global_feature_size,
            num_chars,
            hidden_size,
            action_size,
            privileged_observation_size,
        )
        self.recurrent = nn.GRUCell(hidden_size * 3, hidden_size)
        self.value_head = nn.Linear(hidden_size * 2, 1)

    def forward_step(
        self,
        observation,
        recurrent_state,
        action_mask,
        privileged_observation=None,
    ):
        invariant_input, char_encodings, attention_scores = (
            self.equivariant.encode_actor(observation)
        )
        self.equivariant.validate_hidden(
            recurrent_state,
            observation.shape[0],
        )
        prepared_mask = self.equivariant.prepare_action_mask(action_mask)
        hidden = self.recurrent(invariant_input, recurrent_state)
        logits = self.equivariant.policy_logits(hidden, char_encodings)
        masked_logits = self.equivariant.mask_logits(logits, prepared_mask)
        privileged_encoding = self.equivariant.encode_privileged(
            privileged_observation,
            observation.shape[0],
            observation.device,
        )
        value = self.value_head(torch.cat([hidden, privileged_encoding], dim=-1)).squeeze(-1)
        auxiliary_prediction = self.equivariant.auxiliary_prediction(
            hidden,
            char_encodings,
        )
        return masked_logits, value, hidden, attention_scores, auxiliary_prediction

    def forward_sequence(
        self,
        observations,
        initial_hidden,
        action_masks,
        privileged_observations=None,
        sequence_mask=None,
    ):
        if observations.ndim != 3 or observations.shape[-1] != self.observation_size:
            raise ValueError("observations must match the policy sequence shape")
        if action_masks.shape[:2] != observations.shape[:2]:
            raise ValueError("action_masks sequence shape does not match observations")
        self.equivariant.validate_hidden(initial_hidden, observations.shape[0])
        prepared_masks = self.equivariant.prepare_action_mask(
            action_masks,
            sequence_mask,
        )
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
                prepared_masks[:, step_index].to(action_masks.dtype),
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
            "action_layout_revision": ACTION_LAYOUT_REVISION,
            "architecture_revision": ARCHITECTURE_REVISION,
            "observation_schema_revision": OBSERVATION_SCHEMA_REVISION,
            "privileged_schema_revision": PRIVILEGED_SCHEMA_REVISION,
            "loadout_schema_revision": LOADOUT_SCHEMA_REVISION,
            "capability_schema_revision": CAPABILITY_SCHEMA_REVISION,
            "char_feature_size": self.char_feature_size,
            "global_feature_size": self.global_feature_size,
            "num_chars": self.num_chars,
            "privileged_observation_size": self.privileged_observation_size,
            "state_dict": self.state_dict(),
        }
        if optimizer is not None:
            payload["optimizer_state_dict"] = optimizer.state_dict()
        if extra_state:
            conflicts = sorted(set(payload).intersection(extra_state))
            if conflicts:
                raise ValueError(f"extra_state may not replace checkpoint contract fields: {conflicts}")
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

        self.equivariant = SlotEquivariantBackbone(
            char_feature_size,
            global_feature_size,
            num_chars,
            hidden_size,
            action_size,
            privileged_observation_size,
        )
        self.token_projection = nn.Linear(hidden_size * 3, hidden_size)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=hidden_size,
            nhead=num_heads,
            dim_feedforward=hidden_size * 4,
            dropout=0.0,
            batch_first=True,
            activation="gelu",
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)

        self.value_head = nn.Linear(hidden_size * 2, 1)

    def _encode_token(self, observation):
        """Encode one timestep of observation into a single (B, hidden_size) token.

        Returns (token, attention_scores) where attention_scores have shape
        (B, num_chars).
        """
        invariant_input, char_encodings, attention_scores = (
            self.equivariant.encode_actor(observation)
        )
        token = self.token_projection(invariant_input)
        return token, attention_scores, char_encodings

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
        token, attention_scores, char_encodings = self._encode_token(observation)
        self.equivariant.validate_hidden(
            recurrent_state,
            observation.shape[0],
        )
        prepared_mask = self.equivariant.prepare_action_mask(action_mask)
        # Stack [summary, current] and run causal Transformer (length 2).
        # Position 1 attends to positions 0 (summary) and 1 (itself).
        sequence = torch.stack([recurrent_state, token], dim=1)
        causal_mask = self._causal_mask(2, observation.device)
        encoded = self.transformer(sequence, mask=causal_mask, is_causal=True)
        hidden = encoded[:, 1]
        logits = self.equivariant.policy_logits(hidden, char_encodings)
        masked_logits = self.equivariant.mask_logits(logits, prepared_mask)
        privileged_encoding = self.equivariant.encode_privileged(
            privileged_observation,
            observation.shape[0],
            observation.device,
        )
        value = self.value_head(torch.cat([hidden, privileged_encoding], dim=-1)).squeeze(-1)
        auxiliary_prediction = self.equivariant.auxiliary_prediction(
            hidden,
            char_encodings,
        )
        return masked_logits, value, hidden, attention_scores, auxiliary_prediction

    def forward_sequence(
        self,
        observations,
        initial_hidden,
        action_masks,
        privileged_observations=None,
        sequence_mask=None,
    ):
        if observations.ndim != 3 or observations.shape[-1] != self.observation_size:
            raise ValueError("observations must match the policy sequence shape")
        if action_masks.shape[:2] != observations.shape[:2]:
            raise ValueError("action_masks sequence shape does not match observations")
        batch_size, seq_len, _ = observations.shape
        device = observations.device
        self.equivariant.validate_hidden(initial_hidden, batch_size)
        prepared_masks = self.equivariant.prepare_action_mask(
            action_masks,
            sequence_mask,
        )

        # Encode all observations into tokens (B, T, H) plus per-step attention scores.
        flat_obs = observations.reshape(batch_size * seq_len, observations.shape[-1])
        flat_token, flat_attn, flat_char_encodings = self._encode_token(flat_obs)
        tokens = flat_token.reshape(batch_size, seq_len, self.hidden_size)
        attention_steps = flat_attn.reshape(batch_size, seq_len, self.num_chars)
        char_encodings = flat_char_encodings.reshape(
            batch_size,
            seq_len,
            self.num_chars,
            self.hidden_size,
        )

        # Prepend the initial summary token. Resulting length = seq_len + 1.
        summary = initial_hidden.unsqueeze(1)
        full_sequence = torch.cat([summary, tokens], dim=1)
        causal_mask = self._causal_mask(seq_len + 1, device)
        encoded = self.transformer(full_sequence, mask=causal_mask, is_causal=True)
        # Skip position 0 (the summary slot) for per-step outputs.
        per_step_hidden = encoded[:, 1:]  # (B, T, H)

        logits = self.equivariant.policy_logits(per_step_hidden, char_encodings)
        masked_logits = self.equivariant.mask_logits(logits, prepared_masks)

        if privileged_observations is None:
            privileged_step_encoding = self.equivariant.encode_privileged(
                None, batch_size * seq_len, device
            ).reshape(batch_size, seq_len, self.hidden_size)
        else:
            flat_privileged = privileged_observations.reshape(
                batch_size * seq_len,
                self.privileged_observation_size,
            )
            privileged_step_encoding = self.equivariant.encode_privileged(
                flat_privileged,
                batch_size * seq_len,
                device,
            ).reshape(batch_size, seq_len, self.hidden_size)
        value = self.value_head(
            torch.cat([per_step_hidden, privileged_step_encoding], dim=-1)
        ).squeeze(-1)
        auxiliary_prediction = self.equivariant.auxiliary_prediction(
            per_step_hidden,
            char_encodings,
        )

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
            "action_layout_revision": ACTION_LAYOUT_REVISION,
            "architecture_revision": ARCHITECTURE_REVISION,
            "observation_schema_revision": OBSERVATION_SCHEMA_REVISION,
            "privileged_schema_revision": PRIVILEGED_SCHEMA_REVISION,
            "loadout_schema_revision": LOADOUT_SCHEMA_REVISION,
            "capability_schema_revision": CAPABILITY_SCHEMA_REVISION,
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
            conflicts = sorted(set(payload).intersection(extra_state))
            if conflicts:
                raise ValueError(f"extra_state may not replace checkpoint contract fields: {conflicts}")
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
        "action_layout_revision",
        "architecture_revision",
        "observation_schema_revision",
        "privileged_schema_revision",
        "loadout_schema_revision",
        "capability_schema_revision",
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
    if payload["action_layout_revision"] != ACTION_LAYOUT_REVISION:
        raise ValueError(
            "Unsupported action layout revision in checkpoint: "
            f"expected={ACTION_LAYOUT_REVISION} got={payload['action_layout_revision']}"
        )
    if payload["architecture_revision"] != ARCHITECTURE_REVISION:
        raise ValueError(
            "Unsupported architecture revision in checkpoint: "
            f"expected={ARCHITECTURE_REVISION} got={payload['architecture_revision']}"
        )
    expected_schemas = {
        "observation_schema_revision": OBSERVATION_SCHEMA_REVISION,
        "privileged_schema_revision": PRIVILEGED_SCHEMA_REVISION,
        "loadout_schema_revision": LOADOUT_SCHEMA_REVISION,
        "capability_schema_revision": CAPABILITY_SCHEMA_REVISION,
    }
    for field, expected in expected_schemas.items():
        if payload[field] != expected:
            raise ValueError(
                f"Unsupported {field} in checkpoint: expected={expected} got={payload[field]}"
            )
    expected_dimensions = {
        "observation_size": CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE,
        "action_size": GLOBAL_ACTION_COUNT + NUM_CHARS,
        "char_feature_size": CHAR_FEATURE_SIZE,
        "global_feature_size": GLOBAL_FEATURE_SIZE,
        "num_chars": NUM_CHARS,
        "privileged_observation_size": PRIVILEGED_OBSERVATION_SIZE,
    }
    for field, expected in expected_dimensions.items():
        if payload[field] != expected:
            raise ValueError(
                f"Unsupported {field} for observation schema: expected={expected} got={payload[field]}"
            )


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
