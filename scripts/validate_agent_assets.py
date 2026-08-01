#!/usr/bin/env python3
"""Validate project skill discovery, metadata, references, and Claude shims."""

from __future__ import annotations

import re
import sys
from pathlib import Path


FRONTMATTER = re.compile(r"\A---\n(.*?)\n---\n", re.DOTALL)
REFERENCE_LINK = re.compile(r"\]\((references/[^)]+)\)")
SAFE_NAME = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")


def parse_frontmatter(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    match = FRONTMATTER.match(text)
    if match is None:
        raise ValueError("missing YAML frontmatter")
    result: dict[str, str] = {}
    for raw in match.group(1).splitlines():
        if not raw.strip():
            continue
        if ":" not in raw:
            raise ValueError("malformed frontmatter line")
        key, value = raw.split(":", 1)
        result[key.strip()] = value.strip()
    if set(result) != {"name", "description"}:
        raise ValueError("frontmatter must contain only name and description")
    return result


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    codex_root = root / ".agents" / "skills"
    claude_root = root / ".claude" / "skills"
    if not codex_root.is_dir():
        return ["missing .agents/skills"]
    if not claude_root.is_dir():
        return ["missing .claude/skills"]

    codex_names = {path.name for path in codex_root.iterdir() if path.is_dir()}
    claude_names = {path.name for path in claude_root.iterdir() if path.is_dir()}
    if codex_names != claude_names:
        errors.append("Codex and Claude skill catalogs differ")

    for name in sorted(codex_names):
        canonical = codex_root / name / "SKILL.md"
        shim = claude_root / name / "SKILL.md"
        metadata = codex_root / name / "agents" / "openai.yaml"
        for path in (canonical, shim, metadata):
            if not path.is_file() or path.is_symlink():
                errors.append(f"{path.relative_to(root)} must be a regular file")
        if not canonical.is_file() or not shim.is_file():
            continue
        try:
            canonical_meta = parse_frontmatter(canonical)
            shim_meta = parse_frontmatter(shim)
        except (OSError, UnicodeError, ValueError) as error:
            errors.append(f"{name}: {error}")
            continue
        if canonical_meta != shim_meta:
            errors.append(f"{name}: Claude shim frontmatter differs")
        if canonical_meta["name"] != name or SAFE_NAME.fullmatch(name) is None:
            errors.append(f"{name}: invalid or mismatched skill name")
        canonical_text = canonical.read_text(encoding="utf-8")
        shim_text = shim.read_text(encoding="utf-8")
        if "TODO" in canonical_text or "TODO" in shim_text:
            errors.append(f"{name}: unresolved TODO")
        if f".agents/skills/{name}/SKILL.md" not in shim_text:
            errors.append(f"{name}: Claude shim does not name canonical skill")
        for relative in REFERENCE_LINK.findall(canonical_text):
            target = canonical.parent / relative
            if not target.is_file() or target.is_symlink():
                errors.append(f"{name}: missing reference {relative}")
        if metadata.is_file():
            metadata_text = metadata.read_text(encoding="utf-8")
            if f"${name}" not in metadata_text:
                errors.append(f"{name}: default prompt does not invoke the skill")

    root_agents = (root / "AGENTS.md").read_text(encoding="utf-8")
    readme = (root / "README.md").read_text(encoding="utf-8")
    for marker in (".agents/skills", ".claude/skills", "scripts/agent_validate.py"):
        if marker not in root_agents and marker not in readme:
            errors.append(f"discovery documentation missing {marker}")
    return errors


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    errors = validate(root)
    if errors:
        for error in errors:
            print(f"AGENT_ASSETS fail: {error}", file=sys.stderr)
        return 1
    count = len(list((root / ".agents" / "skills").iterdir()))
    print(f"AGENT_ASSETS pass skills={count} clients=2")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
