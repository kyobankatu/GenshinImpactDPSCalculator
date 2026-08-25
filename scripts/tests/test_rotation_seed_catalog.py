"""Tests for the offline rotation-source catalog validator."""

from __future__ import annotations

import copy
import unittest

from scripts.validate_rotation_seeds import canonical_seed_hash, validate_catalog


class RotationSeedCatalogTest(unittest.TestCase):
    """Checks deterministic hashing and fail-closed source validation."""

    def test_canonical_fixture_hash(self) -> None:
        catalog = fixture_catalog()
        seed = catalog["seeds"][0]
        seed["contentHash"] = canonical_seed_hash(seed)
        self.assertEqual("273373cc8af131cc10c985d34ca0f6346b185b6859d294f2acfc2293f21c9ec8",
                         seed["contentHash"])
        validate_catalog(catalog)

    def test_unknown_source_and_hash_mismatch_rejected(self) -> None:
        catalog = fixture_catalog()
        catalog["seeds"][0]["contentHash"] = canonical_seed_hash(catalog["seeds"][0])
        unknown = copy.deepcopy(catalog)
        unknown["seeds"][0]["sourceIds"] = ["missing"]
        with self.assertRaises(ValueError):
            validate_catalog(unknown)
        corrupted = copy.deepcopy(catalog)
        corrupted["seeds"][0]["partyName"] = "changed"
        with self.assertRaises(ValueError):
            validate_catalog(corrupted)


def fixture_catalog() -> dict[str, object]:
    """Return the cross-language canonical seed fixture."""
    return {
        "schemaVersion": 1,
        "actionLayoutRevision": 2,
        "sources": [{
            "sourceId": "kqm-raiden-guide",
            "title": "Raiden Guide",
            "url": "https://keqingmains.com/q/raiden-quickguide/",
            "publisher": "KeqingMains",
            "sourceRevision": "2025-01-30",
            "accessDate": "2026-08-25",
            "targetCount": 1,
            "loadoutAssumptions": ["single target"],
        }],
        "seeds": [{
            "seedId": "fixture-raiden-national",
            "partyName": "RaidenParty",
            "scenarioFingerprint": "loadout-v1:RAIDEN_SHOGUN-c6-SkywardSpine-EmblemOfSeveredFate:XINGQIU-c6-WolfFang-EmblemOfSeveredFate:XIANGLING-c6-TheCatch-EmblemOfSeveredFate:BENNETT-c6-SkywardBlade-NoblesseOblige",
            "sourceIds": ["kqm-raiden-guide"],
            "adaptationStatus": "accepted",
            "adaptationNote": "",
            "openerActions": [3],
            "cycleActions": [[8, 5, 3, 10, 5, 3, 9, 5, 3, 7, 5, 0, 1]],
            "erTargets": {
                "RAIDEN_SHOGUN": 2.5,
                "XINGQIU": 1.8,
                "XIANGLING": 2.0,
                "BENNETT": 2.0,
            },
            "sourceAssumptions": ["single target"],
            "simulatorAssumptions": ["fixed enemy"],
            "contentHash": "",
        }],
    }


if __name__ == "__main__":
    unittest.main()
