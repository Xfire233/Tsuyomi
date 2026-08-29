#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

REPORT_SCHEMA = "tsuyomi-r1-change-report-v2"


@dataclass(frozen=True)
class ReportBuildContext:
    baseline_path: str | None
    baseline_build_id: str | None
    baseline_available: bool
    force_full_review: bool
    changes: list[dict[str, Any]]
    affected_reasons: dict[str, list[str]]
    changed_kotlin_files: set[str]
    node_ids: list[str]
    catalog_version: int
    current_files: dict[str, str]
    current_build_id: str
    review_policy: dict[str, Any]
    review_policy_path: str
    review_policy_hash: str


def build_report(context: ReportBuildContext) -> dict[str, Any]:
    summary = _summarize_changes(context)
    deferred_profiles = [item["profile"] for item in context.review_policy["deferredProfiles"]]
    return {
        "schema": REPORT_SCHEMA,
        "generatedAt": _utc_now(),
        "provisional": True,
        "reviewAuthority": _review_authority(),
        "baseline": _baseline_section(context),
        "current": _current_section(context),
        "reviewPolicy": _review_policy_section(context, deferred_profiles),
        "summary": _summary_section(context, summary, deferred_profiles),
        "changes": context.changes,
        "affectedNodes": _affected_nodes(context, summary.affected_nodes),
        "next": _next_section(context, summary),
        "files": context.current_files,
    }


@dataclass(frozen=True)
class ChangeSummary:
    scope: str
    affected_nodes: list[str]
    current_stage_nodes: list[str]
    deferred_nodes: list[str]
    build_required: bool
    device_required: bool
    product_runtime_changed: bool

def _summarize_changes(context: ReportBuildContext) -> ChangeSummary:
    classes = {change["class"] for change in context.changes}
    affected_nodes = sorted(context.affected_reasons)
    execution = context.review_policy["nodeExecution"]
    active_prefixes = tuple(execution["activeNodePrefixes"])
    deferred_prefixes = tuple(
        prefix
        for stage in execution["deferredStages"]
        for prefix in stage["nodePrefixes"]
    )
    current_stage_nodes = [node for node in affected_nodes if node.startswith(active_prefixes)]
    deferred_nodes = [node for node in affected_nodes if node.startswith(deferred_prefixes)]
    active_runtime_change = bool(current_stage_nodes) and bool(
        classes & {"runtime", "review-runtime", "prototype-build", "build", "unknown"}
    )
    build_required = context.force_full_review or active_runtime_change
    device_required = context.force_full_review or (
        bool(current_stage_nodes) and bool(classes & {"runtime", "review-runtime", "prototype-build", "unknown"})
    )
    product_runtime_changed = bool(current_stage_nodes) and (
        context.force_full_review or bool(classes & {"runtime", "unknown"})
    )
    if not context.changes:
        scope = "none"
    elif classes <= {"workflow", "other"}:
        scope = "workflow-only"
    elif set(affected_nodes) == set(context.node_ids):
        scope = "full"
    else:
        scope = "targeted"
    return ChangeSummary(
        scope,
        affected_nodes,
        current_stage_nodes,
        deferred_nodes,
        build_required,
        device_required,
        product_runtime_changed,
    )


def _review_authority() -> dict[str, Any]:
    return {
        "aiMayApprove": False,
        "reviewStateModified": False,
        "note": "R1 scopes review work only; unchanged or empty scope never implies approval",
    }


def _baseline_section(context: ReportBuildContext) -> dict[str, Any]:
    return {
        "path": context.baseline_path,
        "buildId": context.baseline_build_id,
        "available": context.baseline_available,
    }


def _current_section(context: ReportBuildContext) -> dict[str, Any]:
    return {
        "buildId": context.current_build_id,
        "buildIdentityChanged": (
            context.baseline_build_id is not None and context.baseline_build_id != context.current_build_id
        ),
        "reviewCatalogVersion": context.catalog_version,
        "reviewNodeCount": len(context.node_ids),
    }


def _review_policy_section(
    context: ReportBuildContext,
    deferred_profiles: list[str],
) -> dict[str, Any]:
    return {
        "path": context.review_policy_path,
        "sha256": context.review_policy_hash,
        "mode": context.review_policy["mode"],
        "activeProfiles": context.review_policy["activeProfiles"],
        "deferredProfiles": deferred_profiles,
        "resume": context.review_policy["resume"],
        "nodeExecution": context.review_policy["nodeExecution"],
    }


def _summary_section(
    context: ReportBuildContext,
    summary: ChangeSummary,
    deferred_profiles: list[str],
) -> dict[str, Any]:
    affected_set = set(summary.current_stage_nodes)
    return {
        "scope": summary.scope,
        "changedFiles": len(context.changes),
        "affectedNodeCount": len(summary.affected_nodes),
        "affectedNodes": summary.affected_nodes,
        "currentStageNodeCount": len(summary.current_stage_nodes),
        "currentStageNodes": summary.current_stage_nodes,
        "deferredNodeCount": len(summary.deferred_nodes),
        "deferredNodes": summary.deferred_nodes,
        "requiresGradleBuild": summary.build_required,
        "requiresDevicePass": summary.device_required,
        "requiresDeviceProfiles": context.review_policy["activeProfiles"] if summary.device_required else [],
        "deferredProfilesAffected": deferred_profiles if summary.device_required else [],
        "requiresJourneySelection": (
            summary.product_runtime_changed and bool({"B03"} & affected_set)
        ),
        "changedKotlinFiles": sorted(context.changed_kotlin_files),
        "androidStudioAnalyzeRecommended": False,
    }


def _affected_nodes(context: ReportBuildContext, affected_nodes: list[str]) -> list[dict[str, Any]]:
    return [
        {"id": node_id, "reasons": sorted(set(context.affected_reasons[node_id]))}
        for node_id in affected_nodes
    ]


def _next_section(
    context: ReportBuildContext,
    summary: ChangeSummary,
) -> dict[str, Any]:
    if summary.device_required:
        stage = "R2 current-stage review on " + ", ".join(context.review_policy["activeProfiles"])
    elif summary.build_required:
        stage = "build verification"
    elif summary.deferred_nodes:
        stage = "actual online scenario review deferred"
    else:
        stage = "R1 complete"
    return {
        "stage": stage,
        "avoid": [
            "Do not edit Review Graph progress or verdicts during R1",
            "Do not build, deploy, capture, or start an emulator for workflow-only changes",
            "Do not execute deferred profile matrices until the policy resume trigger",
            "Do not finalize S/X nodes from the isolated Atlas; they require actual online production scenarios",
            "Do not run Android Studio analysis when compiler or lint already answers the question",
        ],
    }


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
