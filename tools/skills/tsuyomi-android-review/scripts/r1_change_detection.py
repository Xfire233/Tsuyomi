#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import argparse
import hashlib
import json
import re
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


def classify_change(path: str, node_ids: list[str]) -> tuple[str, set[str], list[str]]:
    normalized = path.replace("\\", "/")
    all_nodes = set(node_ids)
    surface_nodes = nodes_with_prefix(node_ids, "L", "B", "S", "M")
    library_nodes = nodes_with_prefix(node_ids, "L") | {"B01"}
    book_reader_nodes = nodes_with_prefix(node_ids, "B")
    source_nodes = nodes_with_prefix(node_ids, "S")
    more_nodes = nodes_with_prefix(node_ids, "M")

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
        "docs/gates/GATE_4.md",
    )):
        return "contract", all_nodes, ["binding UI/review contract changed"]
    if normalized.endswith("ReviewNodeCatalog.kt"):
        return "contract", all_nodes, ["review scope authority changed"]
    if "/prototype/ui-atlas/review/" in normalized:
        return "review-runtime" if normalized.endswith(".kt") else "evidence", {"X06"}, ["review storage/export contract changed"]
    if "/prototype/ui-atlas/src/main/kotlin/" in normalized and "/review/" in normalized:
        return "review-runtime", {"X06"}, ["in-app reviewer runtime changed"]
    if "/prototype/ui-atlas/src/screenshotTest" in normalized:
        return "evidence", surface_nodes | {"X02", "X03", "X04", "X05"}, ["Atlas static evidence definition changed"]
    if normalized.endswith(("LibraryAtlasScreens.kt", "LibraryAtlasFixtures.kt")):
        return "runtime", nodes_with_prefix(node_ids, "L") | book_reader_nodes | {"X01", "X02", "X04", "X05"}, ["Library/Book/Reader prototype surface changed"]
    if normalized.endswith(("SourceAtlasScreens.kt", "SourceAtlasFixtures.kt")):
        return "runtime", source_nodes | {"B01", "L08", "X01", "X02", "X04", "X05"}, ["source/search prototype surface changed"]
    if normalized.endswith(("MoreAtlasScreens.kt", "MoreAtlasFixtures.kt")):
        return "runtime", more_nodes | {"X01", "X02", "X04", "X05"}, ["More/settings prototype surface changed"]
    if "/prototype/ui-atlas/src/main/kotlin/" in normalized:
        if "/theme/" in normalized:
            return "runtime", surface_nodes | {"X02", "X03", "X04"}, ["shared Atlas theme/motion changed"]
        if "/navigation/" in normalized or normalized.endswith(("AtlasApp.kt", "MainActivity.kt")):
            return "runtime", surface_nodes | {"X01", "X02"}, ["shared Atlas navigation/host changed"]
        if "/runtime/" in normalized:
            return "runtime", surface_nodes | {"X01", "X05", "X06"}, ["shared Atlas state/scenario runtime changed"]
        return "runtime", all_nodes, ["shared or unclassified Atlas runtime changed"]
    if "/prototype/ui-atlas/" in normalized:
        return "prototype-build", all_nodes, ["prototype build/resource input changed"]
    test_markers = ("/src/test/", "/src/androidTest/", "/src/screenshotTest/")
    if any(marker in normalized for marker in test_markers):
        if "/feature/library/" in normalized:
            nodes = library_nodes | {"X01", "X02", "X04", "X05"}
        elif "/feature/browse/" in normalized:
            nodes = source_nodes | {"B01", "X01", "X02", "X04", "X05"}
        elif "/feature/settings/" in normalized:
            nodes = more_nodes | {"B03", "X01", "X02", "X04", "X05"}
        elif "/reader/" in normalized or "/shared/locator/" in normalized:
            nodes = book_reader_nodes | {"M03", "X01", "X05"}
        elif "/core/ui/" in normalized:
            nodes = surface_nodes | {"X02", "X03", "X04", "X05"}
        else:
            nodes = all_nodes
        return "evidence", nodes, ["automated contract/evidence source changed"]

    if "/feature/library/" in normalized:
        return "runtime", library_nodes | {"X01", "X02", "X04", "X05"}, ["production Library feature changed"]
    if "/feature/browse/" in normalized:
        return "runtime", source_nodes | {"B01", "X01", "X02", "X04", "X05"}, ["production Browse/Search feature changed"]
    if "/feature/settings/" in normalized:
        return "runtime", more_nodes | {"B03", "X01", "X02", "X04", "X05"}, ["production More/settings feature changed"]
    if "/reader/" in normalized:
        return "runtime", book_reader_nodes | {"M03", "X01", "X02", "X03", "X04", "X05"}, ["Reader implementation changed"]
    if "/core/ui/" in normalized:
        return "runtime", surface_nodes | {"X02", "X03", "X04", "X05"}, ["shared production UI changed"]
    if "/core/display/" in normalized:
        return "runtime", {"M02", "M03", "B02", "B03", "X03", "X04"}, ["display/profile behavior changed"]
    if "/core/files/" in normalized or "/shared/backup/" in normalized:
        return "runtime", {"M04", "M05", "X01", "X05"}, ["data transfer/file behavior changed"]
    if "/shared/locator/" in normalized:
        return "runtime", book_reader_nodes | {"X01", "X05"}, ["reader locator semantics changed"]
    if "/shared/smart-shelf/" in normalized:
        return "runtime", nodes_with_prefix(node_ids, "L") | {"X01", "X05"}, ["shelf membership/rule behavior changed"]
    if "/source/" in normalized or "/core/network/" in normalized:
        return "runtime", source_nodes | {"B01", "L08", "X05"}, ["source/network state behavior changed"]
    if "/core/security/" in normalized:
        return "runtime", {"S04", "M04", "M05", "X02", "X05"}, ["security boundary or failure behavior changed"]
    if "/app/" in normalized:
        return "runtime", surface_nodes | {"X01", "X02", "X05"}, ["application navigation/host changed"]

    if normalized.endswith((".gradle.kts", ".toml", ".properties", ".lockfile")) or "/gradle/" in normalized:
        return "build", set(), ["build or dependency input changed"]
    if "/docs/" in normalized or normalized.endswith(".md"):
        return "workflow", {"X06"}, ["non-binding process/documentation changed"]
    if normalized.endswith((".kt", ".java", ".xml")) and normalized.startswith("tsuyomi-android/"):
        return "runtime", all_nodes, ["unknown Android source change; conservative full scope"]
    return "other", set(), ["non-UI repository input changed"]


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


