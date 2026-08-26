"""Regression tests for the deterministic teacher dataset quality gate."""

from __future__ import annotations

import os
import sys
from dataclasses import replace

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from evaluate_teacher_dataset import (
    canonical_report,
    evaluate_teacher_dataset,
    load_acceptance,
    load_source_references,
)
from expert_dataset import ExpertDataset, load_expert_dataset


FIXTURE = os.path.join(
    os.path.dirname(__file__), "fixtures", "expert_dataset_v2.jsonl"
)


def test_complete_party_disjoint_quality_gate_is_deterministic():
    dataset = _dataset(40)
    first = evaluate_teacher_dataset(dataset)
    second = evaluate_teacher_dataset(dataset)
    assert first["passed"] is True
    assert first["splitCounts"] == {"holdout": 1, "train": 38, "validation": 1}
    assert first["distinctSourceSeeds"] == 40
    assert first["normalization"]["sourceSplits"] == ["train"]
    assert canonical_report(first) == canonical_report(second)


def test_empty_split_source_leakage_duplicate_and_bad_labels_fail_closed():
    complete = _dataset(40)

    empty_holdout = ExpertDataset(
        tuple(
            replace(record, split="train") if record.split == "holdout" else record
            for record in complete.records
        ),
        complete.source_hash,
    )
    assert "EMPTY_HOLDOUT" in evaluate_teacher_dataset(empty_holdout)["rejectionReasons"]

    records = list(complete.records)
    records[-1] = replace(
        records[-1],
        provenance=replace(
            records[-1].provenance,
            source_ids=("alias-source-reference",),
        ),
    )
    source_references = {
        record.provenance.source_ids[0]: record.provenance.source_ids[0]
        for record in records
    }
    source_references[records[0].provenance.source_ids[0]] = "shared-url"
    source_references["alias-source-reference"] = "shared-url"
    leaked = evaluate_teacher_dataset(
        ExpertDataset(tuple(records), "b" * 64),
        source_references=source_references,
    )
    assert "SOURCE_REFERENCE_LEAKAGE" in leaked["rejectionReasons"]
    try:
        evaluate_teacher_dataset(
            ExpertDataset(tuple(records), "b" * 64),
            source_references={},
        )
    except ValueError as error:
        assert "unknown source ID" in str(error)
    else:
        raise AssertionError("Unknown source ID was accepted")

    records = list(complete.records)
    records[1] = replace(
        records[1],
        provenance=replace(
            records[1].provenance,
            party_loadout_fingerprint=records[0].provenance.party_loadout_fingerprint,
        ),
    )
    duplicated = evaluate_teacher_dataset(ExpertDataset(tuple(records), "c" * 64))
    assert "DUPLICATE_ACTION_TRACE" in duplicated["rejectionReasons"]

    records = list(complete.records)
    records[2] = replace(
        records[2],
        terminal_objective={
            **records[2].terminal_objective,
            "cyclicEnergyFeasible": False,
        },
    )
    infeasible = evaluate_teacher_dataset(ExpertDataset(tuple(records), "d" * 64))
    assert "INFEASIBLE_LABEL" in infeasible["rejectionReasons"]

    records = list(complete.records)
    records[3] = replace(
        records[3],
        provenance=replace(
            records[3].provenance,
            teacher_median_objective=0.0,
        ),
    )
    below = evaluate_teacher_dataset(ExpertDataset(tuple(records), "e" * 64))
    assert "TEACHER_BELOW_BASELINE" in below["rejectionReasons"]

    records[3] = replace(
        records[3],
        provenance=replace(
            records[3].provenance,
            teacher_median_objective=float("nan"),
        ),
    )
    non_finite = evaluate_teacher_dataset(
        ExpertDataset(tuple(records), "f" * 64)
    )
    assert "TEACHER_BELOW_BASELINE" in non_finite["rejectionReasons"]


