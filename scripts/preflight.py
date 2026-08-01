#!/usr/bin/env python3
"""Gate a change set before commit or handoff.

This combines the routed checks selected by :mod:`scripts.agent_validate` with a
leak check that rejects staged paths which the repository deliberately keeps
untracked, such as job scripts, scheduler logs, and run outputs.
"""

from __future__ import annotations

import argparse
from fnmatch import fnmatch
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.agent_validate import Check, select_checks  # noqa: E402


ALWAYS_COMMITTABLE = (
    "gradle/wrapper/gradle-wrapper.jar",
)

NEVER_COMMIT_PATTERNS = (
    "*.sh",
    "*.class",
    "*.jar",
    "*.jfr",
    "*.log",
    "*.iml",
    "learning_curve.png",
    "stats_dump.txt",
    "rl_report.html",
    ".claude/settings.local.json",
    ".gradle/*",
    ".venv/*",
    "articles/*",
    "bin/*",
    "build/*",
    "logs/*",
    "output/*",
    "sweeps/*",
    "tools/*",
    "wandb/*",
    "*__pycache__/*",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", help="Git base used to include committed changes")
    parser.add_argument("--path", action="append", default=[], help="Changed path; repeatable")
    parser.add_argument("--staged", action="store_true", help="Restrict discovery to staged paths")
    parser.add_argument("--run", action="store_true", help="Run the selected checks")
    return parser.parse_args()


def is_never_commit(path: str) -> bool:
    """Return whether ``path`` belongs to the repository's never-commit boundary."""
    normalized = path.replace("\\", "/")
    if normalized in ALWAYS_COMMITTABLE:
        return False
    name = normalized.rsplit("/", 1)[-1]
    for pattern in NEVER_COMMIT_PATTERNS:
        if "/" in pattern:
            if fnmatch(normalized, pattern):
                return True
            prefix = pattern.rstrip("*")
            if prefix and not prefix.startswith("*") and normalized.startswith(prefix):
                return True
        elif fnmatch(name, pattern):
            return True
    return False


def find_leaks(paths: list[str]) -> list[str]:
    """Return the subset of ``paths`` that must never be committed."""
    return sorted({path.replace("\\", "/") for path in paths if is_never_commit(path)})


def git_lines(root: Path, command: tuple[str, ...]) -> list[str]:
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
    return [line.strip().replace("\\", "/") for line in result.stdout.splitlines() if line.strip()]


def discover(root: Path, base: str | None, staged_only: bool) -> tuple[list[str], list[str]]:
    """Return ``(all_paths, committable_paths)`` for the current change set.

    ``committable_paths`` covers what a commit would actually record: staged
    entries plus any committed range implied by ``base``. Untracked files are
    reported for check routing but are not leak candidates.
    """
    staged = git_lines(root, ("git", "diff", "--cached", "--name-only"))
    committable = list(staged)
    if base:
        committable.extend(git_lines(root, ("git", "diff", "--name-only", f"{base}...HEAD")))
    if staged_only:
        return sorted(set(staged)), sorted(set(committable))
    everything = set(committable)
    everything.update(git_lines(root, ("git", "diff", "--name-only", "HEAD")))
    everything.update(git_lines(root, ("git", "ls-files", "--others", "--exclude-standard")))
    return sorted(everything), sorted(set(committable))


def run_checks(root: Path, checks: list[Check]) -> int:
    for check in checks:
        print(f"RUN {check.name}", flush=True)
        result = subprocess.run(check.command, cwd=root, check=False)
        if result.returncode != 0:
            print(f"PREFLIGHT_FAIL check={check.name} exit={result.returncode}", file=sys.stderr)
            return result.returncode
    return 0


def main() -> int:
    args = parse_args()
    if args.path:
        paths = sorted({path.replace("\\", "/") for path in args.path})
        committable = paths
    else:
        try:
            paths, committable = discover(ROOT, args.base, args.staged)
        except RuntimeError as error:
            print(f"preflight: {error}", file=sys.stderr)
            return 2

    leaks = find_leaks(committable)
    checks = select_checks(paths)

    print(f"PREFLIGHT_PLAN paths={len(paths)} checks={len(checks)} leaks={len(leaks)}")
    for path in paths:
        print(f"PATH {path}")
    for leak in leaks:
        print(f"LEAK {leak}")
    for check in checks:
        print("CHECK", check.name, subprocess.list2cmdline(check.command))

    if leaks:
        print(f"PREFLIGHT_FAIL leaks={len(leaks)}", file=sys.stderr)
        print("Unstage these paths; they are ignored on purpose.", file=sys.stderr)
        return 1
    if not args.run:
        return 0
    exit_code = run_checks(ROOT, checks)
    if exit_code != 0:
        return exit_code
    print(f"PREFLIGHT_PASS checks={len(checks)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
