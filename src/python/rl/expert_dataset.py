"""Strict readers and typed records for versioned expert trajectory datasets."""

from __future__ import annotations

import gzip
import hashlib
import json
import math
import re
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

from binary_protocol import (
    ACTION_LAYOUT_REVISION,
    OBSERVATION_SCHEMA_REVISION,
    PRIVILEGED_SCHEMA_REVISION,
)
SCHEMA_VERSION = 2
SIMULATOR_REVISION = "rotation-simulator-v4"
ACTION_SIZE = 11
OBSERVATION_SIZE = 287
VALID_SPLITS = frozenset(("train", "validation", "holdout"))


@dataclass(frozen=True)
class ExpertDecision:
    """One validated recurrent expert decision."""

    observation: tuple[float, ...]
    legal_action_mask: tuple[float, ...]
    action_id: int
    visit_policy_target: tuple[float, ...]
    q_estimates: tuple[float, ...]
    state_hash: int
    recurrent_boundary: bool


@dataclass(frozen=True)
class ExpertProvenance:
    """Frozen source, search, quality, and exact-build provenance."""

    source_seed_id: str
    source_content_hash: str
    source_ids: tuple[str, ...]
    source_adaptation_status: str
    parent_record_ids: tuple[str, ...]
    search_mode: str
    feasibility_rank: int
    completed_candidates: int
    completed_generations: int
    human_median_objective: float
    random_median_objective: float
    teacher_median_objective: float
    quality_search_seeds: tuple[int, ...]
    search_seed: int
    build_mode: str
    artifact_standard_revision: str
    build_fingerprint: str
    roll_hash: str
    party_loadout_fingerprint: str
    party_characters: tuple[str, ...]
    party_archetype: str
    scenario_build_fingerprint: str
    er_targets: dict[str, float]
    party_rolls: dict[str, dict[str, int]]


@dataclass(frozen=True)
class ExpertRecord:
    """One hash-validated expert trajectory and its provenance."""

    record_id: str
    record_hash: str
    scenario_fingerprint: str
    party_name: str
    split: str
    seed: int
    search_budget: int
    trajectory_rank: int
    provenance: ExpertProvenance
    decisions: tuple[ExpertDecision, ...]
    terminal_objective: dict[str, Any]


@dataclass(frozen=True)
class ExpertDataset:
    """Validated records plus the source manifest or fixture hash."""

    records: tuple[ExpertRecord, ...]
    source_hash: str


@dataclass(frozen=True)
class ExpertSequenceChunk:
    """One bounded recurrent chunk with optional burn-in decisions."""

    party_name: str
    scenario_fingerprint: str
    decisions: tuple[ExpertDecision, ...]
    loss_start: int
    value_targets: tuple[float, ...]


def load_expert_dataset(path: str | Path) -> ExpertDataset:
    """Load either a manifest or an uncompressed JSONL regression fixture."""
    source = Path(path)
    if not source.is_file():
        raise ValueError(f"Expert dataset does not exist: {source}")
    if source.name.endswith(".jsonl"):
        payload = source.read_bytes()
        records = _parse_lines(payload.decode("utf-8").splitlines(), source)
        _validate_dataset(records)
        return ExpertDataset(tuple(records), hashlib.sha256(payload).hexdigest())
    manifest_bytes = source.read_bytes()
    manifest = _load_json(manifest_bytes.decode("utf-8"), source)
    _require_revision(manifest, "schemaVersion", SCHEMA_VERSION)
    _require_revision(manifest, "simulatorRevision", SIMULATOR_REVISION)
    shards = manifest.get("shards")
    if not isinstance(shards, list) or not shards:
        raise ValueError("Dataset manifest requires non-empty shards")
    records: list[ExpertRecord] = []
    seen_shards: set[str] = set()
    for shard in shards:
        file_name = shard.get("fileName") if isinstance(shard, dict) else None
        expected_hash = shard.get("sha256") if isinstance(shard, dict) else None
        expected_count = shard.get("recordCount") if isinstance(shard, dict) else None
        if not isinstance(file_name, str) or not file_name.startswith("shard-"):
            raise ValueError("Invalid dataset shard name")
        if Path(file_name).name != file_name or file_name in seen_shards:
            raise ValueError("Duplicate or unsafe dataset shard name")
        seen_shards.add(file_name)
        shard_path = source.parent / file_name
        payload = shard_path.read_bytes()
        actual_hash = hashlib.sha256(payload).hexdigest()
        if actual_hash != expected_hash or file_name != f"shard-{actual_hash}.jsonl.gz":
            raise ValueError(f"Dataset shard hash mismatch: {shard_path}")
        try:
            lines = gzip.decompress(payload).decode("utf-8").splitlines()
        except (OSError, UnicodeDecodeError) as error:
            raise ValueError(f"Invalid compressed dataset shard: {shard_path}") from error
        shard_records = _parse_lines(lines, shard_path)
        if len(shard_records) != expected_count:
            raise ValueError(f"Dataset shard record count mismatch: {shard_path}")
        records.extend(shard_records)
    if len(records) != manifest.get("totalRecords"):
        raise ValueError("Dataset manifest totalRecords mismatch")
    _validate_dataset(records)
    return ExpertDataset(
        tuple(records), hashlib.sha256(manifest_bytes).hexdigest()
    )


