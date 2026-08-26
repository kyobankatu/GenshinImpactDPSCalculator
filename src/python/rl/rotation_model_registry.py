"""Registered rotation policy models and matched tournament validation."""

from __future__ import annotations

import math
import statistics
from dataclasses import dataclass
from typing import Any


TOURNAMENT_SCHEMA_VERSION = 1
REQUIRED_METRICS = (
    "policyTop1Accuracy",
    "policyTop3Accuracy",
    "valueRankCorrelation",
    "valueCalibrationError",
    "feasibleImprovementPerCall",
    "inferenceLatencyMillis",
    "publishableMedianDelta",
    "unguidedMedianDelta",
    "holdoutTeacherAdvantage",
)


@dataclass(frozen=True)
class RotationModelSpec:
    """One policy implementation available to training and checkpoint restore."""

    name: str
    class_name: str
    recurrent: bool


MODEL_SPECS = {
    spec.name: spec
    for spec in (
        RotationModelSpec("mlp", "MlpPolicy", False),
        RotationModelSpec("gru", "RecurrentPolicy", True),
        RotationModelSpec("lstm", "LstmPolicy", True),
        RotationModelSpec("transformer", "TransformerPolicy", True),
    )
}


def model_names() -> tuple[str, ...]:
    """Return stable CLI and tournament model ordering."""
    return tuple(MODEL_SPECS)


def model_spec(name: str) -> RotationModelSpec:
    """Resolve one model name or fail closed on an unknown architecture."""
    try:
        return MODEL_SPECS[name]
    except (KeyError, TypeError) as error:
        expected = ", ".join(model_names())
        raise ValueError(
            f"unknown policy_type: {name!r} (expected one of {expected})"
        ) from error


def _model_class(name: str):
    from recurrent_ppo import (
        LstmPolicy,
        MlpPolicy,
        RecurrentPolicy,
        TransformerPolicy,
    )

    classes = {
        "MlpPolicy": MlpPolicy,
        "RecurrentPolicy": RecurrentPolicy,
        "LstmPolicy": LstmPolicy,
        "TransformerPolicy": TransformerPolicy,
    }
    return classes[model_spec(name).class_name]


def build_registered_policy(name: str, *args, **kwargs):
    """Construct a registered model behind the common policy interface."""
    return _model_class(name)(*args, **kwargs)


def load_registered_policy(name: str, path, map_location="cpu"):
    """Restore a registered model from the common checkpoint contract."""
    return _model_class(name).load(path, map_location=map_location)


def evaluate_tournament_manifest(
    manifest: dict[str, Any],
    expected_models: tuple[str, ...] = model_names(),
) -> dict[str, Any]:
    """Validate matched model/seed cells and select a deterministic champion."""
    if not isinstance(manifest, dict) or manifest.get("schemaVersion") != TOURNAMENT_SCHEMA_VERSION:
        raise ValueError("Tournament manifest schema mismatch")
    seeds = manifest.get("seeds")
    runs = manifest.get("runs")
    if (
        not isinstance(seeds, list)
        or not seeds
        or any(not isinstance(seed, int) for seed in seeds)
        or seeds != sorted(set(seeds))
        or not isinstance(runs, list)
    ):
        raise ValueError("Tournament seeds or runs are malformed")
    models = tuple(manifest.get("models", ()))
    if models != expected_models or len(set(models)) != len(models):
        raise ValueError("Tournament model roster mismatch")
    matched_fields = (
        "datasetSourceHash",
        "trainingFingerprints",
        "holdoutFingerprints",
        "optimizationSteps",
        "searchCallBudget",
        "checkpointSelectionRule",
    )
    expected_values = None
    cells = {}
    for run in runs:
        _validate_run(run, models, seeds)
        values = tuple(_freeze(run[field]) for field in matched_fields)
        if expected_values is None:
            expected_values = values
        elif values != expected_values:
            raise ValueError("Tournament run budgets or provenance are unmatched")
        key = (run["model"], run["seed"])
        if key in cells:
            raise ValueError(f"Tournament cell is duplicated: {key}")
        cells[key] = run
    expected_cells = {(model, seed) for model in models for seed in seeds}
    if set(cells) != expected_cells:
        raise ValueError("Tournament report is missing a model/seed cell")

    aggregates = {}
    for model in models:
        model_runs = [cells[(model, seed)] for seed in seeds]
        aggregates[model] = {
            metric: statistics.median(run["metrics"][metric] for run in model_runs)
            for metric in REQUIRED_METRICS
        }
    qualified = [
        model
        for model in models
        if aggregates[model]["publishableMedianDelta"] >= 0.0
        and aggregates[model]["unguidedMedianDelta"] >= 0.0
        and aggregates[model]["holdoutTeacherAdvantage"] > 0.0
    ]
    champion = None
    if qualified:
        champion = min(
            qualified,
            key=lambda model: (
                -aggregates[model]["holdoutTeacherAdvantage"],
                -aggregates[model]["feasibleImprovementPerCall"],
                aggregates[model]["inferenceLatencyMillis"],
                model,
            ),
        )
    return {
        "schemaVersion": TOURNAMENT_SCHEMA_VERSION,
        "qualityGatePassed": champion is not None,
        "champion": champion,
        "aggregates": aggregates,
        "matchedRunCount": len(cells),
    }


def _validate_run(run, models, seeds) -> None:
    required = {
        "model",
        "seed",
        "datasetSourceHash",
        "trainingFingerprints",
        "holdoutFingerprints",
        "optimizationSteps",
        "searchCallBudget",
        "checkpointSelectionRule",
        "checkpointFingerprint",
        "metrics",
    }
    if not isinstance(run, dict) or set(run) != required:
        raise ValueError("Tournament run fields are malformed")
    if run["model"] not in models or run["seed"] not in seeds:
        raise ValueError("Tournament run has an unknown model or seed")
    for field in ("datasetSourceHash", "checkpointFingerprint"):
        if not isinstance(run[field], str) or len(run[field]) != 64:
            raise ValueError(f"Tournament {field} is malformed")
    training = run["trainingFingerprints"]
    holdout = run["holdoutFingerprints"]
    if (
        not isinstance(training, list)
        or not training
        or training != sorted(set(training))
        or not isinstance(holdout, list)
        or not holdout
        or holdout != sorted(set(holdout))
        or set(training).intersection(holdout)
    ):
        raise ValueError("Tournament train/holdout fingerprints are contaminated")
    if run["optimizationSteps"] <= 0 or run["searchCallBudget"] <= 0:
        raise ValueError("Tournament budgets must be positive")
    if not isinstance(run["checkpointSelectionRule"], str) or not run["checkpointSelectionRule"]:
        raise ValueError("Tournament checkpoint selection rule is missing")
    metrics = run["metrics"]
    if not isinstance(metrics, dict) or set(metrics) != set(REQUIRED_METRICS):
        raise ValueError("Tournament metrics are incomplete")
    if any(
        not isinstance(metrics[name], (int, float)) or not math.isfinite(metrics[name])
        for name in REQUIRED_METRICS
    ):
        raise ValueError("Tournament metric must be finite")


def _freeze(value):
    if isinstance(value, list):
        return tuple(_freeze(item) for item in value)
    if isinstance(value, dict):
        return tuple((key, _freeze(item)) for key, item in sorted(value.items()))
    return value
