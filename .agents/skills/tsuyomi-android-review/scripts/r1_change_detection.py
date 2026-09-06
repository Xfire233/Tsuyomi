#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from r1_report import REPORT_SCHEMA, ReportBuildContext, build_report

SUPPORTED_BASELINE_SCHEMAS = {
    REPORT_SCHEMA,
    "tsuyomi-r1-change-report-v1",
    "tsuyomi-r1-baseline-v1",
}
SKILL_ROOT = Path(".agents/skills/tsuyomi-android-review")
CATALOG_PATH = SKILL_ROOT / "review-node-catalog.json"
CATALOG_SCHEMA = "tsuyomi-review-node-catalog-v1"
POLICY_PATH = SKILL_ROOT / "review-policy.json"
POLICY_SCHEMA = "tsuyomi-android-review-policy-v1"
WORKFLOW_FILES = {
    Path(".github/workflows/repository-quality.yml"),
    Path("AGENTS.md"),
    Path(".github/workflows/android-quality.yml"),
    Path("CONTRIBUTING.md"),
    Path("WORKSPACE.md"),
    Path("DOCUMENTATION.md"),
    Path("TOOLING.md"),
}
EXCLUDED_DIRECTORIES = {
    "build",
    ".gradle",
    ".idea",
    ".kotlin",
    ".cxx",
    ".externalNativeBuild",
    "node_modules",
    "__pycache__",
}


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def load_review_policy(repo_root: Path) -> tuple[dict, str]:
    path = repo_root / POLICY_PATH
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema") != POLICY_SCHEMA:
        raise SystemExit("Unsupported Android review policy schema")
    active = data.get("activeProfiles")
    deferred = data.get("deferredProfiles")
    if not isinstance(active, list) or not active or not all(isinstance(item, str) for item in active):
        raise SystemExit("Review policy must declare at least one active profile")
    if not isinstance(deferred, list):
        raise SystemExit("Review policy deferredProfiles must be a list")
    deferred_names = {
        item.get("profile") for item in deferred if isinstance(item, dict) and isinstance(item.get("profile"), str)
    }
    if set(active) & deferred_names:
        raise SystemExit("Review policy cannot activate and defer the same profile")
    node_execution = data.get("nodeExecution")
    if not isinstance(node_execution, dict):
        raise SystemExit("Review policy must declare nodeExecution")
    active_prefixes = node_execution.get("activeNodePrefixes")
    deferred_stages = node_execution.get("deferredStages")
    if not isinstance(active_prefixes, list) or not active_prefixes or not all(
        isinstance(item, str) and len(item) == 1 for item in active_prefixes
    ):
        raise SystemExit("Review policy activeNodePrefixes is invalid")
    if not isinstance(deferred_stages, list):
        raise SystemExit("Review policy deferredStages is invalid")
    deferred_prefixes = {
        prefix
        for stage in deferred_stages
        if isinstance(stage, dict)
        for prefix in stage.get("nodePrefixes", [])
        if isinstance(prefix, str)
    }
    if set(active_prefixes) & deferred_prefixes:
        raise SystemExit("Review policy cannot activate and defer the same node prefix")
    actual_online = node_execution.get("actualOnlineRequirements")
    if not isinstance(actual_online, dict):
        raise SystemExit("Review policy must declare actualOnlineRequirements")
    actual_online_prefixes = actual_online.get("nodePrefixes")
    if not isinstance(actual_online_prefixes, list) or not actual_online_prefixes or not all(
        isinstance(item, str) and len(item) == 1 for item in actual_online_prefixes
    ):
        raise SystemExit("Review policy actual-online nodePrefixes is invalid")
    if not set(actual_online_prefixes) <= set(active_prefixes):
        raise SystemExit("Actual-online node prefixes must be active production prefixes")
    return data, sha256_file(path)


def find_repo_root(start: Path) -> Path:
    current = start.resolve()
    for candidate in (current, *current.parents):
        if (candidate / "tsuyomi-android").is_dir() and (candidate / "WORKSPACE.md").is_file():
            return candidate
    raise SystemExit("Could not locate the Tsuyomi monorepo root")