def training_records(dataset: ExpertDataset) -> tuple[ExpertRecord, ...]:
    """Return train-only records after requiring disjoint validation/holdout."""
    train = tuple(record for record in dataset.records if record.split == "train")
    if not train:
        raise ValueError("Expert dataset has no training records")
    return train


def fingerprints_by_split(dataset: ExpertDataset) -> dict[str, tuple[str, ...]]:
    """Return deterministic scenario fingerprints for every exclusive split."""
    if not isinstance(dataset, ExpertDataset):
        raise ValueError("Expert dataset is required for fingerprint provenance")
    result = {
        split: tuple(
            sorted(
                {
                    record.scenario_fingerprint
                    for record in dataset.records
                    if record.split == split
                }
            )
        )
        for split in sorted(VALID_SPLITS)
    }
    owners: dict[str, str] = {}
    for split, fingerprints in result.items():
        for fingerprint in fingerprints:
            previous = owners.setdefault(fingerprint, split)
            if previous != split:
                raise ValueError(
                    "Scenario fingerprint appears in multiple splits: "
                    f"{fingerprint}"
                )
    return result


def value_normalization(
    records: Iterable[ExpertRecord],
) -> tuple[float, float]:
    """Fit terminal-value normalization from training records only."""
    values = [float(record.terminal_objective["objectiveScore"]) for record in records]
    if not values:
        raise ValueError("Cannot fit value normalization on an empty training split")
    mean = sum(values) / len(values)
    variance = sum((value - mean) ** 2 for value in values) / len(values)
    return mean, max(math.sqrt(variance), 1.0)


def build_party_balanced_order(
    records: Iterable[ExpertRecord], seed: int, epoch: int
) -> tuple[ExpertRecord, ...]:
    """Oversample parties equally with a deterministic epoch-local RNG."""
    groups: dict[str, list[ExpertRecord]] = {}
    for record in records:
        groups.setdefault(record.party_name, []).append(record)
    if not groups:
        raise ValueError("Cannot sample an empty expert dataset")
    generator = random.Random((seed << 32) ^ epoch)
    for group in groups.values():
        generator.shuffle(group)
    target = max(len(group) for group in groups.values())
    order: list[ExpertRecord] = []
    for index in range(target):
        party_names = sorted(groups)
        generator.shuffle(party_names)
        for party_name in party_names:
            group = groups[party_name]
            order.append(group[index % len(group)])
    return tuple(order)


def build_sequence_chunks(
    records: Iterable[ExpertRecord],
    sequence_length: int,
    value_mean: float,
    value_scale: float,
) -> tuple[ExpertSequenceChunk, ...]:
    """Build bounded-loss chunks with one preceding chunk of recurrent burn-in."""
    if sequence_length <= 0 or not _positive_finite(value_scale):
        raise ValueError("sequence_length and value_scale must be positive")
    chunks: list[ExpertSequenceChunk] = []
    for record in records:
        terminal_score = float(record.terminal_objective["objectiveScore"])
        decisions = record.decisions
        for start in range(0, len(decisions), sequence_length):
            context_start = max(0, start - sequence_length)
            end = min(len(decisions), start + sequence_length)
            chunks.append(
                ExpertSequenceChunk(
                    party_name=record.party_name,
                    scenario_fingerprint=record.scenario_fingerprint,
                    decisions=decisions[context_start:end],
                    loss_start=start - context_start,
                    value_targets=tuple(
                        (
                            decision.q_estimates[decision.action_id]
                            if any(abs(value) > 0.0 for value in decision.q_estimates)
                            else terminal_score
                        )
                        / value_scale
                        - value_mean / value_scale
                        for decision in decisions[context_start:end]
                    ),
                )
            )
    return tuple(chunks)