def main() -> int:
    args = parse_args()
    repo_root = find_repo_root(args.root)
    catalog_version, node_ids = parse_catalog(repo_root)
    review_policy, review_policy_hash = load_review_policy(repo_root)
    current_files = collect_files(repo_root)
    current_build_id = compute_prototype_build_id(repo_root)

    baseline_data: dict | None = None
    previous_files: dict[str, str] = {}
    previous_build_id: str | None = None
    if args.baseline is not None:
        baseline_path = args.baseline if args.baseline.is_absolute() else repo_root / args.baseline
        baseline_data = json.loads(baseline_path.read_text(encoding="utf-8"))
        if baseline_data.get("schema") not in SUPPORTED_BASELINE_SCHEMAS:
            raise SystemExit("Unsupported R1 baseline schema")
        previous_files = baseline_files(baseline_data)
        previous_build_id = baseline_build_id(baseline_data)

    changes = []
    affected_reasons: dict[str, list[str]] = {}
    changed_kotlin_files: list[str] = []
    build_classes = {"runtime", "review-runtime", "prototype-build", "build", "unknown"}
    device_classes = {"runtime", "review-runtime", "prototype-build", "unknown"}
    for path in sorted(set(previous_files) | set(current_files)):
        old_hash = previous_files.get(path)
        new_hash = current_files.get(path)
        if old_hash == new_hash:
            continue
        status = "added" if old_hash is None else "removed" if new_hash is None else "modified"
        change_class, nodes, reasons = classify_change(path, node_ids)
        for node_id in sorted(nodes):
            affected_reasons.setdefault(node_id, []).extend(f"{path}: {reason}" for reason in reasons)
        if path.endswith(".kt") or path.endswith(".kts"):
            changed_kotlin_files.append(path)
        changes.append({
            "path": path,
            "status": status,
            "oldSha256": old_hash,
            "newSha256": new_hash,
            "class": change_class,
            "affectedNodes": sorted(nodes),
            "reasons": reasons,
        })

    if baseline_data is None:
        changes.append({
            "path": "<no-baseline>",
            "status": "unknown",
            "oldSha256": None,
            "newSha256": None,
            "class": "unknown",
            "affectedNodes": node_ids,
            "reasons": ["no file-hash baseline; conservative cold-start scope"],
        })
        for node_id in node_ids:
            affected_reasons.setdefault(node_id, []).append("no file-hash baseline")


    if args.force_full_review:
        changes.append({
            "path": "<forced-full-review>",
            "status": "requested",
            "oldSha256": None,
            "newSha256": None,
            "class": "review-request",
            "affectedNodes": node_ids,
            "reasons": ["operator requested a complete AI review"],
        })
        for node_id in node_ids:
            affected_reasons.setdefault(node_id, []).append("operator requested a complete AI review")
    classes = {change["class"] for change in changes}
    affected_nodes = sorted(affected_reasons)
    build_required = args.force_full_review or any(change["class"] in build_classes for change in changes)
    device_required = args.force_full_review or any(change["class"] in device_classes for change in changes)
    product_runtime_changed = args.force_full_review or any(change["class"] in {"runtime", "unknown"} for change in changes)
    if not changes:
        scope = "none"
    elif classes <= {"workflow", "other"}:
        scope = "workflow-only"
    elif set(affected_nodes) == set(node_ids):
        scope = "full"
    else:
        scope = "targeted"

    report = {
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
            "buildId": previous_build_id,
            "available": baseline_data is not None,
        },
        "current": {
            "buildId": current_build_id,
            "buildIdentityChanged": previous_build_id is not None and previous_build_id != current_build_id,
            "reviewCatalogVersion": catalog_version,
            "reviewNodeCount": len(node_ids),
        },
        "reviewPolicy": {
            "path": POLICY_PATH.as_posix(),
            "sha256": review_policy_hash,
            "mode": review_policy["mode"],
            "activeProfiles": review_policy["activeProfiles"],
            "deferredProfiles": [item["profile"] for item in review_policy["deferredProfiles"]],
            "resume": review_policy["resume"],
        },
        "summary": {
            "scope": scope,
            "changedFiles": len(changes),
            "affectedNodeCount": len(affected_nodes),
            "affectedNodes": affected_nodes,
            "requiresGradleBuild": build_required,
            "requiresDevicePass": device_required,
            "requiresDeviceProfiles": review_policy["activeProfiles"] if device_required else [],
            "deferredProfilesAffected": [item["profile"] for item in review_policy["deferredProfiles"]] if device_required else [],
            "requiresJourneySelection": product_runtime_changed and bool({"B03", "X01", "X05", "X06"} & set(affected_nodes)),
            "changedKotlinFiles": sorted(changed_kotlin_files),
            "androidStudioAnalyzeRecommended": False,
        },
        "changes": changes,
        "affectedNodes": [
            {"id": node_id, "reasons": sorted(set(affected_reasons[node_id]))}
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

    output_path = args.output if args.output.is_absolute() else repo_root / args.output
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": output_path.relative_to(repo_root).as_posix(),
        "scope": scope,
        "changedFiles": len(changes),
        "affectedNodes": affected_nodes,
        "buildId": current_build_id,
        "reviewPolicyMode": review_policy["mode"],
        "activeProfiles": review_policy["activeProfiles"],
        "deferredProfiles": [item["profile"] for item in review_policy["deferredProfiles"]],
        "requiresGradleBuild": build_required,
        "requiresDevicePass": device_required,
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
