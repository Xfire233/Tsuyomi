# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
import subprocess
import sys
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parents[1]
TOOLING_PATH = Path("TOOLING.md")
DOCUMENTATION_PATH = Path("DOCUMENTATION.md")
PROJECT_SKILLS_ROOT = Path(".agents/skills")
TOOLING_REQUIRED_SECTIONS = (
    "## Resource record standard",
    "## Deterministic dispatch",
    "## Scope and completion",
    "## Native and repository tools",
    "## Skills",
    "## MCP ownership",
    "## Ambiguity and exclusion examples",
    "## Change procedure",
)
TOOLING_REQUIRED_MARKERS = (
    "| Field | Meaning |",
    "| Scope | Inputs, surfaces and side effects the resource may touch. |",
    "| Completion | Observable condition that releases the resource; includes required cleanup or handoff. |",
    "| Resource | Scope | Completion / stop condition |",
    "| Resource | Owner | Trigger | Preconditions | Method | Output | Do not use | Fallback | Health check |",
    "| Skill | Owner | Trigger | Preconditions | Method | Output | Do not use | Fallback | Health check |",
    "| Server | Owner/state | Trigger | Preconditions | Method | Output | Do not use | Fallback | Health check |",
    "| `android-cli` |",
    "| `tsuyomi-android-review` |",
    "| `frontend-design` |",
    "| `screenshot` |",
    "| `smell-check` |",
    "| `find-skills` |",
    "| `to-spec` |",
    "| `context7` |",
    "| `uiautomator2` |",
    "| `node_repl` |",
    "| `websearch` |",
    "| `grep_app` |",
)
TOOLING_SCOPE_RESOURCES = (
    "`read`",
    "`glob`",
    "`grep`",
    "LSP",
    "`ask`",
    "`todo`",
    "`multi_tool_use.parallel`",
    "`task`",
    "`eval`",
    "`edit`",
    "`write`",
    "`ast_edit`",
    "`bash`",
    "`hub`",
    "`debug`",
    "Mnemopi memory",
    "`web_search`",
    "Browser",
    "`image_gen`",
    "`xd://report_issue`",
    "Gradle Wrapper",
    "Repository policy / REUSE",
    "`android-cli`",
    "`tsuyomi-android-review`",
    "`frontend-design`",
    "`screenshot`",
    "`smell-check`",
    "`find-skills`",
    "`to-spec`",
    "Context7",
    "UIAutomator2",
    "`node_repl`",
    "`websearch`",
    "`grep_app`",
)
TOOLING_NATIVE_RESOURCES = TOOLING_SCOPE_RESOURCES[:22]
TOOLING_SKILL_RESOURCES = TOOLING_SCOPE_RESOURCES[22:29]
TOOLING_MCP_RESOURCES = ("`context7`", "`uiautomator2`", "`node_repl`", "`websearch`", "`grep_app`")
PROJECT_SKILL_REQUIRED_SECTIONS = ("## When to use", "## Do not use", "## Required inputs")
DOCUMENTATION_REQUIRED_SECTIONS = (
    "## Record contract",
    "## Dispatch procedure",
    "## Repository entry and contribution documents",
    "## Android process, design, Phase and verification documents",
    "## Android architecture and ADR documents",
    "## Protocol and extension contract documents",
    "## Lifecycle changes",
)
DOCUMENTATION_REQUIRED_MARKERS = (
    "| Document | Role / purpose | Trigger | Method | Scope / exclusions | Completion / stop | Lifecycle |",
    "| Scope / exclusions | Decisions it owns and nearby decisions it must not own. |",
    "| Completion / stop | Evidence that the required reading or update is sufficient; linked documents are not read automatically after this point. |",
)
ACTIVE_TOOLING_DOCS = (
    Path("WORKSPACE.md"),
    Path("CONTRIBUTING.md"),
    Path("TOOLING.md"),
    Path("DOCUMENTATION.md"),
    Path("tsuyomi-android/docs/process/QUALITY_GATES.md"),
    Path("tsuyomi-android/docs/verification/AVD_MATRIX.md"),
    Path("tsuyomi-android/docs/design/UI_ATLAS.md"),
)
SCOPE_ROOTS = {
    "all": None,
    "android": Path("tsuyomi-android"),
    "protocol": Path("tsuyomi-protocol"),
    "extensions": Path("tsuyomi-extensions"),
}
FORBIDDEN_PARTS = {
    "build",
    "dist",
    "coverage",
    "node_modules",
    ".gradle",
    ".kotlin",
    ".idea",
    ".externalNativeBuild",
    ".cxx",
    ".local",
    ".claude",
    ".ai",
    ".agents",
    ".cursor",
    ".windsurf",
}
FORBIDDEN_NAMES = {
    "local.properties",
    "keystore.properties",
    ".mcp.json",
    "AGENTS.md",
    "AGENTS.local.md",
    "g",
    "id_rsa",
    "id_ed25519",
}
FORBIDDEN_SUFFIXES = {
    ".hprof",
    ".jks",
    ".keystore",
    ".p12",
    ".pem",
    ".key",
    ".apk",
    ".aab",
    ".hxp",
    ".bundle",
    ".log",
    ".trace",
}