def _parse_lines(lines: Iterable[str], source: Path) -> list[ExpertRecord]:
    records: list[ExpertRecord] = []
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            raise ValueError(f"Blank dataset record at {source}:{line_number}")
        payload = _load_json(line, source)
        records.append(_validate_record(payload, line))
    if not records:
        raise ValueError(f"Expert dataset is empty: {source}")
    return records


def _validate_record(payload: dict[str, Any], source_line: str) -> ExpertRecord:
    _require_revision(payload, "schemaVersion", SCHEMA_VERSION)
    _require_revision(payload, "simulatorRevision", SIMULATOR_REVISION)
    _require_revision(payload, "actionLayoutRevision", ACTION_LAYOUT_REVISION)
    _require_revision(
        payload, "observationSchemaRevision", OBSERVATION_SCHEMA_REVISION
    )
    _require_revision(
        payload, "privilegedSchemaRevision", PRIVILEGED_SCHEMA_REVISION
    )
    record_id = _require_text(payload, "recordId")
    record_hash = _require_text(payload, "recordHash")
    hash_source, substitutions = re.subn(
        r'"recordHash":"[0-9a-f]{64}",', "", source_line, count=1
    )
    actual_record_hash = hashlib.sha256(hash_source.encode("utf-8")).hexdigest()
    if substitutions != 1 or actual_record_hash != record_hash:
        raise ValueError(f"Dataset record hash mismatch: {record_id}")
    split = _require_text(payload, "split")
    if split not in VALID_SPLITS:
        raise ValueError(f"Unknown dataset split: {split}")
    if not _positive_finite(payload.get("cycleDurationSeconds")):
        raise ValueError(f"Invalid cycle duration: {record_id}")
    for name in ("cycleCount", "searchBudget"):
        if not isinstance(payload.get(name), int) or payload[name] <= 0:
            raise ValueError(f"Invalid {name}: {record_id}")
    if (
        not isinstance(payload.get("trajectoryRank"), int)
        or payload["trajectoryRank"] < 0
    ):
        raise ValueError(f"Invalid trajectory rank: {record_id}")
    raw_decisions = payload.get("decisions")
    if not isinstance(raw_decisions, list) or not raw_decisions:
        raise ValueError(f"Dataset trajectory is empty: {record_id}")
    decisions = tuple(
        _validate_decision(decision, index, record_id)
        for index, decision in enumerate(raw_decisions)
    )
    terminal = payload.get("terminalObjective")
    _validate_terminal(terminal, record_id)
    provenance = _validate_provenance(payload.get("provenance"), record_id)
    scenario_fingerprint = _require_text(payload, "scenarioFingerprint")
    seed = _require_int(payload, "seed")
    trajectory_rank = payload["trajectoryRank"]
    if (
        provenance.feasibility_rank != trajectory_rank
        or provenance.search_seed != seed
        or provenance.scenario_build_fingerprint != scenario_fingerprint
    ):
        raise ValueError(f"Dataset provenance identity mismatch: {record_id}")
    if (
        terminal["elapsedSeconds"] + 1.0e-6
        < float(payload["cycleDurationSeconds"]) * payload["cycleCount"]
        or terminal["invalidActionCount"] != 0
        or not terminal["cyclicEnergyFeasible"]
    ):
        raise ValueError(f"Dataset trajectory is not terminal and cyclic: {record_id}")
    return ExpertRecord(
        record_id=record_id,
        record_hash=record_hash,
        scenario_fingerprint=scenario_fingerprint,
        party_name=_require_text(payload, "partyName"),
        split=split,
        seed=seed,
        search_budget=payload["searchBudget"],
        trajectory_rank=trajectory_rank,
        provenance=provenance,
        decisions=decisions,
        terminal_objective=dict(terminal),
    )


