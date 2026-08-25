#!/usr/bin/env python3
"""Restricted, fail-closed mapping for reviewed rotation notation."""

from __future__ import annotations

import dataclasses
import math
import re
from typing import Any, Iterable


ACTION_IDS = {
    "N": 0,
    "C": 1,
    "P": 2,
    "E": 3,
    "HE": 4,
    "Q": 5,
    "WAIT": 6,
}
SWAP_IDS = (7, 8, 9, 10)
_ACTION_PATTERN = re.compile(r"(?:N\d*|C|P|hE|E|Q)")
_WAIT_PATTERN = re.compile(r"wait\s+([0-9]+(?:\.[0-9]+)?)s", re.IGNORECASE)
_ER_PATTERN = re.compile(r"ER\s*(?:>=|:)\s*([0-9]+(?:\.[0-9]+)?)%", re.IGNORECASE)


@dataclasses.dataclass(frozen=True)
class MappingResult:
    """Deterministic normalized output for one notation sequence."""

    actions: tuple[int, ...]
    consumed: tuple[str, ...]
    unresolved: tuple[dict[str, str], ...]
    adaptations: tuple[str, ...]
    annotations: tuple[dict[str, Any], ...]

    @property
    def accepted(self) -> bool:
        """Return whether every token mapped exactly without adaptation."""
        return not self.unresolved and not self.adaptations


def normalize_sequence(notation: str | Iterable[str], party_slots: Iterable[str]) -> MappingResult:
    """Map one arrow/list sequence while preserving every unsupported token."""
    slots = tuple(_required_text(value, "party slot") for value in party_slots)
    if len(slots) != 4 or len(set(slots)) != 4:
        raise ValueError("party_slots must contain four distinct names")
    tokens = _tokens(notation)
    actions: list[int] = []
    consumed: list[str] = []
    unresolved: list[dict[str, str]] = []
    adaptations: list[str] = []
    annotations: list[dict[str, Any]] = []
    active_slot = 0

    for token in tokens:
        original = token
        lowered = token.lower()
        if any(marker in lowered for marker in (" if ", "while ", "until ", "infinite")):
            unresolved.append({"token": original, "reason": "conditional_or_unbounded"})
            continue
        if re.search(r"(?:^|[^A-Za-z])[DJ](?:$|[^A-Za-z])", token):
            unresolved.append({"token": original, "reason": "unsupported_cancel"})
            continue
        er_match = _ER_PATTERN.fullmatch(token.strip())
        if er_match:
            annotations.append({"kind": "er", "fraction": float(er_match.group(1)) / 100.0})
            consumed.append(original)
            continue
        wait_match = _WAIT_PATTERN.fullmatch(token.strip())
        if wait_match:
            duration = float(wait_match.group(1))
            wait_count = round(duration / 0.1)
            if duration <= 0.0 or not math.isclose(wait_count * 0.1, duration, abs_tol=1.0e-9):
                unresolved.append({"token": original, "reason": "wait_not_0.1_aligned"})
                continue
            actions.extend([ACTION_IDS["WAIT"]] * wait_count)
            consumed.append(original)
            continue

        explicit_swap = re.fullmatch(r"swap\s+(.+)", token.strip(), re.IGNORECASE)
        if explicit_swap:
            slot = _slot(explicit_swap.group(1), slots)
            if slot is None:
                unresolved.append({"token": original, "reason": "unknown_character"})
                continue
            actions.append(SWAP_IDS[slot])
            active_slot = slot
            consumed.append(original)
            continue

        character_slot, expression = _character_expression(token, slots)
        if character_slot is None or expression is None:
            unresolved.append({"token": original, "reason": "unknown_notation"})
            continue
        mapped = _map_expression(expression)
        if mapped is None:
            unresolved.append({"token": original, "reason": "unknown_combo_shorthand"})
            continue
        if character_slot != active_slot:
            actions.append(SWAP_IDS[character_slot])
            adaptations.append(f"automatic_swap:{slots[character_slot]}")
            active_slot = character_slot
        actions.extend(mapped)
        consumed.append(original)

    return MappingResult(
        tuple(actions),
        tuple(consumed),
        tuple(unresolved),
        tuple(adaptations),
        tuple(annotations),
    )


def _tokens(notation: str | Iterable[str]) -> tuple[str, ...]:
    if isinstance(notation, str):
        raw = notation.split(">")
    elif isinstance(notation, Iterable):
        raw = list(notation)
    else:
        raise ValueError("notation must be a string or string list")
    tokens = tuple(_required_text(token, "rotation token").strip() for token in raw)
    if not tokens:
        raise ValueError("rotation sequence must not be empty")
    return tokens


def _character_expression(token: str, slots: tuple[str, ...]) -> tuple[int | None, str | None]:
    for slot, character in sorted(enumerate(slots), key=lambda item: len(item[1]), reverse=True):
        if token.casefold().startswith(character.casefold() + " "):
            return slot, token[len(character):].strip()
    return None, None


def _map_expression(expression: str) -> list[int] | None:
    compact = expression.replace(" ", "").replace("[", "").replace("]", "")
    actions: list[int] = []
    position = 0
    while position < len(compact):
        multiplier_match = re.match(r"(\d+)(?=E)", compact[position:])
        multiplier = 1
        if multiplier_match:
            multiplier = int(multiplier_match.group(1))
            position += len(multiplier_match.group(1))
        match = _ACTION_PATTERN.match(compact, position)
        if match is None:
            return None
        code = match.group(0)
        position = match.end()
        if code.startswith("N"):
            count = int(code[1:] or "1")
            actions.extend([ACTION_IDS["N"]] * count)
        else:
            normalized = "HE" if code == "hE" else code
            actions.extend([ACTION_IDS[normalized]] * multiplier)
    return actions or None


def _slot(name: str, slots: tuple[str, ...]) -> int | None:
    for slot, character in enumerate(slots):
        if character.casefold() == name.strip().casefold():
            return slot
    return None


def _required_text(value: object, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field} must be non-empty text")
    return value
