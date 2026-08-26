"""Validate equal-budget rotation generalization benchmark artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path

from evaluation import (
    GENERALIZATION_REPORT_REVISION,
    GENERALIZATION_REPORT_SCHEMA_VERSION,
    RotationEvaluationPreset,
    build_rotation_generalization_report,
)
from expert_dataset import SIMULATOR_REVISION
from rotation_model_registry import evaluate_tournament_manifest

BENCHMARK_PRESET = RotationEvaluationPreset(
    name="benchmark",
    seeds=(104729, 130363, 155921),
    search_call_budget=128,
    simulator_revision=SIMULATOR_REVISION,
)
DEFAULT_JAVA_REPORT = "output/rotation_generalization/java-benchmark.json"
DEFAULT_OUTPUT = "output/rotation_generalization/report.json"


def main(argv=None):
    args = parse_args(argv)
    preset = BENCHMARK_PRESET
    input_hashes = {}
    try:
        java_summary, java_hash = load_json_object(args.java_report)
        input_hashes["javaBenchmarkSha256"] = java_hash
        model_summary = None
        if args.model_summary is not None:
            model_summary, model_hash = load_json_object(args.model_summary)
            input_hashes["modelSummarySha256"] = model_hash
        report = build_rotation_generalization_report(
            java_summary, model_summary, preset
        )
        if args.tournament_manifest is not None:
            tournament, tournament_hash = load_json_object(
                args.tournament_manifest
            )
            input_hashes["tournamentManifestSha256"] = tournament_hash
            tournament_report = evaluate_tournament_manifest(tournament)
            report["modelTournament"] = tournament_report
            if not tournament_report["qualityGatePassed"]:
                report["qualityGatePassed"] = False
                report["failures"].append(
                    "matched model tournament produced no qualifying champion"
                )
    except (OSError, ValueError) as error:
        report = failure_report(preset, str(error))
    report["inputs"] = input_hashes
    atomic_write_json(args.output, report)
    if not report["qualityGatePassed"]:
        for failure in report["failures"]:
            print(f"FAIL: {failure}", file=sys.stderr)
        print(f"Wrote failed evaluation report: {args.output}", file=sys.stderr)
        return 1
    print(f"Generalization quality gate passed: {args.output}")
    return 0


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description=(
            "Validate a fixed-seed, equal-call-budget rotation generalization "
            "benchmark without running or inventing missing measurements."
        )
    )
    parser.add_argument(
        "--preset",
        choices=("benchmark",),
        required=True,
        help="required reproducible evaluation preset",
    )
    parser.add_argument(
        "--java-report",
        default=DEFAULT_JAVA_REPORT,
        help="Java benchmark JSON containing replay, split, and simulator results",
    )
    parser.add_argument(
        "--model-summary",
        default=None,
        help=(
            "optional model-only service JSON; model-only runs and checkpoint "
            "provenance must exist in one of the supplied inputs"
        ),
    )
    parser.add_argument(
        "--tournament-manifest",
        default=None,
        help="optional matched model/seed tournament manifest",
    )
    parser.add_argument(
        "--output",
        default=DEFAULT_OUTPUT,
        help="atomic JSON quality report destination",
    )
    return parser.parse_args(argv)


def load_json_object(path):
    source = Path(path)
    try:
        payload = source.read_bytes()
    except OSError as error:
        raise ValueError(f"Required evaluation input is unavailable: {source}") from error
    try:
        value = json.loads(
            payload.decode("utf-8"),
            parse_constant=lambda constant: _reject_non_finite(constant),
        )
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
        raise ValueError(f"Malformed evaluation JSON: {source}") from error
    if not isinstance(value, dict):
        raise ValueError(f"Evaluation JSON must be an object: {source}")
    return value, hashlib.sha256(payload).hexdigest()


def failure_report(preset, failure):
    return {
        "schemaVersion": GENERALIZATION_REPORT_SCHEMA_VERSION,
        "reportRevision": GENERALIZATION_REPORT_REVISION,
        "preset": {
            "name": preset.name,
            "seeds": list(preset.seeds),
            "searchCallBudget": preset.search_call_budget,
        },
        "simulatorRevision": preset.simulator_revision,
        "qualityGatePassed": False,
        "failures": [failure],
        "datasetReplay": None,
        "fingerprintSplits": {
            "train": [],
            "validation": [],
            "holdout": [],
        },
        "checkpointProvenance": None,
        "unsupportedScenarios": None,
        "metrics": {},
        "runCount": 0,
    }


def atomic_write_json(path, payload):
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    encoded = (
        json.dumps(
            payload,
            ensure_ascii=True,
            indent=2,
            sort_keys=True,
            allow_nan=False,
        )
        + "\n"
    ).encode("utf-8")
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=destination.parent,
            prefix=f".{destination.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            temporary_path = Path(handle.name)
            handle.write(encoded)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_path, destination)
        directory_fd = os.open(destination.parent, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    finally:
        if temporary_path is not None and temporary_path.exists():
            temporary_path.unlink()


def _reject_non_finite(value):
    raise ValueError(f"Non-finite JSON constant: {value}")


if __name__ == "__main__":
    raise SystemExit(main())
