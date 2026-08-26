import os
import sys

import pytest
import torch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from evaluate_rotation_optimizer import (
    BENCHMARK_PRESET,
    atomic_write_json,
    load_json_object,
    main,
)
from evaluate_policy import save_generalization_traces
from expert_dataset import load_expert_dataset
from evaluation import (
    RotationGeneralizationError,
    build_rotation_generalization_report,
    validate_rotation_generalization,
)


def test_complete_equal_budget_generalization_report_passes(tmp_path):
    java_summary, model_summary = _valid_inputs()
    report = validate_rotation_generalization(
        java_summary, model_summary, BENCHMARK_PRESET
    )
    assert report["qualityGatePassed"] is True
    assert report["failures"] == []
    medians = report["metrics"]["holdoutMedianObjective"]
    assert medians["model_only"] > medians["deterministic_random"]
    assert medians["guided_search"] >= medians["unguided_search"]

    destination = tmp_path / "nested" / "report.json"
    atomic_write_json(destination, report)
    loaded, digest = load_json_object(destination)
    assert loaded == report
    assert len(digest) == 64
    assert not list(destination.parent.glob("*.tmp"))


def test_model_trace_artifact_preserves_train_only_provenance(tmp_path):
    fixture = os.path.join(
        os.path.dirname(__file__), "fixtures", "expert_dataset_v2.jsonl"
    )
    dataset = load_expert_dataset(fixture)
    fingerprint = dataset.records[0].scenario_fingerprint
    checkpoint = tmp_path / "model.pt"
    torch.save(
        {
            "simulator_revision": BENCHMARK_PRESET.simulator_revision,
            "dataset_source_hash": dataset.source_hash,
            "training_fingerprints": [fingerprint],
            "normalization_fingerprints": [fingerprint],
        },
        checkpoint,
    )
    destination = tmp_path / "traces.json"
    save_generalization_traces(
        {
            "per_party": {
                "FixtureParty": {
                    "action_trace": [0, 6],
                    "invalid_actions": 0,
                }
            }
        },
        checkpoint,
        fixture,
        destination,
    )
    artifact, _ = load_json_object(destination)
    assert artifact["trainingFingerprints"] == [fingerprint]
    assert artifact["normalizationFingerprints"] == [fingerprint]
    assert artifact["traces"][0]["actionTrace"] == [0, 6]


def test_java_benchmark_wire_shape_is_normalized():
    java_summary, model_summary = _valid_inputs()
    wire_metrics = []
    method_names = {
        "deterministic_random": "deterministic-random",
        "unguided_search": "unguided-evolutionary",
        "guided_search": "policy-guided",
    }
    for run in java_summary["runs"]:
        wire_metrics.append(
            {
                "method": method_names[run["method"]],
                "seed": run["seed"],
                "split": run["split"],
                "scenarioFingerprint": run["scenarioFingerprint"],
                "horizonSeconds": run["horizonSeconds"],
                "simulatorCalls": run["simulatorCalls"],
                "wallTimeNanos": 100_000_000,
                "totalDamage": run["terminalDamage"],
                "dps": run["dps"],
                "energyDeficit": run["cyclicEnergyDeficit"],
                "invalidActionCount": run["invalidActions"],
                "invalidActionRate": run["invalidActionRate"],
                "objectiveScore": run["objectiveScore"],
                "archiveDiversity": run["archiveDiversity"],
                "archiveScores": run["archiveScores"],
                "bestFoundActions": [0, 6],
                "simulatorRevision": run["simulatorRevision"],
                "datasetRevision": "fixture-dataset-v1",
            }
        )
    wire_summary = {
        "schemaVersion": 1,
        "benchmarkRevision": "rotation-search-benchmark-v1",
        "simulatorRevision": BENCHMARK_PRESET.simulator_revision,
        "datasetReplay": {
            "schemaVersion": 1,
            "sourceHash": "a" * 64,
            "totalRecords": 10,
            "replayedRecords": 10,
            "replayRate": 1.0,
        },
        "unsupportedComparisons": [],
        "metrics": wire_metrics,
    }
    report = validate_rotation_generalization(
        wire_summary, model_summary, BENCHMARK_PRESET
    )
    assert report["qualityGatePassed"] is True


@pytest.mark.parametrize(
    ("mutation", "message"),
    (
        (
            lambda java, model: model["checkpointProvenance"][
                "trainingFingerprints"
            ].append("holdout-fingerprint"),
            "holdout fingerprint leaked",
        ),
        (
            lambda java, model: java["runs"][0].__setitem__(
                "simulatorCalls", BENCHMARK_PRESET.search_call_budget - 1
            ),
            "unequal search call budget",
        ),
        (
            lambda java, model: java.__setitem__(
                "simulatorRevision", "stale-simulator"
            ),
            "simulator revision mismatch",
        ),
        (
            lambda java, model: java["runs"][0].pop("seed"),
            "seed is missing",
        ),
        (
            lambda java, model: java["datasetReplay"].update(
                {"replayedRecords": 8, "failedRecords": 1, "replayRate": 0.8}
            ),
            "dataset replay is partial",
        ),
        (
            lambda java, model: java["runs"][0].__setitem__(
                "objectiveScore", float("nan")
            ),
            "objectiveScore must be finite",
        ),
        (
            lambda java, model: java["runs"][0].__setitem__(
                "scenarioFingerprint", "unsupported-fingerprint"
            ),
            "unsupported scenario",
        ),
    ),
)
def test_invalid_generalization_inputs_fail_closed(mutation, message):
    java_summary, model_summary = _valid_inputs()
    mutation(java_summary, model_summary)
    with pytest.raises(RotationGeneralizationError, match=message) as captured:
        validate_rotation_generalization(
            java_summary, model_summary, BENCHMARK_PRESET
        )
    assert captured.value.report["qualityGatePassed"] is False
    assert captured.value.report["failures"]