def is_scoped_android_file(path: Path, android_root: Path) -> bool:
    relative = path.relative_to(android_root)
    if any(part in EXCLUDED_DIRECTORIES or part == "screenshotTestDebug" for part in relative.parts):
        return False
    relative_text = relative.as_posix()
    if path.name == "local.properties" or path.suffix == ".pyc":
        return False
    if path.name.startswith("tsuyomi-atlas-review-bundle"):
        return False
    return path.is_file()


def collect_files(repo_root: Path) -> dict[str, str]:
    android_root = repo_root / "tsuyomi-android"
    files: dict[str, str] = {}
    for path in sorted(android_root.rglob("*")):
        if is_scoped_android_file(path, android_root):
            files[path.relative_to(repo_root).as_posix()] = sha256_file(path)

    for relative in sorted(WORKFLOW_FILES):
        path = repo_root / relative
        if path.is_file():
            files[relative.as_posix()] = sha256_file(path)

    skill_root = repo_root / SKILL_ROOT
    if skill_root.is_dir():
        for path in sorted(skill_root.rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts and path.suffix != ".pyc":
                files[path.relative_to(repo_root).as_posix()] = sha256_file(path)
    return files


def is_review_build_identity_path(path: str) -> bool:
    if path in {
        "tsuyomi-android/docs/design/UI_CONSTITUTION.md",
        CATALOG_PATH.as_posix(),
    }:
        return True
    if not path.startswith("tsuyomi-android/"):
        return False
    if "/docs/" in path:
        return False
    if any(marker in path for marker in ("/src/test/", "/src/androidTest/", "/src/screenshotTest/")):
        return False
    return path.endswith((".kt", ".kts", ".java", ".xml", ".toml", ".properties", ".lockfile"))


def compute_review_build_id(current_files: dict[str, str]) -> str:
    digest = hashlib.sha256()
    for path, content_hash in sorted(current_files.items()):
        if not is_review_build_identity_path(path):
            continue
        digest.update(path.encode())
        digest.update(b"\0")
        digest.update(content_hash.encode())
        digest.update(b"\0")
    return digest.hexdigest()


def parse_catalog(repo_root: Path) -> tuple[int, list[str]]:
    catalog_path = repo_root / CATALOG_PATH
    data = json.loads(catalog_path.read_text(encoding="utf-8"))
    if data.get("schema") != CATALOG_SCHEMA or not isinstance(data.get("version"), int):
        raise SystemExit("Unsupported Review Graph catalog schema")
    nodes = data.get("nodes")
    if not isinstance(nodes, list):
        raise SystemExit("Review Graph catalog must contain a node list")
    node_ids = [node.get("id") for node in nodes if isinstance(node, dict)]
    if len(node_ids) != 28 or len(set(node_ids)) != 28 or not all(
        isinstance(node_id, str) and re.fullmatch(r"[LBMSX]\d{2}", node_id)
        for node_id in node_ids
    ):
        raise SystemExit("Review Graph catalog must contain 28 unique valid node IDs")
    return data["version"], sorted(node_ids)


def nodes_with_prefix(node_ids: Iterable[str], *prefixes: str) -> set[str]:
    return {node_id for node_id in node_ids if node_id.startswith(prefixes)}


Classification = tuple[str, set[str], list[str]]


@dataclass(frozen=True)
class ReviewNodeGroups:
    all: set[str]
    surface: set[str]
    library: set[str]
    book_reader: set[str]
    source: set[str]
    more: set[str]


def review_node_groups(node_ids: list[str]) -> ReviewNodeGroups:
    book_reader = nodes_with_prefix(node_ids, "B")
    return ReviewNodeGroups(
        all=set(node_ids),
        surface=nodes_with_prefix(node_ids, "L", "B", "S", "M"),
        library=nodes_with_prefix(node_ids, "L"),
        book_reader=book_reader,
        source=nodes_with_prefix(node_ids, "S"),
        more=nodes_with_prefix(node_ids, "M"),
    )


def available_nodes(groups: ReviewNodeGroups, *node_ids: str) -> set[str]:
    return set(node_ids) & groups.all


def cross_cutting_nodes(normalized: str, groups: ReviewNodeGroups) -> set[str]:
    """Select cross-cutting review nodes only when the path names their capability."""
    lowered = normalized.lower()
    file_name = lowered.rsplit("/", 1)[-1]
    nodes: set[str] = set()
    if any(token in file_name for token in ("navigation", "route", "backstack", "mainactivity")):
        nodes |= available_nodes(groups, "X01")
    if any(token in file_name for token in ("dialog", "menu", "input", "semantic", "accessibility", "interaction")):
        nodes |= available_nodes(groups, "X02")
    if any(token in file_name for token in ("theme", "display", "eink", "color", "motion")):
        nodes |= available_nodes(groups, "X03", "X04")
    if (
        "/src/main/res/" in lowered
        or any(token in file_name for token in (
            "screen", "surface", "presentation", "component", "modules", "scaffold", "layout",
        ))
    ):
        nodes |= available_nodes(groups, "X04")
    if any(token in file_name for token in (
        "remote", "source", "network", "coordinator", "session", "gateway", "webview", "retry", "mutation",
    )):
        nodes |= available_nodes(groups, "X05")
    return nodes


def classify_review_contract(normalized: str, groups: ReviewNodeGroups) -> Classification | None:
    if normalized.startswith(SKILL_ROOT.as_posix() + "/"):
        return "workflow", available_nodes(groups, "X06"), ["project review skill changed"]
    if normalized in {path.as_posix() for path in WORKFLOW_FILES}:
        return "workflow", available_nodes(groups, "X06"), ["repository Android workflow changed"]
    if normalized.endswith((
        "docs/design/UI_ATLAS.md",
        "docs/design/DESIGN_DIRECTION_HANDOFF.md",
        "docs/design/DESIGN_REFERENCE_REVIEW.md",
    )):
        return "workflow", available_nodes(groups, "X06"), ["review procedure or historical design record changed"]
    if normalized.endswith(("docs/design/UI_CONSTITUTION.md", "docs/phases/PHASE_4.md")):
        return "contract", groups.all, ["binding product or phase contract changed"]
    if normalized == CATALOG_PATH.as_posix():
        return "contract", groups.all, ["review scope authority changed"]
    return None




def production_domain_nodes(normalized: str, groups: ReviewNodeGroups) -> tuple[set[str], str] | None:
    if not normalized.endswith((".kt", ".java", ".xml")):
        return None

    name = normalized.rsplit("/", 1)[-1]
    l_nodes = groups.library
    b_nodes = groups.book_reader
    s_nodes = groups.source
    m_nodes = groups.more

    if "RemoteLibrary" in name or "RemoteMirror" in name:
        return available_nodes(groups, "L08", "S03", "B01"), "website mirror implementation changed"
    if "/feature/book/" in normalized:
        return available_nodes(groups, "B01"), "production Book Detail feature changed"
    if "/feature/search/" in normalized:
        return available_nodes(groups, "S02"), "production Search feature changed"
    if "/feature/library/" in normalized:
        return l_nodes | available_nodes(groups, "B01"), "production Library feature changed"
    if "/feature/browse/" in normalized:
        return s_nodes | available_nodes(groups, "B01"), "production Browse/source feature changed"
    if "/feature/settings/" in normalized:
        return m_nodes | available_nodes(groups, "B03"), "production More/settings feature changed"
    if "/feature/backup/" in normalized:
        return available_nodes(groups, "M04", "M05", "X05"), "production backup/transfer feature changed"
    if "/feature/extensions/" in normalized:
        return available_nodes(groups, "S01", "S04", "M07"), "production extension-management feature changed"
    if "/feature/reader/" in normalized or "/reader/" in normalized:
        return b_nodes | available_nodes(groups, "M03"), "Reader implementation changed"
    if "/core/ui/" in normalized:
        return groups.surface | available_nodes(groups, "X02", "X04"), "shared production UI changed"
    if "/core/display/" in normalized:
        return available_nodes(groups, "M02", "M03", "B02", "B03", "X03", "X04"), "display/profile behavior changed"
    if "/core/database/" in normalized:
        return l_nodes | available_nodes(groups, "B01", "M04", "M05"), "library persistence behavior changed"
    if "/core/preferences/" in normalized:
        return available_nodes(groups, "L01", "L02", "L05", "L06", "M02", "M03"), "persisted UI preference behavior changed"
    if "/core/files/" in normalized or "/shared/backup/" in normalized:
        return available_nodes(groups, "M04", "M05", "X05"), "data transfer/file behavior changed"
    if "/core/media/" in normalized:
        return l_nodes | available_nodes(groups, "B01", "S01", "S02", "S03"), "cover/media behavior changed"
    if "/shared/locator/" in normalized:
        return b_nodes | available_nodes(groups, "X01"), "reader locator semantics changed"
    if "/shared/smart-shelf/" in normalized:
        return available_nodes(groups, "L01", "L02", "L04", "L05", "L06", "X01"), "shelf membership/rule behavior changed"
    if "/shared/source-contract/" in normalized or "/source/" in normalized or "/core/network/" in normalized:
        return s_nodes | available_nodes(groups, "B01", "L08", "X05"), "source/network contract or runtime changed"
    if "/core/security/" in normalized or "/core/webview/" in normalized:
        return available_nodes(groups, "S04", "M04", "M05", "X02", "X05"), "security or controlled WebView boundary changed"
    if "/shared/model/" in normalized:
        return groups.surface, "shared presentation model changed"
    if "/app/" in normalized:
        if name.startswith("Library"):
            return l_nodes | available_nodes(groups, "B01"), "application Library owner changed"
        if name.startswith("Source") or name.startswith("NormalizedSource") or name.startswith("VerifiedPage"):
            return s_nodes | available_nodes(groups, "B01", "L08"), "application source owner changed"
        return groups.surface, "application host or navigation changed"
    return None


def classify_test_source(normalized: str, groups: ReviewNodeGroups) -> Classification | None:
    if not any(marker in normalized for marker in ("/src/test/", "/src/androidTest/", "/src/screenshotTest/")):
        return None
    domain = production_domain_nodes(normalized, groups)
    if domain is None:
        return "evidence", groups.all, ["unclassified automated evidence source changed"]
    nodes, reason = domain
    return "evidence", nodes | cross_cutting_nodes(normalized, groups), [f"automated evidence for {reason}"]


def classify_production(normalized: str, groups: ReviewNodeGroups) -> Classification | None:
    domain = production_domain_nodes(normalized, groups)
    if domain is None:
        return None
    nodes, reason = domain
    return "runtime", nodes | cross_cutting_nodes(normalized, groups), [reason]


def classify_repository_input(normalized: str, groups: ReviewNodeGroups) -> Classification:
    if normalized.endswith((".gradle.kts", ".toml", ".properties", ".lockfile")) or "/gradle/" in normalized:
        return "build", set(), ["build or dependency input changed"]
    if "/docs/" in normalized or normalized.endswith(".md"):
        return "workflow", available_nodes(groups, "X06"), ["non-binding process/documentation changed"]
    if normalized.endswith((".kt", ".java", ".xml")) and normalized.startswith("tsuyomi-android/"):
        return "runtime", groups.all, ["unknown Android source change; conservative full scope"]
    return "other", set(), ["non-UI repository input changed"]


def classify_change(path: str, node_ids: list[str]) -> Classification:
    normalized = path.replace("\\", "/")
    groups = review_node_groups(node_ids)
    for classifier in (classify_review_contract, classify_test_source, classify_production):
        classification = classifier(normalized, groups)
        if classification is not None:
            return classification
    return classify_repository_input(normalized, groups)


def baseline_files(data: dict) -> dict[str, str]:
    files = data.get("files")
    if not isinstance(files, dict):
        raise SystemExit("Baseline must contain a top-level 'files' hash map")
    return {str(path): str(value) for path, value in files.items()}


def baseline_build_id(data: dict) -> str | None:
    source = data.get("source", {})
    current = data.get("current", {})
    for candidate in (
        current.get("buildId"),
        source.get("currentComputedBuildId"),
        source.get("reviewedBuildId"),
    ):
        if isinstance(candidate, str):
            return candidate
    return None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Detect Tsuyomi Android Review Graph impact")
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="monorepo root or a child path")
    parser.add_argument("--baseline", type=Path, help="previous UI-R1 report or baseline JSON")
    parser.add_argument(
        "--base-ref",
        help="Git ref used when no file-hash baseline is supplied; defaults to origin/main then main",
    )
    parser.add_argument("--output", type=Path, required=True, help="output UI-R1 report JSON")
    parser.add_argument(
        "--force-full-review",
        action="store_true",
        help="select every Review Graph node and require one exact-source build/device pass",
    )
    return parser.parse_args()


