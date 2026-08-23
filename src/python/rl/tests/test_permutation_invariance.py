import itertools
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
    PRIVILEGED_CHAR_FEATURE_SIZE,
    PRIVILEGED_GLOBAL_FEATURE_SIZE,
    PRIVILEGED_OBSERVATION_SIZE,
    SWAP_ACTION_OFFSET,
    RecurrentPolicy,
    TransformerPolicy,
)


ACTION_SIZE = GLOBAL_ACTION_COUNT + NUM_CHARS
HIDDEN_SIZE = 32
OBSERVATION_SIZE = CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE


def build_policy(policy_type):
    torch.manual_seed(42)
    policy_class = RecurrentPolicy if policy_type == "gru" else TransformerPolicy
    return policy_class(
        observation_size=OBSERVATION_SIZE,
        hidden_size=HIDDEN_SIZE,
        action_size=ACTION_SIZE,
    )


def permute_actor(observation, permutation):
    characters = observation[:, : NUM_CHARS * CHAR_FEATURE_SIZE].reshape(
        observation.shape[0],
        NUM_CHARS,
        CHAR_FEATURE_SIZE,
    )
    global_features = observation[:, NUM_CHARS * CHAR_FEATURE_SIZE :]
    return torch.cat(
        [characters[:, permutation].reshape(observation.shape[0], -1), global_features],
        dim=-1,
    )


def permute_privileged(privileged, permutation):
    characters = privileged[:, : NUM_CHARS * PRIVILEGED_CHAR_FEATURE_SIZE].reshape(
        privileged.shape[0],
        NUM_CHARS,
        PRIVILEGED_CHAR_FEATURE_SIZE,
    )
    global_features = privileged[:, NUM_CHARS * PRIVILEGED_CHAR_FEATURE_SIZE :]
    assert global_features.shape[-1] == PRIVILEGED_GLOBAL_FEATURE_SIZE
    return torch.cat(
        [characters[:, permutation].reshape(privileged.shape[0], -1), global_features],
        dim=-1,
    )


def permute_actions(actions, permutation):
    result = actions.clone()
    result[:, SWAP_ACTION_OFFSET:] = actions[:, SWAP_ACTION_OFFSET:][:, permutation]
    return result


@pytest.mark.parametrize("policy_type", ["gru", "transformer"])
def test_all_slot_permutations_are_exactly_equivariant(policy_type):
    policy = build_policy(policy_type).eval()
    torch.manual_seed(7)
    observation = torch.randn(2, OBSERVATION_SIZE)
    actor_blocks = observation[:, : NUM_CHARS * CHAR_FEATURE_SIZE].reshape(
        2,
        NUM_CHARS,
        CHAR_FEATURE_SIZE,
    )
    actor_blocks[:, :, 1] = 0.0
    actor_blocks[:, 2, 1] = 1.0
    actor_blocks[:, 1] = actor_blocks[:, 0]
    actor_blocks[:, 2, 1] = 1.0
    privileged = torch.randn(2, PRIVILEGED_OBSERVATION_SIZE)
    mask = torch.ones(2, ACTION_SIZE)
    hidden = torch.randn(2, HIDDEN_SIZE)

    with torch.no_grad():
        reference = policy.forward_step(observation, hidden, mask, privileged)

    for permutation_tuple in itertools.permutations(range(NUM_CHARS)):
        permutation = torch.tensor(permutation_tuple)
        permuted_observation = permute_actor(observation, permutation)
        permuted_privileged = permute_privileged(privileged, permutation)
        permuted_mask = permute_actions(mask, permutation)
        with torch.no_grad():
            actual = policy.forward_step(
                permuted_observation,
                hidden,
                permuted_mask,
                permuted_privileged,
            )

        expected_logits = permute_actions(reference[0], permutation)
        expected_attention = reference[3][:, permutation]
        expected_auxiliary = permute_privileged(reference[4], permutation)
        torch.testing.assert_close(actual[0], expected_logits, atol=1e-6, rtol=1e-6)
        torch.testing.assert_close(actual[1], reference[1], atol=1e-6, rtol=1e-6)
        torch.testing.assert_close(actual[2], reference[2], atol=1e-6, rtol=1e-6)
        torch.testing.assert_close(actual[3], expected_attention, atol=1e-6, rtol=1e-6)
        torch.testing.assert_close(actual[4], expected_auxiliary, atol=1e-6, rtol=1e-6)


@pytest.mark.parametrize("policy_type", ["gru", "transformer"])
def test_recurrent_reuse_and_gradients_remain_finite(policy_type):
    policy = build_policy(policy_type).train()
    torch.manual_seed(11)
    observation = torch.randn(3, OBSERVATION_SIZE, requires_grad=True)
    with torch.no_grad():
        observation_view = observation[:, : NUM_CHARS * CHAR_FEATURE_SIZE].reshape(
            3,
            NUM_CHARS,
            CHAR_FEATURE_SIZE,
        )
        observation_view[:, :, 1] = 0.0
        observation_view[:, 0, 1] = 1.0
    hidden = torch.randn(3, HIDDEN_SIZE, requires_grad=True)
    privileged = torch.randn(3, PRIVILEGED_OBSERVATION_SIZE)
    mask = torch.ones(3, ACTION_SIZE)

    first = policy.forward_step(observation, hidden, mask, privileged)
    second = policy.forward_step(observation, first[2], mask, privileged)
    loss = first[0].sum() + first[1].sum() + first[4].sum() + second[1].sum()
    loss.backward()

    assert torch.isfinite(first[2]).all()
    assert torch.isfinite(second[2]).all()
    assert observation.grad is not None and torch.isfinite(observation.grad).all()
    assert hidden.grad is not None and torch.isfinite(hidden.grad).all()
    assert all(
        parameter.grad is None or torch.isfinite(parameter.grad).all()
        for parameter in policy.parameters()
    )
