import dataclasses
import json
import math
import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from expert_dataset import SIMULATOR_REVISION, load_expert_dataset
from expert_iteration import (
    EVALUATION_PRIOR_SOURCE,
    MonotonicExpertArchive,
    PolicyPriorProvider,
    RecoveryLabelStore,
    export_recorded_policy_prior,
    query_all_legal_actions,
    run_offline_iteration,
    select_recovery_candidates,
    uniform_legal_prior,
)
from pretrain_expert_policy import PretrainingConfig, run_pretraining
from recurrent_ppo import build_policy
from sil_buffer import SILBuffer
from train_recurrent_ppo import scheduled_sil_weight, train_sil_auxiliary


FIXTURE = os.path.join(
    os.path.dirname(__file__), "fixtures", "expert_dataset_v2.jsonl"
)


@pytest.fixture(scope="module")
def expert_checkpoint(tmp_path_factory):
    path = tmp_path_factory.mktemp("expert-iteration") / "policy.pt"
    run_pretraining(
        PretrainingConfig(
            dataset_path=FIXTURE,
            output_path=str(path),
            epochs=8,
            hidden_size=8,
            sequence_length=4,
            batch_size=2,
            learning_rate=5e-3,
            policy_type="gru",
            seed=88,
        )
    )
    return path


def test_policy_prior_fallback_and_recovery_selection(expert_checkpoint, tmp_path):
    dataset = load_expert_dataset(FIXTURE)
    decision = dataset.records[0].decisions[0]
    provider = PolicyPriorProvider(
        expert_checkpoint, dataset.source_hash, allow_fallback=False
    )
    weights = provider.weights(decision.observation, decision.legal_action_mask)
    assert provider.contract.dataset_source_hash == dataset.source_hash
    assert len(provider.contract.checkpoint_fingerprint) == 64
    assert math.isclose(sum(weights), 1.0, abs_tol=1e-6)
    assert all(
        weights[index] == 0.0
        for index, legal in enumerate(decision.legal_action_mask)
        if legal <= 0.5
    )
    candidates = select_recovery_candidates(provider.policy, dataset, 1.0)
    assert len(candidates) == 1

    stale = PolicyPriorProvider(
        expert_checkpoint, "stale-dataset", allow_fallback=True
    )
    assert stale.weights(decision.observation, decision.legal_action_mask) == (
        uniform_legal_prior(decision.legal_action_mask)
    )
    with pytest.raises(ValueError, match="stale"):
        PolicyPriorProvider(expert_checkpoint, "stale-dataset", allow_fallback=False)


def test_monotonic_archive_never_regresses():
    record = load_expert_dataset(FIXTURE).records[0]
    archive = MonotonicExpertArchive()
    assert archive.merge((record,)) == 1
    worse = dataclasses.replace(
        record,
        record_id="worse",
        record_hash="f" * 64,
        terminal_objective={**record.terminal_objective, "objectiveScore": 1.0},
    )
    assert archive.merge((worse,)) == 0
    assert archive.best(record.scenario_fingerprint).record_id == record.record_id
    better = dataclasses.replace(
        record,
        record_id="better",
        record_hash="e" * 64,
        terminal_objective={**record.terminal_objective, "objectiveScore": 101.0},
    )
    assert archive.merge((better,)) == 1
    assert archive.scores()[record.scenario_fingerprint] == 101.0


def test_all_legal_query_validates_response_and_timeout():
    mask = [1.0, 0.0, 1.0]

    class Client:
        def branch_rollout_multi(self, runner, snapshot, branches, horizon, gamma):
            del runner, snapshot, branches, horizon, gamma
            return [2.0, float("nan"), 3.0]

    values = query_all_legal_actions(Client(), 1, 2, mask, 2, 4, 0.99)
    assert values[0] == 2.0 and math.isnan(values[1]) and values[2] == 3.0

    class StaleClient:
        def branch_rollout_multi(self, *args):
            return [1.0]

    with pytest.raises(ValueError, match="dimension"):
        query_all_legal_actions(StaleClient(), 1, 2, mask, 2, 4, 0.99)

    class TimeoutClient:
        def branch_rollout_multi(self, *args):
            raise TimeoutError("bounded query timed out")

    with pytest.raises(TimeoutError):
        query_all_legal_actions(TimeoutClient(), 1, 2, mask, 2, 4, 0.99)