@dataclass(frozen=True)
class BaselineState:
    data: dict | None
    files: dict[str, str]
    build_id: str | None
    label: str | None


@dataclass
class ChangeAnalysis:
    changes: list[dict]
    affected_reasons: dict[str, list[str]]
    changed_kotlin_files: list[str]


def is_review_scope_path(path: str) -> bool:
    normalized = path.replace("\\", "/")
    if normalized in {item.as_posix() for item in WORKFLOW_FILES}:
        return True
    if normalized.startswith(SKILL_ROOT.as_posix() + "/"):
        return "__pycache__" not in normalized and not normalized.endswith(".pyc")
    prefix = "tsuyomi-android/"
    if not normalized.startswith(prefix):
        return False
    relative = normalized[len(prefix):]
    parts = relative.split("/")
    if any(part in EXCLUDED_DIRECTORIES or part == "screenshotTestDebug" for part in parts):
        return False
    name = parts[-1]
    if name == "local.properties" or name.endswith(".pyc"):
        return False
    return not name.startswith("tsuyomi-atlas-review-bundle")


def run_git(repo_root: Path, *args: str) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        ["git", *args],
        cwd=repo_root,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def resolve_merge_base(repo_root: Path, requested_ref: str | None) -> str | None:
    candidates = [requested_ref] if requested_ref is not None else ["origin/main", "main"]
    for candidate in candidates:
        result = run_git(repo_root, "merge-base", "HEAD", candidate)
        if result.returncode == 0:
            value = result.stdout.decode().strip()
            if value:
                return value
    if requested_ref is not None:
        raise SystemExit(f"Could not resolve Git merge-base for {requested_ref}")
    return None


