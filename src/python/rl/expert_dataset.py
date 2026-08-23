"""Strict readers and typed records for versioned expert trajectory datasets."""

from __future__ import annotations

import gzip
import hashlib
import json
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

from binary_protocol import (
    ACTION_LAYOUT_REVISION,
    OBSERVATION_SCHEMA_REVISION,
    PRIVILEGED_SCHEMA_REVISION,
)
SCHEMA_VERSION = 1
SIMULATOR_REVISION = "rotation-simulator-v1"
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
    decisions: tuple[ExpertDecision, ...]
    terminal_objective: dict[str, Any]


@dataclass(frozen=True)
class ExpertDataset:
    """Validated records plus the source manifest or fixture hash."""

    records: tuple[ExpertRecord, ...]
    source_hash: str


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
    if substitutions != 1 or hashlib.sha256(hash_source.encode("utf-8")).hexdigest() != record_hash:
        raise ValueError(f"Dataset record hash mismatch: {record_id}")
    split = _require_text(payload, "split")
    if split not in VALID_SPLITS:
        raise ValueError(f"Unknown dataset split: {split}")
    if not _positive_finite(payload.get("cycleDurationSeconds")):
        raise ValueError(f"Invalid cycle duration: {record_id}")
    for name in ("cycleCount", "searchBudget"):
        if not isinstance(payload.get(name), int) or payload[name] <= 0:
            raise ValueError(f"Invalid {name}: {record_id}")
    if not isinstance(payload.get("trajectoryRank"), int) or payload["trajectoryRank"] < 0:
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
    return ExpertRecord(
        record_id=record_id,
        record_hash=record_hash,
        scenario_fingerprint=_require_text(payload, "scenarioFingerprint"),
        party_name=_require_text(payload, "partyName"),
        split=split,
        seed=_require_int(payload, "seed"),
        search_budget=payload["searchBudget"],
        trajectory_rank=payload["trajectoryRank"],
        decisions=decisions,
        terminal_objective=dict(terminal),
    )


def _validate_decision(
    payload: Any, index: int, record_id: str
) -> ExpertDecision:
    if not isinstance(payload, dict):
        raise ValueError(f"Invalid decision object: {record_id}")
    observation = _finite_vector(payload.get("observation"), OBSERVATION_SIZE, "observation")
    legal_mask = _finite_vector(
        payload.get("legalActionMask"), ACTION_SIZE, "legalActionMask"
    )
    policy = _finite_vector(
        payload.get("visitPolicyTarget"), ACTION_SIZE, "visitPolicyTarget"
    )
    q_estimates = _finite_vector(payload.get("qEstimates"), ACTION_SIZE, "qEstimates")
    action_id = _require_int(payload, "actionId")
    if action_id < 0 or action_id >= ACTION_SIZE or legal_mask[action_id] <= 0.5:
        raise ValueError(f"Dataset action is invalid or masked: {record_id}")
    if any(value < 0.0 for value in policy) or not math.isclose(
        sum(policy), 1.0, rel_tol=0.0, abs_tol=1e-9
    ):
        raise ValueError(f"Invalid visit policy target: {record_id}")
    if any(policy[action] != 0.0 for action in range(ACTION_SIZE) if legal_mask[action] <= 0.5):
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


def _validate_dataset(records: list[ExpertRecord]) -> None:
    seen_ids: set[str] = set()
    fingerprint_splits: dict[str, str] = {}
    for record in records:
        if record.record_id in seen_ids:
            raise ValueError(f"Duplicate dataset record ID: {record.record_id}")
        seen_ids.add(record.record_id)
        previous = fingerprint_splits.setdefault(
            record.scenario_fingerprint, record.split
        )
        if previous != record.split:
            raise ValueError(
                "Scenario fingerprint appears in multiple splits: "
                f"{record.scenario_fingerprint}"
            )


def _finite_vector(value: Any, size: int, name: str) -> tuple[float, ...]:
    if not isinstance(value, list) or len(value) != size:
        raise ValueError(f"{name} dimension mismatch")
    if any(not isinstance(item, (int, float)) or not math.isfinite(item) for item in value):
        raise ValueError(f"{name} contains a non-finite value")
    return tuple(float(item) for item in value)


def _require_revision(payload: dict[str, Any], name: str, expected: Any) -> None:
    if payload.get(name) != expected:
        raise ValueError(f"Dataset {name} mismatch: expected {expected}")


def _require_text(payload: dict[str, Any], name: str) -> str:
    value = payload.get(name)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Dataset {name} must not be blank")
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
