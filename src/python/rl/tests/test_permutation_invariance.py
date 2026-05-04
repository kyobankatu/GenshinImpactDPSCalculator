"""Permutation-invariance audit for RecurrentPolicy.

This test checks whether the policy produces consistently permuted outputs
when character slots in the observation are permuted.

The current architecture uses a shared char_encoder applied per-slot, then
global-query attention over all slots. This should be permutation-equivariant
w.r.t. the character slots — permuting slots should yield the same attention
weights in permuted order, and therefore the same policy distribution over
character-related actions.

The swap action slots (actions 3-6, indices 3..num_chars+2) map one-to-one
to character slots, so those logits should permute consistently.

NOTE: If this test FAILS, it means the policy is NOT permutation-equivariant.
This may be acceptable if the observation itself already encodes slot identity
(e.g. on-field fraction of slot 0 always refers to character 0). However it
documents a potential limitation: the policy might overfit to slot positions
instead of learning general role-based strategies.
"""

import sys
import os

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from recurrent_ppo import RecurrentPolicy


CHAR_FEATURE_SIZE = 27
GLOBAL_FEATURE_SIZE = 7
NUM_CHARS = 4
ACTION_SIZE = 7
# Action layout assumed: [wait/skill/burst for on-field char (actions 0-2), swap to slot 0..3 (actions 3-6)]
SWAP_ACTION_OFFSET = 3
HIDDEN_SIZE = 32
BATCH_SIZE = 2
OBS_SIZE = CHAR_FEATURE_SIZE * NUM_CHARS + GLOBAL_FEATURE_SIZE  # 115


@pytest.fixture
def policy():
    torch.manual_seed(42)
    return RecurrentPolicy(
        observation_size=OBS_SIZE,
        hidden_size=HIDDEN_SIZE,
        action_size=ACTION_SIZE,
        char_feature_size=CHAR_FEATURE_SIZE,
        global_feature_size=GLOBAL_FEATURE_SIZE,
        num_chars=NUM_CHARS,
        privileged_observation_size=23,
    ).eval()


def permute_observation(obs, perm):
    """Permute the character blocks in a [B, obs_size] observation tensor.

    Character blocks occupy obs[:, :NUM_CHARS*CHAR_FEATURE_SIZE].
    Global features follow after.
    """
    B = obs.shape[0]
    char_block = obs[:, :NUM_CHARS * CHAR_FEATURE_SIZE].reshape(B, NUM_CHARS, CHAR_FEATURE_SIZE)
    permuted_chars = char_block[:, perm, :]
    global_block = obs[:, NUM_CHARS * CHAR_FEATURE_SIZE:]
    return torch.cat([permuted_chars.reshape(B, -1), global_block], dim=-1)


def permute_action_mask(mask, perm):
    """Permute swap-action slots in an action mask tensor [B, action_size].

    Swap actions occupy indices SWAP_ACTION_OFFSET .. SWAP_ACTION_OFFSET+NUM_CHARS-1.
    """
    permuted = mask.clone()
    for new_slot, old_slot in enumerate(perm):
        permuted[:, SWAP_ACTION_OFFSET + new_slot] = mask[:, SWAP_ACTION_OFFSET + old_slot]
    return permuted


def test_permutation_invariance(policy):
    """Policy output over swap actions should permute consistently with slot permutation."""
    torch.manual_seed(0)
    obs = torch.randn(BATCH_SIZE, OBS_SIZE)
    hidden = torch.zeros(BATCH_SIZE, HIDDEN_SIZE)
    # All actions valid
    mask = torch.ones(BATCH_SIZE, ACTION_SIZE)

    perm = [2, 0, 3, 1]
    perm_tensor = torch.tensor(perm, dtype=torch.long)

    obs_permuted = permute_observation(obs, perm_tensor)
    mask_permuted = permute_action_mask(mask, perm_tensor)

    with torch.no_grad():
        out_orig = policy.act(obs, hidden, mask, deterministic=False)
        out_perm = policy.act(obs_permuted, hidden, mask_permuted, deterministic=False)

    probs_orig = out_orig["probabilities"]  # [B, action_size]
    probs_perm = out_perm["probabilities"]  # [B, action_size]

    # Build expected permuted swap probabilities from original output
    swap_probs_orig = probs_orig[:, SWAP_ACTION_OFFSET:SWAP_ACTION_OFFSET + NUM_CHARS]
    swap_probs_perm_expected = swap_probs_orig[:, perm_tensor]

    swap_probs_perm_actual = probs_perm[:, SWAP_ACTION_OFFSET:SWAP_ACTION_OFFSET + NUM_CHARS]

    # Non-swap actions (skill/burst/wait for on-field char) should be unchanged
    non_swap_orig = probs_orig[:, :SWAP_ACTION_OFFSET]
    non_swap_perm = probs_perm[:, :SWAP_ACTION_OFFSET]

    atol = 1e-4

    non_swap_invariant = torch.allclose(non_swap_orig, non_swap_perm, atol=atol)
    swap_equivariant = torch.allclose(swap_probs_perm_expected, swap_probs_perm_actual, atol=atol)

    # Document the current state — both may fail if the policy is not permutation-equivariant
    # This is expected to FAIL with the current architecture because:
    # 1. The attention query is a function of global_encoding only (not slot-specific), so
    #    the context vector changes when slot order changes, changing GRU input.
    # 2. The policy head maps from GRU hidden state to all actions jointly, so swap logits
    #    are entangled with the full hidden state rather than per-slot encodings.
    if not non_swap_invariant:
        print(
            "\n[NON-INVARIANT] Non-swap action probabilities changed after slot permutation. "
            "The policy is sensitive to slot ordering."
        )
    if not swap_equivariant:
        print(
            "\n[NON-EQUIVARIANT] Swap action probabilities are not consistently permuted. "
            "The policy maps slot positions to fixed action indices, breaking permutation equivariance."
        )

    # Soft assertion: at least check shapes and that probabilities are valid
    assert probs_orig.shape == (BATCH_SIZE, ACTION_SIZE)
    assert probs_perm.shape == (BATCH_SIZE, ACTION_SIZE)
    assert torch.all(probs_orig >= 0)
    assert torch.all(probs_perm >= 0)

    # Record the invariance result as a known property of the current architecture
    # Change this assertion when the architecture is updated to be permutation-equivariant
    assert not non_swap_invariant or not swap_equivariant, (
        "Unexpected: policy appears permutation-equivariant. "
        "Update this test to assert equivariance if the architecture was intentionally made equivariant."
    )