RETIRED_ANDROID_PROTOTYPE = Path("tsuyomi-android/prototype/ui-atlas")
RETIRED_ANDROID_PACKAGE = "org.tsuyomi.prototype"


def retired_android_prototype_violations(repo_root: Path = REPO_ROOT) -> list[str]:
    violations: list[str] = []
    retired_root = repo_root / RETIRED_ANDROID_PROTOTYPE
    if retired_root.exists():
        violations.append(f"{RETIRED_ANDROID_PROTOTYPE.as_posix()}: retired prototype must not exist")

    settings_path = repo_root / "tsuyomi-android/settings.gradle.kts"
    if settings_path.is_file() and ":prototype:ui-atlas" in settings_path.read_text(
        encoding="utf-8", errors="ignore"
    ):
        violations.append("tsuyomi-android/settings.gradle.kts: includes retired prototype module")

    android_root = repo_root / "tsuyomi-android"
    if android_root.is_dir():
        for path in android_root.rglob("*"):
            if not path.is_file() or path.suffix not in {".kt", ".java", ".kts", ".xml"}:
                continue
            relative = path.relative_to(android_root)
            if any(part in FORBIDDEN_PARTS or part == "docs" for part in relative.parts):
                continue
            if RETIRED_ANDROID_PACKAGE in path.read_text(encoding="utf-8", errors="ignore"):
                violations.append(
                    f"{path.relative_to(repo_root).as_posix()}: references retired prototype package"
                )
    return violations


def frozen_profile_screenshot_violations(repo_root: Path = REPO_ROOT) -> list[str]:
    policy_path = repo_root / ".agents/skills/tsuyomi-android-review/review-policy.json"
    if not policy_path.is_file():
        return []
    try:
        policy = json.loads(policy_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    eink_frozen = any(
        item.get("profile") == "EINK" and item.get("status") == "FROZEN"
        for item in policy.get("deferredProfiles", [])
    )
    if not eink_frozen:
        return []

    violations: list[str] = []
    android_root = repo_root / "tsuyomi-android"
    if not android_root.is_dir():
        return violations
    for path in android_root.rglob("*.kt"):
        relative = path.relative_to(android_root)
        if "screenshotTest" not in relative.parts or any(
            part in FORBIDDEN_PARTS for part in relative.parts
        ):
            continue
        for block in path.read_text(encoding="utf-8", errors="ignore").split("\n\n"):
            if "@PreviewTest" in block and "eink" in block.lower():
                relative_path = path.relative_to(repo_root).as_posix()
                violations.append(
                    f"{relative_path}: registers routine EINK screenshot evidence while EINK is frozen"
                )
                break
    return violations


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Reject local or sensitive repository artifacts")
    parser.add_argument("--scope", choices=tuple(SCOPE_ROOTS), default="all")
    return parser.parse_args(argv)


def git_visible_paths() -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(REPO_ROOT), "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        check=True,
        capture_output=True,
    )
    return [Path(value.decode("utf-8")) for value in result.stdout.split(b"\0") if value]


