import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from binary_protocol import ACTION_LAYOUT_REVISION, ACTION_NAMES, VERSION
from recurrent_ppo import RecurrentPolicy, validate_checkpoint_payload


def test_versioned_action_layout_is_complete():
    assert VERSION == 11
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
        observation_size=115,
        hidden_size=16,
        action_size=len(ACTION_NAMES),
        char_feature_size=27,
        global_feature_size=7,
        num_chars=4,
        privileged_observation_size=23,
    )
    checkpoint = tmp_path / "policy.pt"
    policy.save(checkpoint)
    payload = torch.load(checkpoint, map_location="cpu")
    validate_checkpoint_payload(payload, checkpoint)
    assert payload["action_layout_revision"] == ACTION_LAYOUT_REVISION


def test_legacy_checkpoint_is_rejected():
    legacy = {
        "policy_type": "gru",
        "observation_size": 115,
        "hidden_size": 16,
        "action_size": 7,
        "char_feature_size": 27,
        "global_feature_size": 7,
        "num_chars": 4,
        "privileged_observation_size": 23,
        "state_dict": {},
    }
    with pytest.raises(ValueError, match="action_layout_revision"):
        validate_checkpoint_payload(legacy, "legacy.pt")


def test_stale_action_layout_revision_is_rejected():
    stale = {
        "policy_type": "gru",
        "observation_size": 115,
        "hidden_size": 16,
        "action_size": 7,
        "action_layout_revision": 1,
        "char_feature_size": 27,
        "global_feature_size": 7,
        "num_chars": 4,
        "privileged_observation_size": 23,
        "state_dict": {},
    }
    with pytest.raises(ValueError, match="Unsupported action layout revision"):
        validate_checkpoint_payload(stale, "stale.pt")
