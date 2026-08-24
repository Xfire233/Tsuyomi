#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import argparse
import hashlib
import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

REPORT_SCHEMA = "tsuyomi-r1-change-report-v2"
SUPPORTED_BASELINE_SCHEMAS = {
    REPORT_SCHEMA,
    "tsuyomi-r1-change-report-v1",
    "tsuyomi-r1-baseline-v1",
}
SKILL_ROOT = Path("tools/skills/tsuyomi-android-review")
POLICY_PATH = SKILL_ROOT / "review-policy.json"
POLICY_SCHEMA = "tsuyomi-android-review-policy-v1"
WORKFLOW_FILES = {
    Path(".github/workflows/android-quality.yml"),
    Path("CONTRIBUTING.md"),
    Path("WORKSPACE.md"),
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


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


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
    if relative.parts and relative.parts[0].startswith("buildinteractive-prototype"):
        return False
    if relative_text.startswith("prototype/ui-atlas/tools/"):
        return False
    if path.name in {"local.properties", "render-browser-atlas.bat"} or path.suffix == ".pyc":
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


def parse_build_versions(build_file: Path) -> tuple[int, int]:
    text = build_file.read_text(encoding="utf-8")
    data_match = re.search(r"prototypeDataSchemaVersion\s*=\s*(\d+)", text)
    review_match = re.search(r"prototypeReviewSchemaVersion\s*=\s*(\d+)", text)
    if data_match is None or review_match is None:
        raise SystemExit("Could not parse prototype schema versions")
    return int(data_match.group(1)), int(review_match.group(1))


def compute_prototype_build_id(repo_root: Path) -> str:
    android_root = repo_root / "tsuyomi-android"
    prototype_root = android_root / "prototype/ui-atlas"
    data_version, review_version = parse_build_versions(prototype_root / "build.gradle.kts")
    digest = hashlib.sha256()
    paths = sorted(
        (path for path in prototype_root.rglob("*") if is_scoped_android_file(path, android_root)),
        key=lambda path: path.relative_to(prototype_root).as_posix(),
    )
    for path in paths:
        relative = path.relative_to(prototype_root).as_posix()
        if relative.startswith("build/") or relative.startswith(".gradle/"):
            continue
        digest.update(relative.encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    digest.update((android_root / "docs/design/UI_CONSTITUTION.md").read_bytes())
    digest.update(str(data_version).encode())
    digest.update(str(review_version).encode())
    return digest.hexdigest()


def parse_catalog(repo_root: Path) -> tuple[int, list[str]]:
    catalog_path = repo_root / (
        "tsuyomi-android/prototype/ui-atlas/src/main/kotlin/"
        "org/tsuyomi/prototype/uiatlas/review/ReviewNodeCatalog.kt"
    )
    text = catalog_path.read_text(encoding="utf-8")
    version_match = re.search(r"const val VERSION\s*=\s*(\d+)", text)
    if version_match is None:
        raise SystemExit("Could not parse ReviewNodeCatalog.VERSION")
    node_ids = sorted(set(re.findall(r'\b(?:node|cross)\(\s*"([LBMSX]\d{2})"', text)))
    if len(node_ids) != 28:
        raise SystemExit(f"Expected 28 review nodes, found {len(node_ids)}")
    return int(version_match.group(1)), node_ids


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
        library=nodes_with_prefix(node_ids, "L") | {"B01"},
        book_reader=book_reader,
        source=nodes_with_prefix(node_ids, "S"),
        more=nodes_with_prefix(node_ids, "M"),
    )


def classify_review_contract(normalized: str, groups: ReviewNodeGroups) -> Classification | None:
    if normalized.startswith(SKILL_ROOT.as_posix() + "/"):
        return "workflow", {"X06"}, ["project review skill changed"]
    if normalized in {path.as_posix() for path in WORKFLOW_FILES}:
        return "workflow", {"X06"}, ["repository Android workflow changed"]
    if normalized.endswith("docs/design/INTERACTIVE_PROTOTYPE_PLAN.md"):
        return "workflow", {"X06"}, ["interactive review operating contract changed"]
    if normalized.endswith((
        "docs/design/UI_CONSTITUTION.md",
        "docs/design/UI_ATLAS.md",
        "docs/design/DESIGN_DIRECTION_HANDOFF.md",
        "docs/design/DESIGN_REFERENCE_REVIEW.md",
        "docs/phases/PHASE_4.md",
    )):
        return "contract", groups.all, ["binding UI/review contract changed"]
    if normalized.endswith("ReviewNodeCatalog.kt"):
        return "contract", groups.all, ["review scope authority changed"]
    if "/prototype/ui-atlas/review/" in normalized:
        category = "review-runtime" if normalized.endswith(".kt") else "evidence"
        return category, {"X06"}, ["review storage/export contract changed"]
    if "/prototype/ui-atlas/src/main/kotlin/" in normalized and "/review/" in normalized:
        return "review-runtime", {"X06"}, ["in-app reviewer runtime changed"]
    return None


def classify_prototype(normalized: str, groups: ReviewNodeGroups) -> Classification | None:
    if "/prototype/ui-atlas/src/screenshotTest" in normalized:
        return "evidence", groups.surface | {"X02", "X03", "X04", "X05"}, ["Atlas static evidence definition changed"]
    if normalized.endswith(("LibraryAtlasScreens.kt", "LibraryAtlasFixtures.kt")):
        nodes = nodes_with_prefix(groups.all, "L") | groups.book_reader | {"X01", "X02", "X04", "X05"}
        return "runtime", nodes, ["Library/Book/Reader prototype surface changed"]
    if normalized.endswith(("SourceAtlasScreens.kt", "SourceAtlasFixtures.kt")):
        return "runtime", groups.source | {"B01", "L08", "X01", "X02", "X04", "X05"}, ["source/search prototype surface changed"]
    if normalized.endswith(("MoreAtlasScreens.kt", "MoreAtlasFixtures.kt")):
        return "runtime", groups.more | {"X01", "X02", "X04", "X05"}, ["More/settings prototype surface changed"]
    if "/prototype/ui-atlas/src/main/kotlin/" in normalized:
        if "/theme/" in normalized:
            return "runtime", groups.surface | {"X02", "X03", "X04"}, ["shared Atlas theme/motion changed"]
        if "/navigation/" in normalized or normalized.endswith(("AtlasApp.kt", "MainActivity.kt")):
            return "runtime", groups.surface | {"X01", "X02"}, ["shared Atlas navigation/host changed"]
        if "/runtime/" in normalized:
            return "runtime", groups.surface | {"X01", "X05", "X06"}, ["shared Atlas state/scenario runtime changed"]
        return "runtime", groups.all, ["shared or unclassified Atlas runtime changed"]
    if "/prototype/ui-atlas/" in normalized:
        return "prototype-build", groups.all, ["prototype build/resource input changed"]
    return None


def classify_test_source(normalized: str, groups: ReviewNodeGroups) -> Classification | None:
    if not any(marker in normalized for marker in ("/src/test/", "/src/androidTest/", "/src/screenshotTest/")):
        return None
    if "/feature/library/" in normalized:
        nodes = groups.library | {"X01", "X02", "X04", "X05"}
    elif "/feature/browse/" in normalized:
        nodes = groups.source | {"B01", "X01", "X02", "X04", "X05"}
    elif "/feature/settings/" in normalized:
        nodes = groups.more | {"B03", "X01", "X02", "X04", "X05"}
    elif "/reader/" in normalized or "/shared/locator/" in normalized:
        nodes = groups.book_reader | {"M03", "X01", "X05"}
    elif "/core/ui/" in normalized:
        nodes = groups.surface | {"X02", "X03", "X04", "X05"}
    else:
        nodes = groups.all
    return "evidence", nodes, ["automated contract/evidence source changed"]


def classify_production(normalized: str, groups: ReviewNodeGroups) -> Classification | None:
    rules: tuple[tuple[bool, Classification], ...] = (
        ("/feature/library/" in normalized, ("runtime", groups.library | {"X01", "X02", "X04", "X05"}, ["production Library feature changed"])),
        ("/feature/browse/" in normalized, ("runtime", groups.source | {"B01", "X01", "X02", "X04", "X05"}, ["production Browse/Search feature changed"])),
        ("/feature/settings/" in normalized, ("runtime", groups.more | {"B03", "X01", "X02", "X04", "X05"}, ["production More/settings feature changed"])),
        ("/reader/" in normalized, ("runtime", groups.book_reader | {"M03", "X01", "X02", "X03", "X04", "X05"}, ["Reader implementation changed"])),
        ("/core/ui/" in normalized, ("runtime", groups.surface | {"X02", "X03", "X04", "X05"}, ["shared production UI changed"])),
        ("/core/display/" in normalized, ("runtime", {"M02", "M03", "B02", "B03", "X03", "X04"}, ["display/profile behavior changed"])),
        ("/core/files/" in normalized or "/shared/backup/" in normalized, ("runtime", {"M04", "M05", "X01", "X05"}, ["data transfer/file behavior changed"])),
        ("/shared/locator/" in normalized, ("runtime", groups.book_reader | {"X01", "X05"}, ["reader locator semantics changed"])),
        ("/shared/smart-shelf/" in normalized, ("runtime", nodes_with_prefix(groups.all, "L") | {"X01", "X05"}, ["shelf membership/rule behavior changed"])),
        ("/source/" in normalized or "/core/network/" in normalized, ("runtime", groups.source | {"B01", "L08", "X05"}, ["source/network state behavior changed"])),
        ("/core/security/" in normalized, ("runtime", {"S04", "M04", "M05", "X02", "X05"}, ["security boundary or failure behavior changed"])),
        ("/app/" in normalized, ("runtime", groups.surface | {"X01", "X02", "X05"}, ["application navigation/host changed"])),
    )
    return next((classification for matches, classification in rules if matches), None)


def classify_repository_input(normalized: str, groups: ReviewNodeGroups) -> Classification:
    if normalized.endswith((".gradle.kts", ".toml", ".properties", ".lockfile")) or "/gradle/" in normalized:
        return "build", set(), ["build or dependency input changed"]
    if "/docs/" in normalized or normalized.endswith(".md"):
        return "workflow", {"X06"}, ["non-binding process/documentation changed"]
    if normalized.endswith((".kt", ".java", ".xml")) and normalized.startswith("tsuyomi-android/"):
        return "runtime", groups.all, ["unknown Android source change; conservative full scope"]
    return "other", set(), ["non-UI repository input changed"]


def classify_change(path: str, node_ids: list[str]) -> Classification:
    normalized = path.replace("\\", "/")
    groups = review_node_groups(node_ids)
    for classifier in (classify_review_contract, classify_prototype, classify_test_source, classify_production):
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
    parser.add_argument("--baseline", type=Path, help="previous R1 report or baseline JSON")
    parser.add_argument("--output", type=Path, required=True, help="output R1 report JSON")
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


@dataclass
class ChangeAnalysis:
    changes: list[dict]
    affected_reasons: dict[str, list[str]]
    changed_kotlin_files: list[str]


def load_baseline(args: argparse.Namespace, repo_root: Path) -> BaselineState:
    if args.baseline is None:
        return BaselineState(None, {}, None)
    baseline_path = args.baseline if args.baseline.is_absolute() else repo_root / args.baseline
    data = json.loads(baseline_path.read_text(encoding="utf-8"))
    if data.get("schema") not in SUPPORTED_BASELINE_SCHEMAS:
        raise SystemExit("Unsupported R1 baseline schema")
    return BaselineState(data, baseline_files(data), baseline_build_id(data))


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


def summarize_changes(
    analysis: ChangeAnalysis,
    node_ids: list[str],
    force_full_review: bool,
) -> tuple[str, list[str], bool, bool, bool]:
    classes = {change["class"] for change in analysis.changes}
    affected_nodes = sorted(analysis.affected_reasons)
    build_required = force_full_review or bool(classes & {"runtime", "review-runtime", "prototype-build", "build", "unknown"})
    device_required = force_full_review or bool(classes & {"runtime", "review-runtime", "prototype-build", "unknown"})
    product_runtime_changed = force_full_review or bool(classes & {"runtime", "unknown"})
    if not analysis.changes:
        scope = "none"
    elif classes <= {"workflow", "other"}:
        scope = "workflow-only"
    elif set(affected_nodes) == set(node_ids):
        scope = "full"
    else:
        scope = "targeted"
    return scope, affected_nodes, build_required, device_required, product_runtime_changed


def build_report(
    args: argparse.Namespace,
    baseline: BaselineState,
    analysis: ChangeAnalysis,
    node_ids: list[str],
    catalog_version: int,
    current_files: dict[str, str],
    current_build_id: str,
    review_policy: dict,
    review_policy_hash: str,
) -> dict:
    scope, affected_nodes, build_required, device_required, product_runtime_changed = summarize_changes(
        analysis,
        node_ids,
        args.force_full_review,
    )
    deferred_profiles = [item["profile"] for item in review_policy["deferredProfiles"]]
    return {
        "schema": REPORT_SCHEMA,
        "generatedAt": utc_now(),
        "provisional": True,
        "reviewAuthority": {
            "aiMayApprove": False,
            "reviewStateModified": False,
            "note": "R1 scopes review work only; unchanged or empty scope never implies approval",
        },
        "baseline": {
            "path": None if args.baseline is None else args.baseline.as_posix(),
            "buildId": baseline.build_id,
            "available": baseline.data is not None,
        },
        "current": {
            "buildId": current_build_id,
            "buildIdentityChanged": baseline.build_id is not None and baseline.build_id != current_build_id,
            "reviewCatalogVersion": catalog_version,
            "reviewNodeCount": len(node_ids),
        },
        "reviewPolicy": {
            "path": POLICY_PATH.as_posix(),
            "sha256": review_policy_hash,
            "mode": review_policy["mode"],
            "activeProfiles": review_policy["activeProfiles"],
            "deferredProfiles": deferred_profiles,
            "resume": review_policy["resume"],
        },
        "summary": {
            "scope": scope,
            "changedFiles": len(analysis.changes),
            "affectedNodeCount": len(affected_nodes),
            "affectedNodes": affected_nodes,
            "requiresGradleBuild": build_required,
            "requiresDevicePass": device_required,
            "requiresDeviceProfiles": review_policy["activeProfiles"] if device_required else [],
            "deferredProfilesAffected": deferred_profiles if device_required else [],
            "requiresJourneySelection": product_runtime_changed and bool({"B03", "X01", "X05", "X06"} & set(affected_nodes)),
            "changedKotlinFiles": sorted(analysis.changed_kotlin_files),
            "androidStudioAnalyzeRecommended": False,
        },
        "changes": analysis.changes,
        "affectedNodes": [
            {"id": node_id, "reasons": sorted(set(analysis.affected_reasons[node_id]))}
            for node_id in affected_nodes
        ],
        "next": {
            "stage": (
                "R2 affected-state review on " + ", ".join(review_policy["activeProfiles"])
                if device_required else "build verification" if build_required else "R1 complete"
            ),
            "avoid": [
                "Do not edit Review Graph progress or verdicts during R1",
                "Do not build, deploy, capture, or start an emulator for workflow-only changes",
                "Do not execute deferred profile matrices until the policy resume trigger",
                "Do not run Android Studio analysis when compiler or lint already answers the question",
            ],
        },
        "files": current_files,
    }


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
        "requiresDevicePass": summary["requiresDevicePass"],
    }, ensure_ascii=False))


def main() -> int:
    args = parse_args()
    repo_root = find_repo_root(args.root)
    catalog_version, node_ids = parse_catalog(repo_root)
    review_policy, review_policy_hash = load_review_policy(repo_root)
    current_files = collect_files(repo_root)
    current_build_id = compute_prototype_build_id(repo_root)
    baseline = load_baseline(args, repo_root)
    analysis = detect_changes(baseline.files, current_files, node_ids)
    add_synthetic_changes(analysis, baseline.data is not None, args.force_full_review, node_ids)
    report = build_report(
        args,
        baseline,
        analysis,
        node_ids,
        catalog_version,
        current_files,
        current_build_id,
        review_policy,
        review_policy_hash,
    )
    output_path = write_report(repo_root, args.output, report)
    print_summary(repo_root, output_path, report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