def test_missing_model_summary_and_archive_regression_fail_closed():
    java_summary, model_summary = _valid_inputs()
    report = build_rotation_generalization_report(
        java_summary, None, BENCHMARK_PRESET
    )
    assert report["qualityGatePassed"] is False
    assert any("checkpoint provenance is missing" in item for item in report["failures"])
    assert any("missing model_only run" in item for item in report["failures"])

    java_summary, model_summary = _valid_inputs()
    java_summary["runs"][0]["archiveScores"] = [10.0, 9.0]
    with pytest.raises(RotationGeneralizationError, match="archive regressed"):
        validate_rotation_generalization(
            java_summary, model_summary, BENCHMARK_PRESET
        )


def test_non_finite_json_is_rejected_before_validation(tmp_path):
    source = tmp_path / "nan.json"
    source.write_text('{"value": NaN}', encoding="utf-8")
    with pytest.raises(ValueError, match="Malformed evaluation JSON"):
        load_json_object(source)


def test_missing_required_input_writes_failed_report(tmp_path, capsys):
    output = tmp_path / "failed-report.json"
    exit_code = main(
        [
            "--preset",
            "benchmark",
            "--java-report",
            str(tmp_path / "missing.json"),
            "--output",
            str(output),
        ]
    )
    assert exit_code == 1
    report, _digest = load_json_object(output)
    assert report["qualityGatePassed"] is False
    assert "Required evaluation input is unavailable" in report["failures"][0]
    assert "FAIL:" in capsys.readouterr().err


def _valid_inputs():
    splits = {
        "train": ["train-fingerprint"],
        "validation": ["validation-fingerprint"],
        "holdout": ["holdout-fingerprint"],
    }
    java_runs = []
    model_runs = []
    for split, fingerprints in splits.items():
        for fingerprint in fingerprints:
            for seed in BENCHMARK_PRESET.seeds:
                java_runs.extend(
                    (
                        _run(
                            "deterministic_random",
                            split,
                            fingerprint,
                            seed,
                            10.0,
                        ),
                        _run(
                            "unguided_search",
                            split,
                            fingerprint,
                            seed,
                            12.0,
                        ),
                        _run(
                            "guided_search",
                            split,
                            fingerprint,
                            seed,
                            13.0,
                        ),
                    )
                )
                model_runs.append(
                    _run("model_only", split, fingerprint, seed, 11.0)
                )
    java_summary = {
        "schemaVersion": 1,
        "simulatorRevision": BENCHMARK_PRESET.simulator_revision,
        "datasetReplay": {
            "datasetSchemaVersion": 1,
            "datasetSourceHash": "a" * 64,
            "expectedRecords": 10,
            "replayedRecords": 10,
            "failedRecords": 0,
            "replayRate": 1.0,
        },
        "fingerprintSplits": splits,
        "unsupportedScenarios": [],
        "runs": java_runs,
    }
    model_summary = {
        "schemaVersion": 1,
        "simulatorRevision": BENCHMARK_PRESET.simulator_revision,
        "checkpointProvenance": {
            "checkpointRevision": "fixture-checkpoint-v1",
            "datasetSourceHash": "a" * 64,
            "trainingFingerprints": ["train-fingerprint"],
            "normalizationFingerprints": ["train-fingerprint"],
        },
        "runs": model_runs,
    }
    return java_summary, model_summary


def _run(method, split, fingerprint, seed, score):
    run = {
        "method": method,
        "split": split,
        "scenarioFingerprint": fingerprint,
        "seed": seed,
        "horizonSeconds": 42.0,
        "simulatorCalls": (
            1 if method == "model_only" else BENCHMARK_PRESET.search_call_budget
        ),
        "objectiveScore": score,
        "terminalDamage": score * 42.0,
        "dps": score,
        "cyclicEnergyDeficit": 0.0,
        "invalidActions": 0,
        "totalActions": 2,
        "invalidActionRate": 0.0,
        "archiveDiversity": 1.0,
        "wallTimeSeconds": 0.1,
        "simulatorRevision": BENCHMARK_PRESET.simulator_revision,
    }
    if method == "model_only":
        run["actionTrace"] = [0, 6]
    else:
        run["archiveScores"] = [score - 1.0, score]
    return run