def test_breadth_hash_support_bound_and_train_only_normalization_fail_closed():
    narrow = _dataset(3)
    report = evaluate_teacher_dataset(narrow, expected_hash="f" * 64)
    assert "INSUFFICIENT_SOURCE_SEEDS" in report["rejectionReasons"]
    assert "DATASET_HASH_MISMATCH" in report["rejectionReasons"]

    complete = _dataset(40)
    records = list(complete.records)
    validation = records[-2]
    for index in range(3):
        train = records[index]
        characters = list(validation.provenance.party_characters)
        characters[index + 1] = train.provenance.party_characters[index + 1]
        validation = replace(
            validation,
            provenance=replace(
                validation.provenance,
                party_characters=tuple(characters),
            ),
        )
    records[-2] = validation
    support = evaluate_teacher_dataset(
        ExpertDataset(tuple(records), "1" * 64),
        maximum_shared_supports=2,
    )
    assert "SUPPORT_LEAKAGE_EXCEEDS_BOUND" in support["rejectionReasons"]

    records = list(complete.records)
    records[-2] = replace(
        records[-2],
        terminal_objective={
            **records[-2].terminal_objective,
            "objectiveScore": 1.0e30,
        },
    )
    shifted = evaluate_teacher_dataset(ExpertDataset(tuple(records), "2" * 64))
    assert shifted["normalization"] == evaluate_teacher_dataset(complete)["normalization"]


def test_frozen_acceptance_metadata_rejects_malformed_values(tmp_path):
    acceptance = tmp_path / "accepted.json"
    acceptance.write_text(
        '{"schemaVersion":1,"datasetSchemaVersion":2,'
        '"simulatorRevision":"rotation-simulator-v4","datasetHash":"'
        + "a" * 64
        + '","sourceCatalogHash":"'
        + "b" * 64
        + '","minimumSourceSeeds":40,"maximumSharedSupports":8}',
        encoding="utf-8",
    )
    assert load_acceptance(acceptance) == ("a" * 64, "b" * 64, 40, 8)

    acceptance.write_text(
        '{"schemaVersion":1,"datasetSchemaVersion":2,'
        '"simulatorRevision":"rotation-simulator-v4","datasetHash":"bad",'
        '"sourceCatalogHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",'
        '"minimumSourceSeeds":40,"maximumSharedSupports":8}',
        encoding="utf-8",
    )
    try:
        load_acceptance(acceptance)
    except ValueError as error:
        assert "malformed" in str(error)
    else:
        raise AssertionError("Malformed acceptance metadata was accepted")

    catalog = tmp_path / "catalog.json"
    catalog.write_text(
        '{"sources":[{"sourceId":"source-a",'
        '"url":"https://example.test/guide"}]}',
        encoding="utf-8",
    )
    assert load_source_references(catalog) == {
        "source-a": "https://example.test/guide"
    }


def _dataset(count: int) -> ExpertDataset:
    base = load_expert_dataset(FIXTURE).records[0]
    records = []
    for index in range(count):
        split = "train"
        if index == count - 2:
            split = "validation"
        elif index == count - 1:
            split = "holdout"
        scenario = f"scenario-{index}"
        characters = tuple(f"CHARACTER_{index}_{slot}" for slot in range(4))
        provenance = replace(
            base.provenance,
            source_seed_id=f"source-seed-{index}",
            source_content_hash=f"{index + 1:064x}",
            source_ids=(f"source-reference-{index}",),
            search_seed=1000 + index,
            party_loadout_fingerprint=f"loadout-{index}",
            party_characters=characters,
            party_archetype=f"primary:{characters[0]}",
            scenario_build_fingerprint=scenario,
        )
        records.append(
            replace(
                base,
                record_id=f"record-{index}",
                record_hash=f"{index + 100:064x}",
                scenario_fingerprint=scenario,
                party_name=f"Party{index}",
                split=split,
                seed=1000 + index,
                provenance=provenance,
            )
        )
    return ExpertDataset(tuple(records), "a" * 64)
