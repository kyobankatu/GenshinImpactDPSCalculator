import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from recurrent_ppo import (
    ARCHITECTURE_REVISION,
    CHAR_FEATURE_SIZE,
    GLOBAL_ACTION_COUNT,
    GLOBAL_FEATURE_SIZE,
    NUM_CHARS,
    RecurrentPolicy,
    TransformerPolicy,
    validate_checkpoint_payload,
)


OBSERVATION_SIZE = CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE
ACTION_SIZE = GLOBAL_ACTION_COUNT + NUM_CHARS


@pytest.mark.parametrize("policy_class", [RecurrentPolicy, TransformerPolicy])
def test_checkpoint_round_trip_records_equivariant_architecture(policy_class, tmp_path):
    policy = policy_class(OBSERVATION_SIZE, 16, ACTION_SIZE)
    checkpoint = tmp_path / f"{policy_class.__name__}.pt"
    policy.save(checkpoint)
    payload = torch.load(checkpoint, map_location="cpu")
    validate_checkpoint_payload(payload, checkpoint)
    assert payload["architecture_revision"] == ARCHITECTURE_REVISION


def test_legacy_architecture_checkpoint_is_rejected(tmp_path):
    policy = RecurrentPolicy(OBSERVATION_SIZE, 16, ACTION_SIZE)
    checkpoint = tmp_path / "current.pt"
    policy.save(checkpoint)
    payload = torch.load(checkpoint, map_location="cpu")
    payload["architecture_revision"] = ARCHITECTURE_REVISION - 1
    with pytest.raises(ValueError, match="architecture revision"):
        validate_checkpoint_payload(payload, "legacy_architecture.pt")


@pytest.mark.parametrize("policy_class", [RecurrentPolicy, TransformerPolicy])
def test_constructor_rejects_incompatible_dimensions(policy_class):
    with pytest.raises(ValueError, match="observation_size mismatch"):
        policy_class(OBSERVATION_SIZE - 1, 16, ACTION_SIZE)
    with pytest.raises(ValueError, match="action_size mismatch"):
        policy_class(OBSERVATION_SIZE, 16, ACTION_SIZE - 1)
