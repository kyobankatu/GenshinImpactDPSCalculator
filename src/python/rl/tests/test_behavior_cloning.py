import dataclasses
import hashlib
import json
import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from expert_dataset import (
    SIMULATOR_REVISION,
    build_party_balanced_order,
    build_sequence_chunks,
    load_expert_dataset,
    training_records,
    value_normalization,
)
from pretrain_expert_policy import (
    PretrainingConfig,
    _collate,
    run_pretraining,
)
from recurrent_ppo import build_policy, load_policy
from train_recurrent_ppo import load_expert_initialization


FIXTURE = os.path.join(
    os.path.dirname(__file__), "fixtures", "expert_dataset_v1.jsonl"
)


def test_tiny_fixture_overfits_policy_and_value(tmp_path):
    output = tmp_path / "overfit.pt"
    policy, history = run_pretraining(_config(output, epochs=35))
    assert history[-1]["policy_loss"] < history[0]["policy_loss"]
    assert history[-1]["value_loss"] < history[0]["value_loss"]
    assert history[-1]["accuracy"] == 1.0
    record = load_expert_dataset(FIXTURE).records[0]
    decision = record.decisions[0]
    result = policy.act(
        torch.tensor([decision.observation]),
        torch.zeros(1, policy.hidden_size),
        torch.tensor([decision.legal_action_mask]),
        deterministic=True,
    )
    assert result["action"].item() == decision.action_id
    probabilities = result["probabilities"][0]
    for action, legal in enumerate(decision.legal_action_mask):
        if legal <= 0.5:
            assert probabilities[action].item() == 0.0
    loaded, _ = load_policy(output)
    assert loaded.hidden_size == policy.hidden_size
    initialized = build_policy("gru", 287, policy.hidden_size, 11)
    load_expert_initialization(initialized, output)
    for name, value in policy.state_dict().items():
        assert torch.equal(value, initialized.state_dict()[name])
    payload = torch.load(output, weights_only=False)
    assert payload["pretraining_revision"] == 2
    assert payload["simulator_revision"] == SIMULATOR_REVISION
    assert payload["training_fingerprints"] == [record.scenario_fingerprint]
    assert payload["normalization_fingerprints"] == [
        record.scenario_fingerprint
    ]


def test_soft_policy_target_and_recurrent_padding(tmp_path):
    payload = _fixture_payload()
    payload["decisions"][0]["visitPolicyTarget"] = [
        0.7,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.3,
        0.0,
        0.0,
        0.0,
        0.0,
    ]
    _seal(payload)
    dataset_path = tmp_path / "soft.jsonl"
    dataset_path.write_text(_canonical(payload), encoding="utf-8")
    _, history = run_pretraining(
        dataclasses.replace(
            _config(tmp_path / "soft.pt", epochs=25),
            dataset_path=str(dataset_path),
        )
    )
    assert history[-1]["policy_loss"] < history[0]["policy_loss"]

    record = load_expert_dataset(FIXTURE).records[0]
    longer = dataclasses.replace(
        record,
        record_id="longer",
        decisions=(record.decisions[0], record.decisions[0]),
    )
    chunks = build_sequence_chunks((record, longer), 4, 100.0, 1.0)
    policy = build_policy("gru", 287, 8, 11)
    batch = _collate(list(chunks), policy, torch.device("cpu"))
    assert batch["sequence_mask"].shape == (2, 2)
    assert batch["sequence_mask"][0, 1].item() == 0.0
    logits, _, _, _, _ = policy.forward_sequence(
        batch["observations"],
        batch["initial_hidden"],
        batch["action_masks"],
        sequence_mask=batch["sequence_mask"],
    )
    assert torch.isfinite(logits).all()


