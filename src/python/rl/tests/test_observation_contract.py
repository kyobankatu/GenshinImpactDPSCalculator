import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from binary_protocol import (
    ACTION_LAYOUT_REVISION,
    CAPABILITY_SCHEMA_REVISION,
    LOADOUT_SCHEMA_REVISION,
    OBSERVATION_SCHEMA_REVISION,
    PRIVILEGED_SCHEMA_REVISION,
)
from recurrent_ppo import (
    CHAR_FEATURE_SIZE,
    GLOBAL_FEATURE_SIZE,
    NUM_CHARS,
    PRIVILEGED_OBSERVATION_SIZE,
    RecurrentPolicy,
    validate_checkpoint_payload,
)


def test_loadout_aware_dimensions_and_checkpoint_schema(tmp_path):
    assert CHAR_FEATURE_SIZE == 70
    assert GLOBAL_FEATURE_SIZE == 7
    assert NUM_CHARS == 4
    assert PRIVILEGED_OBSERVATION_SIZE == 187
    observation_size = CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE
    assert observation_size == 287

    policy = RecurrentPolicy(
        observation_size=observation_size,
        hidden_size=16,
        action_size=11,
    )
    checkpoint = tmp_path / "loadout_policy.pt"
    policy.save(checkpoint)
    payload = torch.load(checkpoint, map_location="cpu")
    validate_checkpoint_payload(payload, checkpoint)
    assert payload["observation_schema_revision"] == OBSERVATION_SCHEMA_REVISION
    assert payload["privileged_schema_revision"] == PRIVILEGED_SCHEMA_REVISION
    assert payload["loadout_schema_revision"] == LOADOUT_SCHEMA_REVISION
    assert payload["capability_schema_revision"] == CAPABILITY_SCHEMA_REVISION


def test_stale_observation_schema_is_rejected():
    payload = {
        "policy_type": "gru",
        "observation_size": 287,
        "hidden_size": 16,
        "action_size": 11,
        "action_layout_revision": ACTION_LAYOUT_REVISION,
        "observation_schema_revision": OBSERVATION_SCHEMA_REVISION - 1,
        "privileged_schema_revision": PRIVILEGED_SCHEMA_REVISION,
        "loadout_schema_revision": LOADOUT_SCHEMA_REVISION,
        "capability_schema_revision": CAPABILITY_SCHEMA_REVISION,
        "char_feature_size": CHAR_FEATURE_SIZE,
        "global_feature_size": GLOBAL_FEATURE_SIZE,
        "num_chars": NUM_CHARS,
        "privileged_observation_size": PRIVILEGED_OBSERVATION_SIZE,
        "state_dict": {},
    }
    with pytest.raises(ValueError, match="observation_schema_revision"):
        validate_checkpoint_payload(payload, "stale_observation.pt")


def test_checkpoint_contract_cannot_be_overridden(tmp_path):
    policy = RecurrentPolicy(
        observation_size=287,
        hidden_size=16,
        action_size=11,
    )
    with pytest.raises(ValueError, match="may not replace"):
        policy.save(
            tmp_path / "invalid.pt",
            extra_state={"observation_schema_revision": 999},
        )