def _validate_decision(
    payload: Any, index: int, record_id: str
) -> ExpertDecision:
    if not isinstance(payload, dict):
        raise ValueError(f"Invalid decision object: {record_id}")
    observation = _finite_vector(
        payload.get("observation"), OBSERVATION_SIZE, "observation"
    )
    legal_mask = _finite_vector(
        payload.get("legalActionMask"), ACTION_SIZE, "legalActionMask"
    )
    policy = _finite_vector(
        payload.get("visitPolicyTarget"), ACTION_SIZE, "visitPolicyTarget"
    )
    q_estimates = _finite_vector(
        payload.get("qEstimates"), ACTION_SIZE, "qEstimates"
    )
    action_id = _require_int(payload, "actionId")
    if action_id < 0 or action_id >= ACTION_SIZE or legal_mask[action_id] <= 0.5:
        raise ValueError(f"Dataset action is invalid or masked: {record_id}")
    if any(value < 0.0 for value in policy) or not math.isclose(
        sum(policy), 1.0, rel_tol=0.0, abs_tol=1e-9
    ):
        raise ValueError(f"Invalid visit policy target: {record_id}")
    masked_probability = any(
        policy[action] != 0.0
        for action in range(ACTION_SIZE)
        if legal_mask[action] <= 0.5
    )
    if masked_probability:
        raise ValueError(f"Policy target assigns a masked action: {record_id}")
    recurrent_boundary = payload.get("recurrentBoundary")
    if not isinstance(recurrent_boundary, bool) or recurrent_boundary != (index == 0):
        raise ValueError(f"Invalid recurrent boundary: {record_id}")
    return ExpertDecision(
        observation=observation,
        legal_action_mask=legal_mask,
        action_id=action_id,
        visit_policy_target=policy,
        q_estimates=q_estimates,
        state_hash=_require_int(payload, "stateHash"),
        recurrent_boundary=recurrent_boundary,
    )


def _validate_terminal(payload: Any, record_id: str) -> None:
    if not isinstance(payload, dict):
        raise ValueError(f"Missing terminal objective: {record_id}")
    for field in (
        "totalDamage",
        "dps",
        "elapsedSeconds",
        "energyDeficit",
        "objectiveScore",
    ):
        value = payload.get(field)
        if not isinstance(value, (int, float)) or not math.isfinite(value):
            raise ValueError(f"Invalid terminal objective {field}: {record_id}")
    if not isinstance(payload.get("invalidActionCount"), int):
        raise ValueError(f"Invalid terminal invalidActionCount: {record_id}")
    if not isinstance(payload.get("cyclicEnergyFeasible"), bool):
        raise ValueError(f"Invalid terminal cyclicEnergyFeasible: {record_id}")