def paths_in_scope(paths: Iterable[Path], scope: str) -> list[Path]:
    root = SCOPE_ROOTS[scope]
    if root is None:
        return list(paths)
    return [path for path in paths if path.is_relative_to(root)]


def is_public_hxp_fixture(path: Path) -> bool:
    return path.suffix.lower() == ".hxp" and path.parts[:3] == ("tsuyomi-extensions", "fixtures", "wenku8")


def is_public_project_skill(path: Path) -> bool:
    return path.parts[:2] == (".agents", "skills") and len(path.parts) >= 4


def has_forbidden_part(path: Path) -> bool:
    return any(
        part in FORBIDDEN_PARTS and not (part == ".agents" and is_public_project_skill(path))
        for part in path.parts
    )



def violates_policy(path: Path) -> bool:
    return (
        has_forbidden_part(path)
        or path.name in FORBIDDEN_NAMES
        or (path.suffix.lower() in FORBIDDEN_SUFFIXES and not is_public_hxp_fixture(path))
        or path.name.endswith(".prompt.md")
        or ".transcript." in path.name
        or (path.name.startswith(".env") and path.name != ".env.example")
    )

def skill_frontmatter_name(text: str) -> str | None:
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return None
    for line in lines[1:]:
        if line.strip() == "---":
            return None
        if line.startswith("name:"):
            return line.removeprefix("name:").strip().strip('"\'') or None
    return None


def documentation_inventory_paths(repo_root: Path) -> list[Path]:
    paths: set[Path] = set()
    for path in repo_root.glob("*.md"):
        if path.is_file():
            paths.add(path.relative_to(repo_root))
    for component in ("tsuyomi-android", "tsuyomi-protocol", "tsuyomi-extensions"):
        root = repo_root / component
        if root.is_dir():
            paths.update(path.relative_to(repo_root) for path in root.glob("*.md") if path.is_file())
    for relative_root in (
        Path(".github"),
        Path("tsuyomi-android/docs"),
        Path("tsuyomi-protocol/docs"),
        Path("tsuyomi-extensions/docs"),
    ):
        root = repo_root / relative_root
        if root.is_dir():
            paths.update(path.relative_to(repo_root) for path in root.rglob("*.md") if path.is_file())
    skills_root = repo_root / PROJECT_SKILLS_ROOT
    if skills_root.is_dir():
        paths.update(path.relative_to(repo_root) for path in skills_root.glob("*/SKILL.md") if path.is_file())
    vendored_docs = repo_root / "tsuyomi-android/source/quickjs-runtime/src/main/cpp/quickjs-ng"
    if vendored_docs.is_dir():
        paths.update(path.relative_to(repo_root) for path in vendored_docs.glob("*.md") if path.is_file())
    return sorted(paths, key=lambda path: path.as_posix())