def test_party_balance_and_train_only_normalization():
    record = load_expert_dataset(FIXTURE).records[0]
    records = (
        record,
        dataclasses.replace(record, record_id="a-1"),
        dataclasses.replace(record, record_id="a-2"),
        dataclasses.replace(record, record_id="b-0", party_name="PartyB"),
    )
    order = build_party_balanced_order(records, seed=7, epoch=2)
    counts = {name: 0 for name in ("FixtureParty", "PartyB")}
    for item in order:
        counts[item.party_name] += 1
    assert counts == {"FixtureParty": 3, "PartyB": 3}
    assert order == build_party_balanced_order(records, seed=7, epoch=2)

    validation = dataclasses.replace(
        record,
        record_id="validation",
        scenario_fingerprint="validation-only",
        split="validation",
        terminal_objective={**record.terminal_objective, "objectiveScore": 1e9},
    )
    dataset = dataclasses.replace(
        load_expert_dataset(FIXTURE), records=(record, validation)
    )
    mean, scale = value_normalization(training_records(dataset))
    assert mean == 100.0
    assert scale == 1.0


def test_interrupted_resume_matches_uninterrupted_metrics(tmp_path):
    full_path = tmp_path / "full.pt"
    _, full_history = run_pretraining(_config(full_path, epochs=4))

    resumed_path = tmp_path / "resumed.pt"
    run_pretraining(_config(resumed_path, epochs=2))
    _, resumed_history = run_pretraining(
        dataclasses.replace(
            _config(resumed_path, epochs=4),
            resume_from=str(resumed_path),
        )
    )
    assert resumed_history == full_history
    full_payload = torch.load(full_path, weights_only=False)
    resumed_payload = torch.load(resumed_path, weights_only=False)
    for name, value in full_payload["state_dict"].items():
        assert torch.equal(value, resumed_payload["state_dict"][name])


def test_empty_dataset_and_incomplete_resume_fail_closed(tmp_path):
    empty = tmp_path / "empty.jsonl"
    empty.write_text("", encoding="utf-8")
    with pytest.raises(ValueError, match="empty"):
        run_pretraining(
            dataclasses.replace(
                _config(tmp_path / "empty.pt", epochs=1),
                dataset_path=str(empty),
            )
        )

    checkpoint = tmp_path / "checkpoint.pt"
    run_pretraining(_config(checkpoint, epochs=1))
    payload = torch.load(checkpoint, weights_only=False)
    payload.pop("dataset_source_hash")
    torch.save(payload, checkpoint)
    with pytest.raises(ValueError, match="missing metadata"):
        run_pretraining(
            dataclasses.replace(
                _config(checkpoint, epochs=2), resume_from=str(checkpoint)
            )
        )
    policy = build_policy("gru", 287, 8, 11)
    with pytest.raises(ValueError, match="missing provenance"):
        load_expert_initialization(policy, checkpoint)


def test_checkpoint_fingerprint_provenance_fails_closed(tmp_path):
    checkpoint = tmp_path / "checkpoint.pt"
    run_pretraining(_config(checkpoint, epochs=1))
    payload = torch.load(checkpoint, weights_only=False)
    payload["normalization_fingerprints"] = ["holdout-leak"]
    torch.save(payload, checkpoint)
    policy = build_policy("gru", 287, 8, 11)
    with pytest.raises(ValueError, match="normalization fingerprints"):
        load_expert_initialization(policy, checkpoint)


def _config(output, epochs):
    return PretrainingConfig(
        dataset_path=FIXTURE,
        output_path=str(output),
        epochs=epochs,
        hidden_size=8,
        sequence_length=4,
        batch_size=2,
        learning_rate=5e-3,
        value_coefficient=0.25,
        scheduler_gamma=0.99,
        policy_type="gru",
        seed=4321,
    )


def _fixture_payload():
    with open(FIXTURE, encoding="utf-8") as handle:
        return json.load(handle)


def _canonical(payload):
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def _seal(payload):
    content = dict(payload)
    content.pop("recordHash", None)
    payload["recordHash"] = hashlib.sha256(_canonical(content).encode()).hexdigest()
