"""Tests for fail-closed offline rotation notation import."""

from __future__ import annotations

import copy
import unittest

from scripts.import_rotation_sources import import_payload
from scripts.rotation_source_mapping import normalize_sequence


SLOTS = ("Raiden", "Xingqiu", "Xiangling", "Bennett")


class RotationSourceMappingTest(unittest.TestCase):
    """Checks exact mappings and explicit unresolved diagnostics."""

    def test_kqm_arrow_and_gcsl_list_map_deterministically(self) -> None:
        arrow = normalize_sequence(
            "Raiden E > Xingqiu Q E N1 > wait 1.0s > Raiden Q N1C",
            SLOTS,
        )
        listed = normalize_sequence(
            ["Raiden E", "Xingqiu Q E N1", "wait 1.0s", "Raiden Q N1C"],
            SLOTS,
        )
        self.assertEqual(arrow, listed)
        self.assertEqual((3, 8, 5, 3, 0, *([6] * 10), 7, 5, 0, 1), arrow.actions)
        self.assertEqual(("automatic_swap:Xingqiu", "automatic_swap:Raiden"), arrow.adaptations)
        self.assertFalse(arrow.unresolved)

    def test_hold_plunge_multi_skill_and_annotations(self) -> None:
        result = normalize_sequence(
            ["Raiden hE", "Raiden P", "Raiden 2[E]", "ER>=180%"],
            SLOTS,
        )
        self.assertEqual((4, 2, 3, 3), result.actions)
        self.assertEqual(({"kind": "er", "fraction": 1.8},), result.annotations)

    def test_unsupported_tokens_are_never_discarded(self) -> None:
        result = normalize_sequence(
            ["Raiden N1D", "Raiden E if aura", "wait infinite", "Raiden N2XYZ"],
            SLOTS,
        )
        self.assertEqual(4, len(result.unresolved))
        self.assertEqual(set(), set(result.consumed))
        self.assertEqual(
            {"unsupported_cancel", "conditional_or_unbounded", "unknown_combo_shorthand"},
            {item["reason"] for item in result.unresolved},
        )

    def test_import_is_deterministic_and_candidates_are_not_accepted(self) -> None:
        payload = fixture_payload()
        first = import_payload(payload)
        second = import_payload(copy.deepcopy(payload))
        self.assertEqual(first, second)
        candidate = first["candidates"][0]
        self.assertEqual("candidate", candidate["reviewStatus"])
        self.assertNotIn("contentHash", candidate)
        self.assertNotIn("adaptationStatus", candidate)
        self.assertTrue(candidate["adaptationRequired"])

    def test_malformed_duplicate_and_multi_target_sources_fail_closed(self) -> None:
        duplicate = fixture_payload()
        duplicate["sources"].append(copy.deepcopy(duplicate["sources"][0]))
        duplicate["sources"][1]["sourceId"] = "duplicate-url"
        with self.assertRaisesRegex(ValueError, "duplicate"):
            import_payload(duplicate)
        multi_target = fixture_payload()
        multi_target["sources"][0]["targetCount"] = 2
        candidate = import_payload(multi_target)["candidates"][0]
        self.assertEqual("blocked", candidate["reviewStatus"])
        malformed = fixture_payload()
        malformed["sources"][0]["executable"] = "rm -rf /"
        with self.assertRaisesRegex(ValueError, "fields"):
            import_payload(malformed)


def fixture_payload() -> dict[str, object]:
    """Return a minimal pinned source snapshot."""
    return {"sources": [{
        "sourceId": "kqm-raiden-national",
        "title": "Raiden Guide",
        "url": "https://keqingmains.com/raiden/",
        "publisher": "KeqingMains",
        "sourceRevision": "2025-01-30",
        "accessDate": "2026-08-25",
        "targetCount": 1,
        "partyName": "RaidenParty",
        "partySlots": list(SLOTS),
        "opener": ["Raiden E"],
        "cycles": [["Xingqiu Q E N1", "Bennett Q E", "Xiangling Q E", "Raiden Q N1C"]],
        "erTargets": {"RAIDEN_SHOGUN": 2.5, "XINGQIU": 1.8,
                      "XIANGLING": 2.0, "BENNETT": 2.0},
        "assumptions": ["single target"],
    }]}


if __name__ == "__main__":
    unittest.main()
