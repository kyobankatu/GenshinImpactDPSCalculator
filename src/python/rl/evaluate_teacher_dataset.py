"""Deterministic split, leakage, diversity, and teacher-quality gate."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from collections import Counter, defaultdict
from pathlib import Path
from typing import Iterable

from expert_dataset import (
    ExpertDataset,
    ExpertRecord,
    VALID_SPLITS,
    load_expert_dataset,
    training_records,
    value_normalization,
)


REPORT_SCHEMA_VERSION = 1
DEFAULT_MANIFEST = Path("output/rotation_dataset/manifest.json")
DEFAULT_REPORT = Path("output/rotation_dataset/quality-report.json")
DEFAULT_ACCEPTANCE = Path("config/rotation_dataset/accepted_v2.json")
DEFAULT_SOURCE_CATALOG = Path("config/rotation_seeds/catalog.json")


def evaluate_teacher_dataset(
    dataset: ExpertDataset,
    minimum_source_seeds: int = 40,
    maximum_shared_supports: int = 8,
    expected_hash: str | None = None,
    source_references: dict[str, str] | None = None,
) -> dict[str, object]:
    """Return canonical evidence and typed rejection reasons for one dataset."""
    if minimum_source_seeds <= 0 or maximum_shared_supports < 0:
        raise ValueError("Dataset quality thresholds are invalid")
    records = tuple(dataset.records)
    reasons: set[str] = set()
    split_counts = Counter(record.split for record in records)
    for split in VALID_SPLITS:
        if split_counts[split] == 0:
            reasons.add(f"EMPTY_{split.upper()}")

    source_seed_owners = _owners(
        (record.provenance.source_seed_id, record.split) for record in records
    )
    source_owners = _owners(
        (_source_reference(source_id, source_references), record.split)
        for record in records
        for source_id in record.provenance.source_ids
    )
    loadout_owners = _owners(
        (record.provenance.party_loadout_fingerprint, record.split)
        for record in records
    )
    if _leaked(source_seed_owners):
        reasons.add("SOURCE_SEED_LEAKAGE")
    if _leaked(source_owners):
        reasons.add("SOURCE_REFERENCE_LEAKAGE")
    if _leaked(loadout_owners):
        reasons.add("PARTY_LOADOUT_LEAKAGE")

    duplicate_pairs = _near_duplicate_pairs(records)
    if duplicate_pairs:
        reasons.add("DUPLICATE_ACTION_TRACE")

    invalid_records: list[str] = []
    below_baseline: list[str] = []
    for record in records:
        objective = record.terminal_objective
        provenance = record.provenance
        if (
            objective.get("invalidActionCount") != 0
            or objective.get("cyclicEnergyFeasible") is not True
            or not _finite(objective.get("objectiveScore"))
        ):
            invalid_records.append(record.record_id)
        if (
            not _finite(provenance.teacher_median_objective)
            or provenance.teacher_median_objective
            < provenance.human_median_objective
            or provenance.teacher_median_objective
            < provenance.random_median_objective
        ):
            below_baseline.append(record.record_id)
    if invalid_records:
        reasons.add("INFEASIBLE_LABEL")
    if below_baseline:
        reasons.add("TEACHER_BELOW_BASELINE")

    distinct_seeds = len(source_seed_owners)
    if distinct_seeds < minimum_source_seeds:
        reasons.add("INSUFFICIENT_SOURCE_SEEDS")
    if expected_hash is not None and dataset.source_hash != expected_hash:
        reasons.add("DATASET_HASH_MISMATCH")

    support_owners = _owners(
        (character, record.split)
        for record in records
        for character in record.provenance.party_characters[1:]
    )
    support_leakage = _pairwise_overlap(support_owners)
    if any(
        len(characters) > maximum_shared_supports
        for characters in support_leakage.values()
    ):
        reasons.add("SUPPORT_LEAKAGE_EXCEEDS_BOUND")

    train = tuple(record for record in records if record.split == "train")
    normalization = None
    if train:
        mean, scale = value_normalization(training_records(dataset))
        normalization = {
            "sourceSplits": ["train"],
            "recordIds": sorted(record.record_id for record in train),
            "mean": mean,
            "scale": scale,
        }

    report: dict[str, object] = {
        "schemaVersion": REPORT_SCHEMA_VERSION,
        "datasetHash": dataset.source_hash,
        "expectedDatasetHash": expected_hash,
        "recordCount": len(records),
        "splitCounts": {split: split_counts[split] for split in sorted(VALID_SPLITS)},
        "scenarioCounts": {
            split: len(
                {
                    record.scenario_fingerprint
                    for record in records
                    if record.split == split
                }
            )
            for split in sorted(VALID_SPLITS)
        },
        "distinctSourceSeeds": distinct_seeds,
        "minimumSourceSeeds": minimum_source_seeds,
        "maximumSharedSupports": maximum_shared_supports,
        "partyCounts": dict(
            sorted(Counter(record.party_name for record in records).items())
        ),
        "archetypeCounts": dict(
            sorted(
                Counter(
                    record.provenance.party_archetype for record in records
                ).items()
            )
        ),
        "sourceSeedOwners": _serializable_owners(source_seed_owners),
        "sourceReferenceOwners": _serializable_owners(source_owners),
        "loadoutOwners": _serializable_owners(loadout_owners),
        "characterOwners": _serializable_owners(_owners(
            (character, record.split)
            for record in records
            for character in record.provenance.party_characters
        )),
        "sharedSupports": support_leakage,
        "nearDuplicatePairs": duplicate_pairs,
        "invalidRecords": sorted(invalid_records),
        "belowBaselineRecords": sorted(below_baseline),
        "normalization": normalization,
        "rejectionReasons": sorted(reasons),
        "passed": not reasons,
    }
    return report


def canonical_report(report: dict[str, object]) -> str:
    """Serialize report evidence deterministically."""
    return json.dumps(report, indent=2, sort_keys=True, allow_nan=False) + "\n"


def load_acceptance(path: Path) -> tuple[str, str, int, int]:
    """Load the frozen dataset hash and quality thresholds."""
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"Invalid dataset acceptance metadata: {path}") from error
    if (
        not isinstance(payload, dict)
        or payload.get("schemaVersion") != 1
        or payload.get("datasetSchemaVersion") != 2
        or payload.get("simulatorRevision") != "rotation-simulator-v4"
    ):
        raise ValueError("Dataset acceptance schema mismatch")
    dataset_hash = payload.get("datasetHash")
    source_catalog_hash = payload.get("sourceCatalogHash")
    minimum_seeds = payload.get("minimumSourceSeeds")
    maximum_supports = payload.get("maximumSharedSupports")
    if (
        not isinstance(dataset_hash, str)
        or len(dataset_hash) != 64
        or any(character not in "0123456789abcdef" for character in dataset_hash)
        or not isinstance(source_catalog_hash, str)
        or len(source_catalog_hash) != 64
        or any(
            character not in "0123456789abcdef"
            for character in source_catalog_hash
        )
        or not isinstance(minimum_seeds, int)
        or isinstance(minimum_seeds, bool)
        or minimum_seeds <= 0
        or not isinstance(maximum_supports, int)
        or isinstance(maximum_supports, bool)
        or maximum_supports < 0
    ):
        raise ValueError("Dataset acceptance metadata is malformed")
    return dataset_hash, source_catalog_hash, minimum_seeds, maximum_supports


def load_source_references(path: Path) -> dict[str, str]:
    """Load source IDs as canonical URLs for cross-split leakage checks."""
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"Invalid rotation source catalog: {path}") from error
    sources = payload.get("sources") if isinstance(payload, dict) else None
    if not isinstance(sources, list) or not sources:
        raise ValueError("Rotation source catalog has no sources")
    references: dict[str, str] = {}
    for source in sources:
        source_id = source.get("sourceId") if isinstance(source, dict) else None
        url = source.get("url") if isinstance(source, dict) else None
        if (
            not isinstance(source_id, str)
            or not source_id
            or not isinstance(url, str)
            or not url.startswith(("https://", "http://"))
            or source_id in references
        ):
            raise ValueError("Rotation source catalog contains invalid references")
        references[source_id] = url
    return references


def _source_reference(
    source_id: str, references: dict[str, str] | None
) -> str:
    if references is None:
        return source_id
    reference = references.get(source_id)
    if reference is None:
        raise ValueError(f"Dataset references unknown source ID: {source_id}")
    return reference


def _owners(entries: Iterable[tuple[str, str]]) -> dict[str, set[str]]:
    owners: dict[str, set[str]] = defaultdict(set)
    for identity, split in entries:
        owners[identity].add(split)
    return dict(owners)


def _leaked(owners: dict[str, set[str]]) -> bool:
    return any(len(splits) > 1 for splits in owners.values())


def _serializable_owners(owners: dict[str, set[str]]) -> dict[str, list[str]]:
    return {
        identity: sorted(splits)
        for identity, splits in sorted(owners.items())
    }


def _pairwise_overlap(owners: dict[str, set[str]]) -> dict[str, list[str]]:
    overlap: dict[str, list[str]] = {
        "train-validation": [],
        "train-holdout": [],
        "validation-holdout": [],
    }
    for identity, splits in owners.items():
        for pair in overlap:
            first, second = pair.split("-")
            if first in splits and second in splits:
                overlap[pair].append(identity)
    return {pair: sorted(identities) for pair, identities in overlap.items()}


def _near_duplicate_pairs(records: tuple[ExpertRecord, ...]) -> list[list[str]]:
    by_loadout: dict[str, list[ExpertRecord]] = defaultdict(list)
    for record in records:
        by_loadout[record.provenance.party_loadout_fingerprint].append(record)
    duplicates: list[list[str]] = []
    for group in by_loadout.values():
        for first_index, first in enumerate(group):
            first_actions = tuple(decision.action_id for decision in first.decisions)
            for second in group[first_index + 1 :]:
                second_actions = tuple(decision.action_id for decision in second.decisions)
                limit = max(1, math.ceil(max(len(first_actions), len(second_actions)) * 0.05))
                if _edit_distance(first_actions, second_actions) <= limit:
                    duplicates.append(sorted([first.record_id, second.record_id]))
    return sorted(duplicates)


def _edit_distance(first: tuple[int, ...], second: tuple[int, ...]) -> int:
    previous = list(range(len(second) + 1))
    for first_index, first_action in enumerate(first, start=1):
        current = [first_index]
        for second_index, second_action in enumerate(second, start=1):
            current.append(
                min(
                    previous[second_index] + 1,
                    current[second_index - 1] + 1,
                    previous[second_index - 1] + (first_action != second_action),
                )
            )
        previous = current
    return previous[-1]


def _finite(value: object) -> bool:
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(value)
    )


def main() -> None:
    """Evaluate one manifest, persist evidence, and fail on any quality reason."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--preset", choices=("benchmark",), default="benchmark")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--acceptance", type=Path, default=DEFAULT_ACCEPTANCE)
    parser.add_argument(
        "--source-catalog", type=Path, default=DEFAULT_SOURCE_CATALOG
    )
    parser.add_argument("--minimum-source-seeds", type=int, default=40)
    parser.add_argument("--maximum-shared-supports", type=int, default=8)
    parser.add_argument("--expected-hash")
    args = parser.parse_args()
    expected_hash = args.expected_hash
    expected_source_catalog_hash = None
    if args.acceptance.is_file():
        (
            accepted_hash,
            expected_source_catalog_hash,
            accepted_minimum,
            accepted_maximum,
        ) = load_acceptance(args.acceptance)
        if (
            args.minimum_source_seeds != accepted_minimum
            or args.maximum_shared_supports != accepted_maximum
        ):
            raise ValueError("CLI quality thresholds differ from frozen acceptance")
        if expected_hash is not None and expected_hash != accepted_hash:
            raise ValueError("CLI dataset hash differs from frozen acceptance")
        expected_hash = accepted_hash
    dataset = load_expert_dataset(args.manifest)
    source_catalog_hash = hashlib.sha256(args.source_catalog.read_bytes()).hexdigest()
    if (
        expected_source_catalog_hash is not None
        and source_catalog_hash != expected_source_catalog_hash
    ):
        raise ValueError("Rotation source catalog differs from frozen acceptance")
    source_references = load_source_references(args.source_catalog)
    report = evaluate_teacher_dataset(
        dataset,
        minimum_source_seeds=args.minimum_source_seeds,
        maximum_shared_supports=args.maximum_shared_supports,
        expected_hash=expected_hash,
        source_references=source_references,
    )
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(canonical_report(report), encoding="utf-8")
    print(f"datasetHash={report['datasetHash']}")
    print(f"records={report['recordCount']}")
    print(f"passed={str(report['passed']).lower()}")
    print(f"rejectionReasons={report['rejectionReasons']}")
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
