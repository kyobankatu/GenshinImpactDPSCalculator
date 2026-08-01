from __future__ import annotations

import unittest

from scripts.preflight import find_leaks, is_never_commit


class NeverCommitBoundaryTest(unittest.TestCase):
    def test_job_scripts_are_never_committable(self) -> None:
        self.assertTrue(is_never_commit("execute.sh"))
        self.assertTrue(is_never_commit("execute_learner.sh"))
        self.assertTrue(is_never_commit("evaluate.sh"))

    def test_run_artifacts_are_never_committable(self) -> None:
        for path in (
            "logs/recurrent_ppo.79814",
            "output/recurrent_ppo_py/latest-model.pt",
            "wandb/run-abc/files/config.yaml",
            "sweeps/rollout.yaml",
            "build/classes/java/main/sample/RaidenParty.class",
            "bin/Main.class",
            ".gradle/8.0/fileHashes",
            "src/python/rl/__pycache__/recurrent_ppo.cpython-314.pyc",
            ".claude/settings.local.json",
            "rl_report.html",
            "stats_dump.txt",
            "learning_curve.png",
        ):
            with self.subTest(path=path):
                self.assertTrue(is_never_commit(path))

    def test_tracked_sources_are_committable(self) -> None:
        for path in (
            "src/java/mechanics/formula/DamageCalculator.java",
            "src/python/rl/train_recurrent_ppo.py",
            "config/characters/Flins_Status.csv",
            "docs/simulation_report.html",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradlew",
            "build.gradle",
            "TASKS.md",
            ".agents/skills/manage-genshin-git/SKILL.md",
            ".claude/skills/manage-genshin-git/SKILL.md",
            "scripts/preflight.py",
        ):
            with self.subTest(path=path):
                self.assertFalse(is_never_commit(path))

    def test_find_leaks_reports_sorted_unique_paths(self) -> None:
        leaks = find_leaks(
            [
                "logs/a",
                "src/java/Main.java",
                "execute.sh",
                "logs/a",
            ]
        )
        self.assertEqual(["execute.sh", "logs/a"], leaks)

    def test_gradle_wrapper_jar_survives_the_jar_pattern(self) -> None:
        self.assertFalse(is_never_commit("gradle/wrapper/gradle-wrapper.jar"))
        self.assertTrue(is_never_commit("dist/app.jar"))


if __name__ == "__main__":
    unittest.main()
