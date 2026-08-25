#!/usr/bin/env python3
"""Validate the tracked rotation-source catalog without network access."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import date
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


SCHEMA_VERSION = 1
ACTION_LAYOUT_REVISION = 2
ACTION_COUNT = 11
DEFAULT_CATALOG = Path("config/rotation_seeds/catalog.json")


def canonical_seed_hash(seed: dict[str, Any]) -> str:
    """Return the cross-language canonical SHA-256 for one seed."""
    payload = dict(seed)
    payload.pop("contentHash", None)
    canonical = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def validate_catalog(catalog: dict[str, Any]) -> None:
    """Validate schema-shaped fields, provenance, action IDs, and hashes."""
    required_root = {"schemaVersion", "actionLayoutRevision", "sources", "seeds"}
    if set(catalog) != required_root:
        raise ValueError("catalog root fields do not match schema_v1")
    if catalog["schemaVersion"] != SCHEMA_VERSION:
        raise ValueError("rotation source schema revision mismatch")
    if catalog["actionLayoutRevision"] != ACTION_LAYOUT_REVISION:
        raise ValueError("rotation action layout revision mismatch")
    if not isinstance(catalog["sources"], list) or not isinstance(catalog["seeds"], list):
        raise ValueError("sources and seeds must be arrays")

    source_ids: set[str] = set()
    for source in catalog["sources"]:
        required_source = {
            "sourceId", "title", "url", "publisher", "sourceRevision",
            "accessDate", "targetCount", "loadoutAssumptions",
        }
        if not isinstance(source, dict) or set(source) != required_source:
            raise ValueError("source fields do not match schema_v1")
        source_id = _required_text(source["sourceId"], "sourceId")
        if source_id in source_ids:
            raise ValueError(f"duplicate source ID: {source_id}")
        source_ids.add(source_id)
        for field in ("title", "publisher", "sourceRevision"):
            _required_text(source[field], field)
        parsed = urlparse(_required_text(source["url"], "url"))
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise ValueError(f"source URL must be HTTP(S): {source_id}")
        date.fromisoformat(_required_text(source["accessDate"], "accessDate"))
        if not isinstance(source["targetCount"], int) or source["targetCount"] < 1:
            raise ValueError(f"invalid targetCount: {source_id}")
        if not isinstance(source["loadoutAssumptions"], list):
            raise ValueError(f"invalid loadoutAssumptions: {source_id}")

    seed_ids: set[str] = set()
    for seed in catalog["seeds"]:
        _validate_seed(seed, source_ids, seed_ids)


def _validate_seed(seed: Any, source_ids: set[str], seed_ids: set[str]) -> None:
    required_seed = {
        "seedId", "partyName", "scenarioFingerprint", "sourceIds",
        "adaptationStatus", "adaptationNote", "openerActions", "cycleActions",
        "erTargets", "sourceAssumptions", "simulatorAssumptions", "contentHash",
    }
    if not isinstance(seed, dict) or set(seed) != required_seed:
        raise ValueError("seed fields do not match schema_v1")
    seed_id = _required_text(seed["seedId"], "seedId")
    if seed_id in seed_ids:
        raise ValueError(f"duplicate seed ID: {seed_id}")
    seed_ids.add(seed_id)
    _required_text(seed["partyName"], "partyName")
    _required_text(seed["scenarioFingerprint"], "scenarioFingerprint")
    if seed["adaptationStatus"] not in {"accepted", "adapted", "rejected"}:
        raise ValueError(f"invalid adaptation status: {seed_id}")
    if seed["adaptationStatus"] == "adapted" and not str(seed["adaptationNote"]).strip():
        raise ValueError(f"adapted seed requires note: {seed_id}")
    references = seed["sourceIds"]
    if not isinstance(references, list) or not references or len(references) != len(set(references)):
        raise ValueError(f"invalid sourceIds: {seed_id}")
    if not set(references).issubset(source_ids):
        raise ValueError(f"unknown source reference: {seed_id}")
    _validate_actions(seed["openerActions"], False, seed_id)
    cycles = seed["cycleActions"]
    if not isinstance(cycles, list) or not cycles:
        raise ValueError(f"cycleActions must not be empty: {seed_id}")
    for cycle in cycles:
        _validate_actions(cycle, True, seed_id)
    targets = seed["erTargets"]
    if not isinstance(targets, dict) or not targets:
        raise ValueError(f"erTargets must not be empty: {seed_id}")
    if any(not isinstance(value, (int, float)) or value < 1.0 for value in targets.values()):
        raise ValueError(f"invalid ER target: {seed_id}")
    for field in ("sourceAssumptions", "simulatorAssumptions"):
        if not isinstance(seed[field], list):
            raise ValueError(f"{field} must be an array: {seed_id}")
    if seed["contentHash"] != canonical_seed_hash(seed):
        raise ValueError(f"seed content hash mismatch: {seed_id}")


def _validate_actions(actions: Any, require_nonempty: bool, seed_id: str) -> None:
    if not isinstance(actions, list) or (require_nonempty and not actions):
        raise ValueError(f"invalid action list: {seed_id}")
    if any(not isinstance(action, int) or not 0 <= action < ACTION_COUNT for action in actions):
        raise ValueError(f"unknown action ID: {seed_id}")


def _required_text(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field} must not be blank")
    return value


def main() -> None:
    """Validate a catalog selected on the command line."""
    parser = argparse.ArgumentParser()
    parser.add_argument("catalog", nargs="?", type=Path, default=DEFAULT_CATALOG)
    args = parser.parse_args()
    with args.catalog.open(encoding="utf-8") as stream:
        catalog = json.load(stream)
    validate_catalog(catalog)
    print(f"validated rotation source catalog: {len(catalog['sources'])} sources, {len(catalog['seeds'])} seeds")


if __name__ == "__main__":
    main()
