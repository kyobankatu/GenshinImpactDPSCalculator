import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from binary_protocol import (
    ACTION_LAYOUT_REVISION,
    ACTION_NAMES,
    CAPABILITY_SCHEMA_REVISION,
    LOADOUT_SCHEMA_REVISION,
    OBSERVATION_SCHEMA_REVISION,
    PRIVILEGED_SCHEMA_REVISION,
    VERSION,
)
from recurrent_ppo import (
    ARCHITECTURE_REVISION,
    CHAR_FEATURE_SIZE,
    GLOBAL_FEATURE_SIZE,
    NUM_CHARS,
    PRIVILEGED_OBSERVATION_SIZE,
    RecurrentPolicy,
    validate_checkpoint_payload,
)


def test_versioned_action_layout_is_complete():
    assert VERSION == 12
    assert ACTION_LAYOUT_REVISION == 2
    assert ACTION_NAMES == (
        "NORMAL",
        "CHARGE",
        "PLUNGE",
        "SKILL_PRESS",
        "SKILL_HOLD",
        "BURST",
        "WAIT_SHORT",
        "SWAP_SLOT_0",
        "SWAP_SLOT_1",
        "SWAP_SLOT_2",
        "SWAP_SLOT_3",
    )


def test_checkpoint_records_action_layout_revision(tmp_path):
    policy = RecurrentPolicy(
        observation_size=CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE,
        hidden_size=16,
        action_size=len(ACTION_NAMES),
    )
    checkpoint = tmp_path / "policy.pt"
    policy.save(checkpoint)
    payload = torch.load(checkpoint, map_location="cpu")
    validate_checkpoint_payload(payload, checkpoint)
    assert payload["action_layout_revision"] == ACTION_LAYOUT_REVISION


def test_legacy_checkpoint_is_rejected():
    legacy = {
        "policy_type": "gru",
        "observation_size": CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE,
        "hidden_size": 16,
        "action_size": 7,
        "char_feature_size": CHAR_FEATURE_SIZE,
        "global_feature_size": GLOBAL_FEATURE_SIZE,
        "num_chars": NUM_CHARS,
        "privileged_observation_size": PRIVILEGED_OBSERVATION_SIZE,
        "state_dict": {},
    }
    with pytest.raises(ValueError, match="action_layout_revision"):
        validate_checkpoint_payload(legacy, "legacy.pt")


def test_stale_action_layout_revision_is_rejected():
    stale = {
        "policy_type": "gru",
        "observation_size": CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE,
        "hidden_size": 16,
        "action_size": 7,
        "action_layout_revision": 1,
        "architecture_revision": ARCHITECTURE_REVISION,
        "observation_schema_revision": OBSERVATION_SCHEMA_REVISION,
        "privileged_schema_revision": PRIVILEGED_SCHEMA_REVISION,
        "loadout_schema_revision": LOADOUT_SCHEMA_REVISION,
        "capability_schema_revision": CAPABILITY_SCHEMA_REVISION,
        "char_feature_size": CHAR_FEATURE_SIZE,
        "global_feature_size": GLOBAL_FEATURE_SIZE,
        "num_chars": NUM_CHARS,
        "privileged_observation_size": PRIVILEGED_OBSERVATION_SIZE,
        "state_dict": {},
    }
    with pytest.raises(ValueError, match="Unsupported action layout revision"):
        validate_checkpoint_payload(stale, "stale.pt")