def load_git_baseline(
    repo_root: Path,
    current_files: dict[str, str],
    requested_ref: str | None,
) -> BaselineState | None:
    merge_base = resolve_merge_base(repo_root, requested_ref)
    if merge_base is None:
        return None

    changed = run_git(repo_root, "diff", "--no-renames", "--name-only", merge_base, "--")
    untracked = run_git(repo_root, "ls-files", "--others", "--exclude-standard")
    if changed.returncode != 0 or untracked.returncode != 0:
        if requested_ref is not None:
            raise SystemExit("Could not derive Review Graph scope from Git")
        return None

    paths = {
        line.strip()
        for output in (changed.stdout, untracked.stdout)
        for line in output.decode(errors="replace").splitlines()
        if line.strip() and is_review_scope_path(line.strip())
    }
    files = dict(current_files)
    for path in paths:
        previous = run_git(repo_root, "show", f"{merge_base}:{path}")
        if previous.returncode == 0:
            files[path] = sha256_bytes(previous.stdout)
        else:
            files.pop(path, None)

    data = {
        "schema": "tsuyomi-r1-baseline-v1",
        "source": {"gitMergeBase": merge_base},
    }
    return BaselineState(data, files, None, f"git:{merge_base}")


def load_baseline(
    args: argparse.Namespace,
    repo_root: Path,
    current_files: dict[str, str],
) -> BaselineState:
    if args.baseline is None:
        git_baseline = load_git_baseline(repo_root, current_files, args.base_ref)
        return git_baseline or BaselineState(None, {}, None, None)
    baseline_path = args.baseline if args.baseline.is_absolute() else repo_root / args.baseline
    data = json.loads(baseline_path.read_text(encoding="utf-8"))
    if data.get("schema") not in SUPPORTED_BASELINE_SCHEMAS:
        raise SystemExit("Unsupported UI-R1 baseline schema")
    return BaselineState(data, baseline_files(data), baseline_build_id(data), args.baseline.as_posix())


