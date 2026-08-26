"""Matched rotation model registry and tournament regressions."""

from __future__ import annotations

import copy
import json
import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from recurrent_ppo import build_policy, load_policy
from assemble_rotation_tournament import combine_cell
from evaluate_rotation_checkpoint import evaluate_checkpoint
from pretrain_expert_policy import PretrainingConfig, run_pretraining
from run_rotation_live_search import (
    build_benchmark_arguments,
    validate_live_report,
)
from rotation_model_registry import (
    REQUIRED_METRICS,
    evaluate_tournament_manifest,
    model_names,
)


@pytest.mark.parametrize("model_name", model_names())
def test_registered_model_forward_and_restore(model_name, tmp_path):
    policy = build_policy(model_name, 287, 8, 11)
    recurrent = torch.zeros(2, policy.recurrent_state_size)
    output = policy.act(
        torch.zeros(2, 287),
        recurrent,
        torch.ones(2, 11),
        deterministic=True,
    )
    assert output["hidden"].shape == recurrent.shape
    assert output["probabilities"].shape == (2, 11)
    assert torch.isfinite(output["value"]).all()

    checkpoint = tmp_path / f"{model_name}.pt"
    policy.save(checkpoint)
    restored, _ = load_policy(checkpoint)
    assert type(restored) is type(policy)
    assert restored.recurrent_state_size == policy.recurrent_state_size
    for name, value in policy.state_dict().items():
        assert torch.equal(value, restored.state_dict()[name])


def test_matched_tournament_selects_deterministic_champion():
    manifest = _manifest()
    report = evaluate_tournament_manifest(manifest)
    assert report["qualityGatePassed"] is True
    assert report["champion"] == "lstm"
    assert report["matchedRunCount"] == len(model_names()) * 2

    tied = copy.deepcopy(manifest)
    for run in tied["runs"]:
        run["metrics"]["holdoutTeacherAdvantage"] = 2.0
        run["metrics"]["feasibleImprovementPerCall"] = 1.0
        run["metrics"]["inferenceLatencyMillis"] = 1.0
    assert evaluate_tournament_manifest(tied)["champion"] == "gru"


@pytest.mark.parametrize(
    ("mutate", "message"),
    (
        (lambda value: value["runs"].pop(), "missing a model/seed cell"),
        (
            lambda value: value["runs"][0].__setitem__("optimizationSteps", 9),
            "unmatched",
        ),
        (
            lambda value: value["runs"][0]["holdoutFingerprints"].append("train"),
            "contaminated",
        ),
        (
            lambda value: value["runs"][0]["metrics"].__setitem__(
                "valueRankCorrelation", float("nan")
            ),
            "finite",
        ),
        (
            lambda value: value["runs"][0].__setitem__("model", "unknown"),
            "unknown model",
        ),
    ),
)
def test_tournament_rejects_unmatched_or_invalid_cells(mutate, message):
    manifest = _manifest()
    mutate(manifest)
    with pytest.raises(ValueError, match=message):
        evaluate_tournament_manifest(manifest)


def test_unknown_registered_model_fails_closed():
    with pytest.raises(ValueError, match="unknown policy_type"):
        build_policy("unknown", 287, 8, 11)


def test_offline_checkpoint_metrics_preserve_frozen_provenance(tmp_path):
    fixture = os.path.join(
        os.path.dirname(__file__), "fixtures", "expert_dataset_v2.jsonl"
    )
    checkpoint = tmp_path / "policy.pt"
    run_pretraining(
        PretrainingConfig(
            dataset_path=fixture,
            output_path=str(checkpoint),
            epochs=1,
            hidden_size=8,
            sequence_length=4,
            batch_size=2,
            policy_type="lstm",
            seed=19,
        )
    )
    result = evaluate_checkpoint(fixture, checkpoint)
    assert result["model"] == "lstm"
    assert result["seed"] == 19
    assert len(result["datasetSourceHash"]) == 64
    assert result["splitMetrics"]["train"]["decisions"] > 0
    assert result["splitMetrics"]["train"]["policyTop3Accuracy"] >= 0.0
    assert result["inferenceLatencyMillis"] >= 0.0

    payload = torch.load(checkpoint, weights_only=False)
    payload["dataset_source_hash"] = "f" * 64
    torch.save(payload, checkpoint)
    with pytest.raises(ValueError, match="stale"):
        evaluate_checkpoint(fixture, checkpoint)


def test_live_search_cell_combines_only_matched_measurements():
    offline, search, human = _combined_inputs()
    result = combine_cell(
        "gru", 11, "a" * 64, offline, search, human, 128
    )
    assert result["metrics"]["publishableMedianDelta"] == 5.0
    assert result["metrics"]["unguidedMedianDelta"] == 2.0
    assert result["metrics"]["holdoutTeacherAdvantage"] == 2.0
    assert result["metrics"]["feasibleImprovementPerCall"] == 2.0 / 128.0


