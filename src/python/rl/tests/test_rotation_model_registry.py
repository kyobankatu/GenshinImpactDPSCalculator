"""Matched rotation model registry and tournament regressions."""

from __future__ import annotations

import copy
import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from recurrent_ppo import build_policy, load_policy
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