def _validate_provenance(payload: Any, record_id: str) -> ExpertProvenance:
    if not isinstance(payload, dict):
        raise ValueError(f"Missing dataset provenance: {record_id}")
    source_seed_id = _require_text(payload, "sourceSeedId")
    source_content_hash = _require_hash(payload, "sourceContentHash")
    source_ids = _text_tuple(payload.get("sourceIds"), "sourceIds")
    adaptation = _require_text(payload, "sourceAdaptationStatus")
    if adaptation not in {"accepted", "adapted"}:
        raise ValueError(f"Unusable source adaptation: {record_id}")
    parents = _text_tuple(
        payload.get("parentRecordIds"), "parentRecordIds", allow_empty=True
    )
    search_mode = _require_text(payload, "searchMode")
    if search_mode not in {"unguided-evolutionary", "unguided-mcts"}:
        raise ValueError(f"Invalid teacher search mode: {record_id}")
    rank = _nonnegative_int(payload, "feasibilityRank")
    completed = _positive_int(payload, "completedCandidates")
    generations = _nonnegative_int(payload, "completedGenerations")
    if search_mode == "unguided-evolutionary" and generations == 0:
        raise ValueError(f"Incomplete evolutionary provenance: {record_id}")
    if (rank == 0) != (len(parents) == 0):
        raise ValueError(f"Invalid dataset parent lineage: {record_id}")
    human = _finite_number(payload, "humanMedianObjective")
    random_baseline = _finite_number(payload, "randomMedianObjective")
    teacher = _finite_number(payload, "teacherMedianObjective")
    if teacher < human or teacher < random_baseline:
        raise ValueError(f"Teacher baseline provenance failed: {record_id}")
    quality_seeds = _int_tuple(payload.get("qualitySearchSeeds"), "qualitySearchSeeds")
    if len(quality_seeds) < 5 or tuple(sorted(set(quality_seeds))) != quality_seeds:
        raise ValueError(f"Teacher quality seeds are incomplete: {record_id}")
    search_seed = _require_int(payload, "searchSeed")
    if payload.get("buildMode") != "optimized-kqms-v1":
        raise ValueError(f"Dataset build mode mismatch: {record_id}")
    if payload.get("artifactStandardRevision") != "artifact-kqms-generic-v1":
        raise ValueError(f"Dataset artifact revision mismatch: {record_id}")
    build_fingerprint = _require_text(payload, "buildFingerprint")
    if not build_fingerprint.startswith("optimized-kqms-v1:"):
        raise ValueError(f"Invalid build fingerprint: {record_id}")
    roll_hash = _require_hash(payload, "rollHash")
    loadout = _require_text(payload, "partyLoadoutFingerprint")
    if "artifact-none" in loadout:
        raise ValueError(f"Dataset loadout omits artifact standard: {record_id}")
    characters = _text_tuple(payload.get("partyCharacters"), "partyCharacters")
    if len(characters) != 4 or len(set(characters)) != 4:
        raise ValueError(f"Invalid party character provenance: {record_id}")
    archetype = _require_text(payload, "partyArchetype")
    if archetype != f"primary:{characters[0]}":
        raise ValueError(f"Invalid party archetype provenance: {record_id}")
    scenario_build = _require_text(payload, "scenarioBuildFingerprint")
    er_targets = _er_targets(payload.get("erTargets"), record_id)
    party_rolls = _party_rolls(payload.get("partyRolls"), record_id)
    if set(er_targets) != set(party_rolls) or set(er_targets) != set(characters):
        raise ValueError(f"Dataset frozen build character mismatch: {record_id}")
    if _roll_hash(party_rolls) != roll_hash:
        raise ValueError(f"Dataset frozen roll hash mismatch: {record_id}")
    return ExpertProvenance(
        source_seed_id=source_seed_id,
        source_content_hash=source_content_hash,
        source_ids=source_ids,
        source_adaptation_status=adaptation,
        parent_record_ids=parents,
        search_mode=search_mode,
        feasibility_rank=rank,
        completed_candidates=completed,
        completed_generations=generations,
        human_median_objective=human,
        random_median_objective=random_baseline,
        teacher_median_objective=teacher,
        quality_search_seeds=quality_seeds,
        search_seed=search_seed,
        build_mode="optimized-kqms-v1",
        artifact_standard_revision="artifact-kqms-generic-v1",
        build_fingerprint=build_fingerprint,
        roll_hash=roll_hash,
        party_loadout_fingerprint=loadout,
        party_characters=characters,
        party_archetype=archetype,
        scenario_build_fingerprint=scenario_build,
        er_targets=er_targets,
        party_rolls=party_rolls,
    )


def _validate_dataset(records: list[ExpertRecord]) -> None:
    seen_ids: set[str] = set()
    fingerprint_splits: dict[str, str] = {}
    indexes: dict[str, int] = {}
    for index, record in enumerate(records):
        if record.record_id in seen_ids:
            raise ValueError(f"Duplicate dataset record ID: {record.record_id}")
        seen_ids.add(record.record_id)
        indexes[record.record_id] = index
        previous = fingerprint_splits.setdefault(
            record.scenario_fingerprint, record.split
        )
        if previous != record.split:
            raise ValueError(
                "Scenario fingerprint appears in multiple splits: "
                f"{record.scenario_fingerprint}"
            )
    for index, record in enumerate(records):
        for parent_id in record.provenance.parent_record_ids:
            parent_index = indexes.get(parent_id)
            if parent_index is None or parent_index >= index:
                raise ValueError(f"Dataset parent must precede child: {parent_id}")
            parent = records[parent_index]
            if (
                parent.scenario_fingerprint != record.scenario_fingerprint
                or parent.provenance.source_content_hash
                != record.provenance.source_content_hash
            ):
                raise ValueError("Dataset lineage crosses source or scenario")


def _finite_vector(value: Any, size: int, name: str) -> tuple[float, ...]:
    if not isinstance(value, list) or len(value) != size:
        raise ValueError(f"{name} dimension mismatch")
    if any(
        not isinstance(item, (int, float)) or not math.isfinite(item)
        for item in value
    ):
        raise ValueError(f"{name} contains a non-finite value")
    return tuple(float(item) for item in value)


def _text_tuple(value: Any, name: str, allow_empty: bool = False) -> tuple[str, ...]:
    if not isinstance(value, list) or (not allow_empty and not value):
        raise ValueError(f"Dataset {name} must be an array")
    if any(not isinstance(item, str) or not item.strip() for item in value):
        raise ValueError(f"Dataset {name} contains invalid text")
    if len(set(value)) != len(value):
        raise ValueError(f"Dataset {name} contains duplicates")
    return tuple(value)