def documentation_governance_violations(repo_root: Path = REPO_ROOT) -> list[str]:
    registry_path = repo_root / DOCUMENTATION_PATH
    try:
        registry_text = registry_path.read_text(encoding="utf-8")
    except OSError as error:
        return [f"{DOCUMENTATION_PATH.as_posix()}: cannot read documentation registry: {error}"]

    violations: list[str] = []
    for section in DOCUMENTATION_REQUIRED_SECTIONS:
        if section not in registry_text:
            violations.append(f"{DOCUMENTATION_PATH.as_posix()}: missing required section {section}")
    for marker in DOCUMENTATION_REQUIRED_MARKERS:
        if marker not in registry_text:
            violations.append(f"{DOCUMENTATION_PATH.as_posix()}: missing required registry marker {marker}")

    document_rows: dict[str, list[str]] = {}
    for line in registry_text.splitlines():
        if not line.startswith("| `"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        match = re.fullmatch(r"`([^`]+)`", cells[0]) if cells else None
        if match is None:
            violations.append(f"{DOCUMENTATION_PATH.as_posix()}: malformed document path cell {line}")
            continue
        relative_text = match.group(1)
        document_rows.setdefault(relative_text, []).append(line)
        if len(cells) != 7 or any(not cell for cell in cells):
            violations.append(
                f"{DOCUMENTATION_PATH.as_posix()}: {relative_text} must define all seven responsibility fields"
            )
        if not (repo_root / relative_text).is_file():
            violations.append(f"{DOCUMENTATION_PATH.as_posix()}: registered document does not exist: {relative_text}")

    for relative in documentation_inventory_paths(repo_root):
        rows = document_rows.get(relative.as_posix(), [])
        if not rows:
            violations.append(f"{relative.as_posix()}: document is not registered in {DOCUMENTATION_PATH.as_posix()}")
        elif len(rows) > 1:
            violations.append(f"{relative.as_posix()}: document has {len(rows)} responsibility rows")
    return violations

def tooling_governance_violations(repo_root: Path = REPO_ROOT) -> list[str]:
    violations: list[str] = []
    tooling_path = repo_root / TOOLING_PATH
    try:
        tooling_text = tooling_path.read_text(encoding="utf-8")
    except OSError as error:
        return [f"{TOOLING_PATH.as_posix()}: cannot read canonical tooling registry: {error}"]

    for section in TOOLING_REQUIRED_SECTIONS:
        if section not in tooling_text:
            violations.append(f"{TOOLING_PATH.as_posix()}: missing required section {section}")
    for marker in TOOLING_REQUIRED_MARKERS:
        if marker not in tooling_text:
            violations.append(f"{TOOLING_PATH.as_posix()}: missing required registry marker {marker}")

    dynamic_policy_values: list[tuple[str, str]] = []
    skills_root = repo_root / PROJECT_SKILLS_ROOT
    if skills_root.is_dir():
        for skill_dir in sorted(path for path in skills_root.iterdir() if path.is_dir()):
            skill_path = skill_dir / "SKILL.md"
            relative_skill = skill_path.relative_to(repo_root).as_posix()
            if not skill_path.is_file():
                violations.append(f"{relative_skill}: project Skill is missing SKILL.md")
                continue
            skill_text = skill_path.read_text(encoding="utf-8")
            declared_name = skill_frontmatter_name(skill_text)
            if declared_name != skill_dir.name:
                violations.append(
                    f"{relative_skill}: frontmatter name {declared_name!r} must match directory {skill_dir.name!r}"
                )
            registry_path = (PROJECT_SKILLS_ROOT / skill_dir.name).as_posix()
            if registry_path not in tooling_text:
                violations.append(f"{relative_skill}: project Skill is not registered in {TOOLING_PATH.as_posix()}")
            for section in PROJECT_SKILL_REQUIRED_SECTIONS:
                if section not in skill_text:
                    violations.append(f"{relative_skill}: missing required trigger section {section}")

            policy_path = skill_dir / "review-policy.json"
            if policy_path.is_file():
                relative_policy = policy_path.relative_to(repo_root).as_posix()
                try:
                    policy = json.loads(policy_path.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError) as error:
                    violations.append(f"{relative_policy}: invalid policy JSON: {error}")
                    continue
                dynamic_values = (
                    policy.get("mode"),
                    policy.get("phase4Milestone"),
                    policy.get("nodeExecution", {}).get("activeStage"),
                )
                dynamic_policy_values.extend(
                    (relative_policy, value)
                    for value in dynamic_values
                    if isinstance(value, str) and value
                )
                for value in dynamic_values:
                    if isinstance(value, str) and value and value in skill_text:
                        violations.append(
                            f"{relative_skill}: copies dynamic policy value {value!r} from {relative_policy}"
                        )
                for key in ("activeNodeCount", "activeSurfaceCount", "activeRouteStateObligationCount"):
                    if key in skill_text:
                        violations.append(f"{relative_skill}: copies dynamic policy field {key}")
    scope_section = tooling_text.split("## Scope and completion", maxsplit=1)[-1].split("\n## ", maxsplit=1)[0]
    for resource in TOOLING_SCOPE_RESOURCES:
        row_marker = f"| {resource} |"
        rows = [line for line in scope_section.splitlines() if line.startswith(row_marker)]
        if len(rows) != 1:
            violations.append(
                f"{TOOLING_PATH.as_posix()}: {resource} must have exactly one scope/completion row"
            )
            continue
        cells = [cell.strip() for cell in rows[0].strip().strip("|").split("|")]
        if len(cells) != 3 or any(not cell for cell in cells):
            violations.append(f"{TOOLING_PATH.as_posix()}: {resource} scope/completion row is incomplete")

    record_specs = (
        ("## Native and repository tools", TOOLING_NATIVE_RESOURCES, 9, "native tool"),
        ("## Skills", TOOLING_SKILL_RESOURCES, 9, "Skill"),
        ("## MCP ownership", TOOLING_MCP_RESOURCES, 9, "MCP"),
    )
    for heading, resources, field_count, label in record_specs:
        if heading not in tooling_text:
            continue
        section = tooling_text.split(heading, maxsplit=1)[1].split("\n## ", maxsplit=1)[0]
        for resource in resources:
            row_marker = f"| {resource} |"
            rows = [line for line in section.splitlines() if line.startswith(row_marker)]
            if len(rows) != 1:
                violations.append(
                    f"{TOOLING_PATH.as_posix()}: {resource} must have exactly one {label} record"
                )
                continue
            cells = [cell.strip() for cell in rows[0].strip().strip("|").split("|")]
            if len(cells) != field_count or any(not cell for cell in cells):
                violations.append(
                    f"{TOOLING_PATH.as_posix()}: {resource} {label} record is incomplete"
                )

    obsolete_skill_path = (Path("tools") / "skills" / "tsuyomi-android-review").as_posix()
    governed_docs = (*ACTIVE_TOOLING_DOCS, PROJECT_SKILLS_ROOT / "tsuyomi-android-review" / "SKILL.md")
    for relative in governed_docs:
        path = repo_root / relative
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if obsolete_skill_path in text:
            violations.append(f"{relative.as_posix()}: references obsolete Skill path {obsolete_skill_path}")
        for relative_policy, value in dynamic_policy_values:
            if value in text:
                violations.append(
                    f"{relative.as_posix()}: copies dynamic policy value {value!r} from {relative_policy}"
                )
    return violations


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    paths = paths_in_scope(git_visible_paths(), args.scope)
    artifact_violations = sorted(str(path) for path in paths if violates_policy(path))
    tooling_violations = tooling_governance_violations()
    documentation_violations = documentation_governance_violations()
    prototype_violations = retired_android_prototype_violations()
    frozen_screenshot_violations = frozen_profile_screenshot_violations()
    if artifact_violations or tooling_violations or documentation_violations or prototype_violations or frozen_screenshot_violations:
        if artifact_violations:
            print(f"Forbidden repository artifacts in scope {args.scope}:", file=sys.stderr)
            for violation in artifact_violations:
                print(f"- {violation}", file=sys.stderr)
        if tooling_violations:
            print("Tooling governance violations:", file=sys.stderr)
            for violation in tooling_violations:
                print(f"- {violation}", file=sys.stderr)
        if documentation_violations:
            print("Documentation governance violations:", file=sys.stderr)
            for violation in documentation_violations:
                print(f"- {violation}", file=sys.stderr)
        if prototype_violations:
            print("Retired Android prototype violations:", file=sys.stderr)
            for violation in prototype_violations:
                print(f"- {violation}", file=sys.stderr)
        if frozen_screenshot_violations:
            print("Frozen Android profile screenshot violations:", file=sys.stderr)
            for violation in frozen_screenshot_violations:
                print(f"- {violation}", file=sys.stderr)
        return 1
    print(f"Repository artifact policy passed for {len(paths)} candidate files in scope {args.scope}.")
    print("Tooling governance policy passed.")
    print("Documentation governance policy passed.")
    print("Retired Android prototype policy passed.")
    print("Frozen Android profile screenshot policy passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
