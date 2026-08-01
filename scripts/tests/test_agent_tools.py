from __future__ import annotations

import unittest
from pathlib import Path

from scripts.agent_validate import select_checks
from scripts.validate_agent_assets import validate


class AgentValidationRouterTest(unittest.TestCase):
    def names(self, *paths: str) -> list[str]:
        return [check.name for check in select_checks(list(paths), windows=False)]

    def test_mechanic_change_routes_build_and_reaction(self) -> None:
        names = self.names("src/java/mechanics/reaction/ReactionSystem.java")
        self.assertEqual(["java-build", "reaction-regression"], names)

    def test_rl_contract_routes_both_stacks(self) -> None:
        java_names = self.names("src/java/mechanics/rl/ObservationEncoder.java")
        self.assertIn("party-catalog-regression", java_names)
        self.assertIn("java-rollout-benchmark", java_names)
        python_names = self.names("src/python/rl/recurrent_ppo.py")
        self.assertEqual(["python-rl-tests"], python_names)

    def test_report_and_optimizer_routes_are_specific(self) -> None:
        self.assertEqual(
            ["java-build", "report-regression"],
            self.names("src/java/visualization/ReportDataBuilder.java"),
        )
        optimizer = self.names("src/java/mechanics/optimization/OptimizerPipeline.java")
        self.assertEqual(["java-build", "raiden-party", "flins-party"], optimizer)

    def test_current_skill_catalog_is_valid(self) -> None:
        root = Path(__file__).resolve().parents[2]
        self.assertEqual([], validate(root))

    def test_both_clients_carry_the_same_skill_assets(self) -> None:
        root = Path(__file__).resolve().parents[2]
        codex_root = root / ".agents" / "skills"
        claude_root = root / ".claude" / "skills"
        for canonical in sorted(path for path in codex_root.iterdir() if path.is_dir()):
            name = canonical.name
            with self.subTest(skill=name):
                metadata = canonical / "agents" / "openai.yaml"
                shim_metadata = claude_root / name / "agents" / "openai.yaml"
                self.assertTrue(shim_metadata.is_file(), f"{name}: Claude shim metadata missing")
                self.assertEqual(
                    metadata.read_bytes(),
                    shim_metadata.read_bytes(),
                    f"{name}: Claude shim metadata differs",
                )


if __name__ == "__main__":
    unittest.main()
