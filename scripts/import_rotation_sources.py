#!/usr/bin/env python3
"""Normalize pinned, reviewed rotation snapshots into non-trainable candidates."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from scripts.rotation_source_mapping import MappingResult, normalize_sequence


CANDIDATE_VERSION = 1


def import_payload(payload: dict[str, Any]) -> dict[str, Any]:
    """Return a deterministic candidate report without copying arbitrary payload fields."""
    if not isinstance(payload, dict) or set(payload) != {"sources"}:
        raise ValueError("input root must contain only sources")
    sources = payload["sources"]
    if not isinstance(sources, list):
        raise ValueError("sources must be an array")
    seen_ids: set[str] = set()
    seen_urls: set[str] = set()
    candidates = []
    for source in sorted(sources, key=lambda value: _required_text(value, "sourceId")):
        required = {
            "sourceId", "title", "url", "publisher", "sourceRevision", "accessDate",
            "targetCount", "partyName", "partySlots", "opener", "cycles", "erTargets",
            "assumptions",
        }
        if not isinstance(source, dict) or set(source) != required:
            raise ValueError("source snapshot fields do not match importer contract")
        source_id = _required_text(source["sourceId"], "sourceId")
        url = _required_text(source["url"], "url")
        if source_id in seen_ids or url in seen_urls:
            raise ValueError("duplicate source ID or URL")
        seen_ids.add(source_id)
        seen_urls.add(url)
        cycles = source["cycles"]
        if not isinstance(cycles, list) or not cycles:
            raise ValueError(f"cycles must be non-empty: {source_id}")
        slots = source["partySlots"]
        opener = normalize_sequence(source["opener"], slots)
        mapped_cycles = [normalize_sequence(cycle, slots) for cycle in cycles]
        target_count = source["targetCount"]
        if not isinstance(target_count, int) or target_count < 1:
            raise ValueError(f"invalid targetCount: {source_id}")
        assumptions = source["assumptions"]
        if not isinstance(assumptions, list) or any(not isinstance(item, str) for item in assumptions):
            raise ValueError(f"invalid assumptions: {source_id}")
        blockers = []
        if target_count != 1:
            blockers.append("multi_target_only")
        unresolved = [item for result in [opener, *mapped_cycles] for item in result.unresolved]
        adaptations = sorted({item for result in [opener, *mapped_cycles]
                              for item in result.adaptations})
        candidates.append({
            "sourceId": source_id,
            "title": _required_text(source["title"], "title"),
            "url": url,
            "publisher": _required_text(source["publisher"], "publisher"),
            "sourceRevision": _required_text(source["sourceRevision"], "sourceRevision"),
            "accessDate": _required_text(source["accessDate"], "accessDate"),
            "targetCount": target_count,
            "partyName": _required_text(source["partyName"], "partyName"),
            "partySlots": list(slots),
            "opener": _result(opener),
            "cycles": [_result(result) for result in mapped_cycles],
            "erTargets": _er_targets(source["erTargets"]),
            "assumptions": assumptions,
            "adaptationRequired": bool(adaptations),
            "adaptations": adaptations,
            "unresolved": unresolved,
            "blockers": blockers,
            "reviewStatus": "candidate" if not unresolved and not blockers else "blocked",
        })
    return {"candidateVersion": CANDIDATE_VERSION, "candidates": candidates}


def _result(result: MappingResult) -> dict[str, Any]:
    return {
        "actions": list(result.actions),
        "consumed": list(result.consumed),
        "unresolved": list(result.unresolved),
        "annotations": list(result.annotations),
    }


def _er_targets(value: object) -> dict[str, float]:
    if not isinstance(value, dict) or not value:
        raise ValueError("erTargets must be a non-empty object")
    result: dict[str, float] = {}
    for key in sorted(value):
        target = value[key]
        if not isinstance(key, str) or not isinstance(target, (int, float)) or target < 1.0:
            raise ValueError("invalid ER target")
        result[key] = float(target)
    return result


def _required_text(value: object, field: str) -> str:
    if isinstance(value, dict):
        value = value.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field} must be non-empty text")
    return value


def main() -> None:
    """Read a pinned JSON snapshot and write a review-only candidate report."""
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    with args.input.open(encoding="utf-8") as stream:
        payload = json.load(stream)
    report = import_payload(payload)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
