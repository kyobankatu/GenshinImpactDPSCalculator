"""Assemble offline checkpoints and matched live-search results."""

from __future__ import annotations

import argparse
import json
import math
import statistics
from pathlib import Path

from evaluate_rotation_checkpoint import atomic_write
from expert_dataset import load_expert_dataset
from rotation_model_registry import (
    TOURNAMENT_SCHEMA_VERSION,
    evaluate_tournament_manifest,
    model_names,
)


def assemble_tournament(
    dataset_path,
    offline_directory,
    search_directory,
    seeds,
    search_call_budget,
):
    """Combine every model/seed cell without inventing absent measurements."""
    if not seeds or seeds != tuple(sorted(set(seeds))) or search_call_budget <= 0:
        raise ValueError("Tournament seeds and search budget are invalid")
    dataset = load_expert_dataset(dataset_path)
    human_baselines = _human_baselines(dataset)
    runs = []
    for model in model_names():
        for seed in seeds:
            stem = f"{model}-{seed}"
            offline = _load_json(Path(offline_directory) / f"{stem}.offline.json")
            search = _load_json(Path(search_directory) / f"{stem}.search.json")
            runs.append(
                combine_cell(
                    model,
                    seed,
                    dataset.source_hash,
                    offline,
                    search,
                    human_baselines,
                    search_call_budget,
                )
            )
    manifest = {
        "schemaVersion": TOURNAMENT_SCHEMA_VERSION,
        "models": list(model_names()),
        "seeds": list(seeds),
        "runs": runs,
    }
    report = evaluate_tournament_manifest(manifest)
    return manifest, report


def combine_cell(
    model,
    seed,
    dataset_source_hash,
    offline,
    search,
    human_baselines,
    search_call_budget,
):
    """Validate and combine one training seed with its fixed search suite."""
    if (
        offline.get("schemaVersion") != 1
        or offline.get("model") != model
        or offline.get("seed") != seed
        or offline.get("datasetSourceHash") != dataset_source_hash
    ):
        raise ValueError("Offline tournament cell identity mismatch")
    checkpoint = offline.get("checkpointFingerprint")
    if not isinstance(checkpoint, str) or len(checkpoint) != 64:
        raise ValueError("Offline checkpoint fingerprint is malformed")
    split_metrics = offline.get("splitMetrics", {})
    holdout = split_metrics.get("holdout")
    if not isinstance(holdout, dict) or holdout.get("decisions", 0) <= 0:
        raise ValueError("Offline tournament cell has no holdout decisions")
    metrics = search.get("metrics")
    if not isinstance(metrics, list) or not metrics:
        raise ValueError("Live search report has no metrics")
    grouped = {}
    for metric in metrics:
        method = metric.get("method")
        if method not in (
            "deterministic-random",
            "unguided-evolutionary",
            "policy-guided",
        ):
            continue
        if metric.get("simulatorCalls") != search_call_budget:
            raise ValueError("Live search report has an unequal call budget")
        if metric.get("simulatorRevision") != "rotation-simulator-v4":
            raise ValueError("Live search report simulator revision mismatch")
        if dataset_source_hash not in str(metric.get("datasetRevision")):
            raise ValueError("Live search report dataset fingerprint mismatch")
        if method == "policy-guided" and checkpoint not in str(
            metric.get("priorRevision")
        ):
            raise ValueError("Live search report checkpoint fingerprint mismatch")
        key = (
            metric.get("split"),
            metric.get("scenarioFingerprint"),
            metric.get("seed"),
        )
        if key in grouped and method in grouped[key]:
            raise ValueError("Live search report duplicates a method cell")
        grouped.setdefault(key, {})[method] = metric
    if not grouped or any(len(methods) != 3 for methods in grouped.values()):
        raise ValueError("Live search report has an incomplete matched cell")

    guided_human = []
    guided_unguided = []
    holdout_advantage = []
    feasible_per_call = []
    for (split, fingerprint, _search_seed), methods in sorted(grouped.items()):
        if fingerprint not in human_baselines:
            raise ValueError("Live search scenario is absent from frozen provenance")
        guided = _score(methods["policy-guided"])
        unguided = _score(methods["unguided-evolutionary"])
        _score(methods["deterministic-random"])
        guided_human.append(guided - human_baselines[fingerprint])
        guided_unguided.append(guided - unguided)
        if split == "holdout":
            holdout_advantage.append(guided - unguided)
        if methods["policy-guided"].get("cyclicEnergyFeasible") is True:
            feasible_per_call.append((guided - unguided) / search_call_budget)
    if not holdout_advantage or not feasible_per_call:
        raise ValueError("Live search report lacks holdout or feasible comparisons")
    combined_metrics = {
        "policyTop1Accuracy": _finite(holdout["policyTop1Accuracy"]),
        "policyTop3Accuracy": _finite(holdout["policyTop3Accuracy"]),
        "valueRankCorrelation": _finite(holdout["valueRankCorrelation"]),
        "valueCalibrationError": _finite(holdout["valueCalibrationError"]),
        "feasibleImprovementPerCall": statistics.median(feasible_per_call),
        "inferenceLatencyMillis": _finite(offline["inferenceLatencyMillis"]),
        "publishableMedianDelta": statistics.median(guided_human),
        "unguidedMedianDelta": statistics.median(guided_unguided),
        "holdoutTeacherAdvantage": statistics.median(holdout_advantage),
    }
    return {
        "model": model,
        "seed": seed,
        "datasetSourceHash": dataset_source_hash,
        "trainingFingerprints": offline["trainingFingerprints"],
        "holdoutFingerprints": offline["holdoutFingerprints"],
        "optimizationSteps": offline["optimizationSteps"],
        "searchCallBudget": search_call_budget,
        "checkpointSelectionRule": offline["checkpointSelectionRule"],
        "checkpointFingerprint": checkpoint,
        "metrics": combined_metrics,
    }


def _human_baselines(dataset):
    grouped = {}
    for record in dataset.records:
        grouped.setdefault(record.scenario_fingerprint, []).append(
            record.provenance.human_median_objective
        )
    return {
        fingerprint: statistics.median(values)
        for fingerprint, values in grouped.items()
    }


def _score(metric):
    if metric.get("complete") is not True or metric.get("invalidActionCount") != 0:
        raise ValueError("Live search trajectory is incomplete or invalid")
    return _finite(metric.get("objectiveScore"))


def _finite(value):
    if not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError("Tournament metric must be finite")
    return float(value)


def _load_json(path):
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"Tournament artifact is unavailable or malformed: {path}") from error
    if not isinstance(value, dict):
        raise ValueError(f"Tournament artifact must be an object: {path}")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--offline-directory", required=True)
    parser.add_argument("--search-directory", required=True)
    parser.add_argument("--seeds", default="104729,130363,155921")
    parser.add_argument("--search-call-budget", type=int, default=128)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--report", required=True)
    args = parser.parse_args()
    seeds = tuple(int(value) for value in args.seeds.split(","))
    manifest, report = assemble_tournament(
        args.dataset,
        args.offline_directory,
        args.search_directory,
        seeds,
        args.search_call_budget,
    )
    atomic_write(args.manifest, manifest)
    atomic_write(args.report, report)
    print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