def detect_changes(
    previous_files: dict[str, str],
    current_files: dict[str, str],
    node_ids: list[str],
) -> ChangeAnalysis:
    analysis = ChangeAnalysis([], {}, [])
    for path in sorted(set(previous_files) | set(current_files)):
        old_hash = previous_files.get(path)
        new_hash = current_files.get(path)
        if old_hash == new_hash:
            continue
        status = "added" if old_hash is None else "removed" if new_hash is None else "modified"
        change_class, nodes, reasons = classify_change(path, node_ids)
        for node_id in sorted(nodes):
            analysis.affected_reasons.setdefault(node_id, []).extend(f"{path}: {reason}" for reason in reasons)
        if path.endswith((".kt", ".kts")):
            analysis.changed_kotlin_files.append(path)
        analysis.changes.append({
            "path": path,
            "status": status,
            "oldSha256": old_hash,
            "newSha256": new_hash,
            "class": change_class,
            "affectedNodes": sorted(nodes),
            "reasons": reasons,
        })
    return analysis


def add_synthetic_changes(
    analysis: ChangeAnalysis,
    baseline_available: bool,
    force_full_review: bool,
    node_ids: list[str],
) -> None:
    if not baseline_available:
        analysis.changes.append({
            "path": "<no-baseline>",
            "status": "unknown",
            "oldSha256": None,
            "newSha256": None,
            "class": "unknown",
            "affectedNodes": node_ids,
            "reasons": ["no file-hash baseline; conservative cold-start scope"],
        })
        for node_id in node_ids:
            analysis.affected_reasons.setdefault(node_id, []).append("no file-hash baseline")
    if force_full_review:
        analysis.changes.append({
            "path": "<forced-full-review>",
            "status": "requested",
            "oldSha256": None,
            "newSha256": None,
            "class": "review-request",
            "affectedNodes": node_ids,
            "reasons": ["operator requested a complete AI review"],
        })
        for node_id in node_ids:
            analysis.affected_reasons.setdefault(node_id, []).append("operator requested a complete AI review")




