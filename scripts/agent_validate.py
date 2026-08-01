#!/usr/bin/env python3
"""Plan or run the smallest project checks implied by changed paths."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import os
from pathlib import Path
import subprocess
import sys


@dataclass(frozen=True)
class Check:
    name: str
    command: tuple[str, ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", help="Git base used to discover committed changes")
    parser.add_argument("--path", action="append", default=[], help="Changed path; repeatable")
    parser.add_argument("--run", action="store_true", help="Run the selected commands")
    return parser.parse_args()


def git_paths(root: Path, base: str | None) -> list[str]:
    commands = []
    if base:
        commands.append(("git", "diff", "--name-only", f"{base}...HEAD"))
    commands.append(("git", "diff", "--name-only", "HEAD"))
    commands.append(("git", "ls-files", "--others", "--exclude-standard"))
    paths: set[str] = set()
    for command in commands:
        result = subprocess.run(
            command,
            cwd=root,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or "Git path discovery failed")
        paths.update(line.strip().replace("\\", "/") for line in result.stdout.splitlines() if line.strip())
    return sorted(paths)


def select_checks(paths: list[str], windows: bool | None = None) -> list[Check]:
    normalized = [path.replace("\\", "/") for path in paths]
    is_windows = os.name == "nt" if windows is None else windows
    gradle = ("gradlew.bat",) if is_windows else ("./gradlew",)
    checks: list[Check] = []

    def add(name: str, command: tuple[str, ...]) -> None:
        if name not in {check.name for check in checks}:
            checks.append(Check(name, command))

    if any(path == "AGENTS.md" or path == "README.md" or path.startswith((".agents/", ".claude/", "scripts/")) for path in normalized):
        add("agent-assets", (sys.executable, "scripts/validate_agent_assets.py"))
        add("agent-tools-tests", (sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-t", "."))

    java_or_config = any(path == "build.gradle" or path.startswith(("src/java/", "config/characters/", "config/capability_profiles/")) for path in normalized)
    if java_or_config:
        add("java-build", gradle + ("build",))

    mechanics_prefixes = (
        "src/java/mechanics/reaction/",
        "src/java/mechanics/element/",
        "src/java/mechanics/formula/",
        "src/java/mechanics/buff/",
        "src/java/mechanics/energy/",
        "src/java/model/character/",
        "src/java/model/weapon/",
        "src/java/model/artifact/",
        "src/java/simulation/runtime/",
        "src/java/simulation/action/",
        "src/java/simulation/event/",
    )
    if any(path.startswith(mechanics_prefixes) for path in normalized):
        add("reaction-regression", gradle + ("ReactionRegressionTest",))

    if any(path.startswith(("src/java/simulation/party/", "src/java/mechanics/rl/", "src/java/sample/PartyCatalogRegressionTest")) for path in normalized):
        add("party-catalog-regression", gradle + ("PartyCatalogRegressionTest",))

    if any(path.startswith(("src/java/visualization/", "src/java/mechanics/analysis/")) for path in normalized):
        add("report-regression", gradle + ("ReportRegressionTest",))

    if any(path.startswith("src/java/mechanics/optimization/") for path in normalized):
        add("raiden-party", gradle + ("RaidenParty",))
        add("flins-party", gradle + ("FlinsParty2",))

    if any(path.startswith("src/java/mechanics/rl/") for path in normalized):
        add("java-rollout-benchmark", gradle + ("BenchmarkRLJava",))

    if any(path.startswith("src/python/rl/") or path == "requirements.txt" for path in normalized):
        add("python-rl-tests", (sys.executable, "-m", "pytest", "src/python/rl/tests"))

    return checks


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        paths = sorted(set(path.replace("\\", "/") for path in args.path)) or git_paths(root, args.base)
    except RuntimeError as error:
        print(f"agent-validate: {error}", file=sys.stderr)
        return 2
    checks = select_checks(paths)
    print(f"VALIDATION_PLAN paths={len(paths)} checks={len(checks)}")
    for path in paths:
        print(f"PATH {path}")
    for check in checks:
        print("CHECK", check.name, subprocess.list2cmdline(check.command))
    if not args.run:
        return 0
    for check in checks:
        print(f"RUN {check.name}", flush=True)
        result = subprocess.run(check.command, cwd=root, check=False)
        if result.returncode != 0:
            print(f"VALIDATION_FAIL check={check.name} exit={result.returncode}", file=sys.stderr)
            return result.returncode
    print(f"VALIDATION_PASS checks={len(checks)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
