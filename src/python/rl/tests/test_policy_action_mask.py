import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from recurrent_ppo import (
    CHAR_FEATURE_SIZE,
    GLOBAL_ACTION_COUNT,
    GLOBAL_FEATURE_SIZE,
    NUM_CHARS,
    WAIT_ACTION_ID,
    RecurrentPolicy,
    TransformerPolicy,
)


OBSERVATION_SIZE = CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE
ACTION_SIZE = GLOBAL_ACTION_COUNT + NUM_CHARS


@pytest.mark.parametrize("policy_class", [RecurrentPolicy, TransformerPolicy])
def test_masked_actions_have_zero_probability_and_wait_fallback(policy_class):
    policy = policy_class(OBSERVATION_SIZE, 16, ACTION_SIZE).eval()
    observation = torch.zeros(2, OBSERVATION_SIZE)
    hidden = torch.zeros(2, 16)
    mask = torch.zeros(2, ACTION_SIZE)
    mask[:, WAIT_ACTION_ID] = 1.0

    result = policy.act(observation, hidden, mask, deterministic=False)
    expected = torch.zeros_like(result["probabilities"])
    expected[:, WAIT_ACTION_ID] = 1.0
    torch.testing.assert_close(result["probabilities"], expected)
    assert torch.equal(result["action"], torch.full((2,), WAIT_ACTION_ID))


@pytest.mark.parametrize("policy_class", [RecurrentPolicy, TransformerPolicy])
def test_invalid_inference_inputs_fail_before_sampling(policy_class):
    policy = policy_class(OBSERVATION_SIZE, 16, ACTION_SIZE).eval()
    observation = torch.zeros(2, OBSERVATION_SIZE)
    hidden = torch.zeros(2, 16)
    mask = torch.ones(2, ACTION_SIZE)

    with pytest.raises(ValueError, match="no legal action"):
        policy.act(observation, hidden, torch.zeros_like(mask))
    with pytest.raises(ValueError, match="observation shape mismatch"):
        policy.act(observation[:, :-1], hidden, mask)
    with pytest.raises(ValueError, match="action_mask width mismatch"):
        policy.act(observation, hidden, mask[:, :-1])
    invalid_hidden = hidden.clone()
    invalid_hidden[0, 0] = float("nan")
    with pytest.raises(ValueError, match="non-finite"):
        policy.act(observation, invalid_hidden, mask)


@pytest.mark.parametrize("policy_class", [RecurrentPolicy, TransformerPolicy])
def test_padding_may_use_internal_wait_but_active_steps_may_not(policy_class):
    policy = policy_class(OBSERVATION_SIZE, 16, ACTION_SIZE).eval()
    observations = torch.zeros(1, 2, OBSERVATION_SIZE)
    hidden = torch.zeros(1, 16)
    masks = torch.zeros(1, 2, ACTION_SIZE)
    masks[:, 0, WAIT_ACTION_ID] = 1.0
    sequence_mask = torch.tensor([[1.0, 0.0]])
    output = policy.forward_sequence(
        observations,
        hidden,
        masks,
        sequence_mask=sequence_mask,
    )
    assert torch.isfinite(output[0]).all()

    with pytest.raises(ValueError, match="active sequence step"):
        policy.forward_sequence(
            observations,
            hidden,
            masks,
            sequence_mask=torch.ones_like(sequence_mask),
        )