def write_report(repo_root: Path, output: Path, report: dict) -> Path:
    output_path = output if output.is_absolute() else repo_root / output
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return output_path


def print_summary(repo_root: Path, output_path: Path, report: dict) -> None:
    summary = report["summary"]
    policy = report["reviewPolicy"]
    print(json.dumps({
        "output": output_path.relative_to(repo_root).as_posix(),
        "scope": summary["scope"],
        "changedFiles": summary["changedFiles"],
        "affectedNodes": summary["affectedNodes"],
        "buildId": report["current"]["buildId"],
        "reviewPolicyMode": policy["mode"],
        "activeProfiles": policy["activeProfiles"],
        "deferredProfiles": policy["deferredProfiles"],
        "requiresGradleBuild": summary["requiresGradleBuild"],
        "currentStageNodes": summary["currentStageNodes"],
        "deferredNodes": summary["deferredNodes"],
        "requiresDevicePass": summary["requiresDevicePass"],
    }, ensure_ascii=False))


def main() -> int:
    args = parse_args()
    repo_root = find_repo_root(args.root)
    catalog_version, node_ids = parse_catalog(repo_root)
    review_policy, review_policy_hash = load_review_policy(repo_root)
    current_files = collect_files(repo_root)
    current_build_id = compute_review_build_id(current_files)
    baseline = load_baseline(args, repo_root, current_files)
    analysis = detect_changes(baseline.files, current_files, node_ids)
    add_synthetic_changes(analysis, baseline.data is not None, args.force_full_review, node_ids)
    report = build_report(
        ReportBuildContext(
            baseline_path=baseline.label,
            baseline_build_id=baseline.build_id,
            baseline_available=baseline.data is not None,
            force_full_review=args.force_full_review,
            changes=analysis.changes,
            affected_reasons=analysis.affected_reasons,
            changed_kotlin_files=analysis.changed_kotlin_files,
            node_ids=node_ids,
            catalog_version=catalog_version,
            current_files=current_files,
            current_build_id=current_build_id,
            review_policy=review_policy,
            review_policy_path=POLICY_PATH.as_posix(),
            review_policy_hash=review_policy_hash,
        ),
    )
    output_path = write_report(repo_root, args.output, report)
    print_summary(repo_root, output_path, report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