@pytest.mark.parametrize(
    ("mutate", "message"),
    (
        (lambda offline, search: search["metrics"].pop(), "incomplete"),
        (
            lambda offline, search: search["metrics"][0].__setitem__(
                "simulatorCalls", 127
            ),
            "unequal call budget",
        ),
        (
            lambda offline, search: search["metrics"][2].__setitem__(
                "priorRevision", "stale"
            ),
            "checkpoint fingerprint",
        ),
        (
            lambda offline, search: search["metrics"][2].__setitem__(
                "cyclicEnergyFeasible", False
            ),
            "lacks holdout or feasible",
        ),
    ),
)
def test_live_search_cell_rejects_unmatched_measurements(mutate, message):
    offline, search, human = _combined_inputs()
    mutate(offline, search)
    with pytest.raises(ValueError, match=message):
        combine_cell("gru", 11, "a" * 64, offline, search, human, 128)


def test_live_search_command_and_report_require_three_matched_arms(tmp_path):
    arguments = build_benchmark_arguments(
        "dataset.json",
        "report.json",
        "b" * 64,
        "127.0.0.1",
        18761,
        (11, 13),
        128,
        "all",
    )
    assert arguments[0] == "BenchmarkRotationSearch"
    assert "--training-prior" not in arguments
    assert arguments[arguments.index("--guidance-mode") + 1] == "live"

    report = {
        "checkpointProvenance": {
            "checkpointRevision": f"sha256:{'b' * 64}",
            "datasetSourceHash": "a" * 64,
        },
        "metrics": [
            {"method": method, "simulatorCalls": 128}
            for method in (
                "deterministic-random",
                "unguided-evolutionary",
                "policy-guided",
            )
        ],
    }
    path = tmp_path / "live.json"
    path.write_text(json.dumps(report), encoding="utf-8")
    assert len(validate_live_report(path, "a" * 64, "b" * 64, 128)["metrics"]) == 3
    report["metrics"].pop()
    path.write_text(json.dumps(report), encoding="utf-8")
    with pytest.raises(ValueError, match="coverage"):
        validate_live_report(path, "a" * 64, "b" * 64, 128)


def _manifest():
    runs = []
    advantages = {"mlp": 0.5, "gru": 1.0, "lstm": 2.0, "transformer": -0.5}
    for model in model_names():
        for seed in (11, 13):
            metrics = {name: 1.0 for name in REQUIRED_METRICS}
            metrics["holdoutTeacherAdvantage"] = advantages[model]
            metrics["publishableMedianDelta"] = 0.0
            metrics["unguidedMedianDelta"] = 0.0
            runs.append(
                {
                    "model": model,
                    "seed": seed,
                    "datasetSourceHash": "a" * 64,
                    "trainingFingerprints": ["train"],
                    "holdoutFingerprints": ["holdout"],
                    "optimizationSteps": 10,
                    "searchCallBudget": 128,
                    "checkpointSelectionRule": "validation-policy-loss",
                    "checkpointFingerprint": (model[0] * 64),
                    "metrics": metrics,
                }
            )
    return {
        "schemaVersion": 1,
        "models": list(model_names()),
        "seeds": [11, 13],
        "runs": runs,
    }


def _combined_inputs():
    fingerprint = "holdout-scenario"
    checkpoint = "b" * 64
    offline = {
        "schemaVersion": 1,
        "model": "gru",
        "seed": 11,
        "datasetSourceHash": "a" * 64,
        "trainingFingerprints": ["train"],
        "holdoutFingerprints": [fingerprint],
        "checkpointFingerprint": checkpoint,
        "checkpointSelectionRule": "final-matched-epoch",
        "optimizationSteps": 10,
        "inferenceLatencyMillis": 0.5,
        "splitMetrics": {
            "holdout": {
                "decisions": 3,
                "policyTop1Accuracy": 0.5,
                "policyTop3Accuracy": 1.0,
                "valueRankCorrelation": 0.25,
                "valueCalibrationError": 2.0,
            }
        },
    }
    common = {
        "split": "holdout",
        "scenarioFingerprint": fingerprint,
        "seed": 104729,
        "simulatorCalls": 128,
        "simulatorRevision": "rotation-simulator-v4",
        "datasetRevision": f"schema=2:sha256={'a' * 64}",
        "complete": True,
        "invalidActionCount": 0,
        "cyclicEnergyFeasible": True,
    }
    search = {
        "metrics": [
            {**common, "method": "deterministic-random", "objectiveScore": 8.0},
            {**common, "method": "unguided-evolutionary", "objectiveScore": 13.0},
            {
                **common,
                "method": "policy-guided",
                "objectiveScore": 15.0,
                "priorRevision": f"live:{checkpoint}",
            },
        ]
    }
    return offline, search, {fingerprint: 10.0}