def test_recovery_labels_append_atomically_and_reject_corruption(tmp_path):
    path = tmp_path / "recovery.json"
    first = {
        "scenarioFingerprint": "scenario-a",
        "stateHash": 1,
        "legalActionMask": [1.0, 0.0],
        "qEstimates": [5.0, None],
    }
    digest = RecoveryLabelStore.append(path, [first])
    assert len(digest) == 64
    assert RecoveryLabelStore.read(path) == [first]
    replacement = {**first, "qEstimates": [6.0, None]}
    RecoveryLabelStore.append(path, [replacement])
    assert RecoveryLabelStore.read(path)[0]["qEstimates"][0] == 6.0
    path.write_text("{truncated", encoding="utf-8")
    before = path.read_bytes()
    with pytest.raises(ValueError, match="Corrupt"):
        RecoveryLabelStore.append(path, [first])
    assert path.read_bytes() == before


def test_persistent_sil_load_resume_auxiliary_decay_and_offline_cycle(
    expert_checkpoint, tmp_path,
):
    buffer = SILBuffer(max_per_party=2, min_episodes_before_ready=0)
    source_hash = buffer.load_expert_dataset(FIXTURE, 4, 8)
    assert source_hash == load_expert_dataset(FIXTURE).source_hash
    assert buffer.size() == 1 and buffer.is_ready()
    chunks = buffer.sample_sequence_chunks(1)
    restored = SILBuffer(max_per_party=2, min_episodes_before_ready=0)
    restored.load_state_dict(buffer.state_dict())
    assert restored.size() == 1

    policy = build_policy("gru", 287, 8, 11)
    optimizer = torch.optim.Adam(policy.parameters(), lr=1e-2)
    losses = [
        train_sil_auxiliary(policy, optimizer, chunks, "cpu", 1.0, 1.0)
        for _ in range(8)
    ]
    assert losses[-1] < losses[0]
    config = {"sil_loss_weight": 1.0, "sil_final_loss_weight": 0.0, "updates": 3}
    assert [scheduled_sil_weight(config, update) for update in (1, 2, 3)] == [
        1.0,
        0.5,
        0.0,
    ]
    summary = run_offline_iteration(FIXTURE, expert_checkpoint, 0.7)
    assert summary["records"] == 1
    assert summary["archiveScores"]
    prior_path = tmp_path / "policy-prior.json"
    prior_hash = export_recorded_policy_prior(
        load_expert_dataset(FIXTURE), expert_checkpoint, prior_path
    )
    prior = json.loads(prior_path.read_text(encoding="utf-8"))
    assert len(prior_hash) == 64
    assert prior["schemaVersion"] == 2
    assert prior["simulatorRevision"] == SIMULATOR_REVISION
    assert prior["sourceKind"] == "training-dataset-states"
    assert prior["trainingFingerprints"] == [
        load_expert_dataset(FIXTURE).records[0].scenario_fingerprint
    ]
    assert prior["entries"][0]["stateHash"] == "42"

    validation = dataclasses.replace(
        load_expert_dataset(FIXTURE).records[0],
        record_id="fixture-validation-0",
        scenario_fingerprint="validation-fingerprint",
        split="validation",
    )
    mixed_dataset = dataclasses.replace(
        load_expert_dataset(FIXTURE),
        records=(load_expert_dataset(FIXTURE).records[0], validation),
    )
    evaluation_path = tmp_path / "evaluation-prior.json"
    export_recorded_policy_prior(
        mixed_dataset,
        expert_checkpoint,
        evaluation_path,
        EVALUATION_PRIOR_SOURCE,
    )
    evaluation_prior = json.loads(evaluation_path.read_text(encoding="utf-8"))
    assert evaluation_prior["sourceKind"] == EVALUATION_PRIOR_SOURCE
    assert {
        entry["scenarioFingerprint"] for entry in evaluation_prior["entries"]
    } == {"validation-fingerprint"}