def _int_tuple(value: Any, name: str) -> tuple[int, ...]:
    if not isinstance(value, list) or not value:
        raise ValueError(f"Dataset {name} must be a non-empty array")
    if any(not isinstance(item, int) or isinstance(item, bool) for item in value):
        raise ValueError(f"Dataset {name} contains a non-integer")
    return tuple(value)


def _er_targets(value: Any, record_id: str) -> dict[str, float]:
    if not isinstance(value, dict) or not value:
        raise ValueError(f"Dataset ER targets are missing: {record_id}")
    targets: dict[str, float] = {}
    for character, target in value.items():
        if (
            not isinstance(character, str)
            or not character
            or not isinstance(target, (int, float))
            or isinstance(target, bool)
            or not math.isfinite(target)
            or target < 1.0
        ):
            raise ValueError(f"Invalid dataset ER target: {record_id}")
        targets[character] = float(target)
    return targets


def _party_rolls(value: Any, record_id: str) -> dict[str, dict[str, int]]:
    if not isinstance(value, dict) or not value:
        raise ValueError(f"Dataset party rolls are missing: {record_id}")
    party_rolls: dict[str, dict[str, int]] = {}
    for character, raw_rolls in value.items():
        if (
            not isinstance(character, str)
            or not character
            or not isinstance(raw_rolls, dict)
        ):
            raise ValueError(f"Invalid dataset party rolls: {record_id}")
        rolls: dict[str, int] = {}
        for stat, count in raw_rolls.items():
            if (
                not isinstance(stat, str)
                or not stat
                or not isinstance(count, int)
                or isinstance(count, bool)
                or count < 0
            ):
                raise ValueError(f"Invalid dataset artifact roll: {record_id}")
            rolls[stat] = count
        party_rolls[character] = rolls
    return party_rolls


def _roll_hash(party_rolls: dict[str, dict[str, int]]) -> str:
    canonical = "".join(
        character
        + "{"
        + "".join(
            f"{stat}={party_rolls[character][stat]};"
            for stat in sorted(party_rolls[character])
        )
        + "}"
        for character in sorted(party_rolls)
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _finite_number(payload: dict[str, Any], name: str) -> float:
    value = payload.get(name)
    if (
        not isinstance(value, (int, float))
        or isinstance(value, bool)
        or not math.isfinite(value)
    ):
        raise ValueError(f"Dataset {name} must be finite")
    return float(value)


def _positive_int(payload: dict[str, Any], name: str) -> int:
    value = _require_int(payload, name)
    if value <= 0:
        raise ValueError(f"Dataset {name} must be positive")
    return value


def _nonnegative_int(payload: dict[str, Any], name: str) -> int:
    value = _require_int(payload, name)
    if value < 0:
        raise ValueError(f"Dataset {name} must be non-negative")
    return value


def _require_revision(payload: dict[str, Any], name: str, expected: Any) -> None:
    if payload.get(name) != expected:
        raise ValueError(f"Dataset {name} mismatch: expected {expected}")


def _require_text(payload: dict[str, Any], name: str) -> str:
    value = payload.get(name)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Dataset {name} must not be blank")
    return value


def _require_hash(payload: dict[str, Any], name: str) -> str:
    value = _require_text(payload, name)
    if re.fullmatch(r"[0-9a-f]{64}", value) is None:
        raise ValueError(f"Dataset {name} must be SHA-256")
    return value


def _require_int(payload: dict[str, Any], name: str) -> int:
    value = payload.get(name)
    if not isinstance(value, int) or isinstance(value, bool):
        raise ValueError(f"Dataset {name} must be an integer")
    return value


def _positive_finite(value: Any) -> bool:
    return isinstance(value, (int, float)) and math.isfinite(value) and value > 0.0


def _load_json(text: str, source: Path) -> dict[str, Any]:
    try:
        payload = json.loads(text, parse_constant=lambda value: (_raise_non_finite(value)))
    except (json.JSONDecodeError, ValueError) as error:
        raise ValueError(f"Malformed expert dataset JSON: {source}") from error
    if not isinstance(payload, dict):
        raise ValueError(f"Expert dataset JSON must be an object: {source}")
    return payload


def _raise_non_finite(value: str) -> None:
    raise ValueError(f"Non-finite JSON constant: {value}")
