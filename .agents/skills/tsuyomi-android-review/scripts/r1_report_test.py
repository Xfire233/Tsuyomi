# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import unittest

from r1_report import REPORT_SCHEMA, ReportBuildContext, build_report


class ReportBuilderTest(unittest.TestCase):
    def test_workflow_only_change_has_no_runtime_or_candidate_evidence(self) -> None:
        report = build_report(
            self.context(
                changes=[self.change("workflow", ["X06"])],
                affected_reasons={"X06": ["review workflow changed"]},
            ),
        )

        self.assertEqual(REPORT_SCHEMA, report["schema"])
        self.assertEqual("workflow-only", report["summary"]["scope"])
        self.assertFalse(report["summary"]["requiresGradleBuild"])
        self.assertFalse(report["summary"]["requiresDevicePass"])
        self.assertEqual([], report["next"]["evidencePlan"]["sharedCandidateTaskFlowNodes"])
        self.assertEqual([], report["next"]["evidencePlan"]["sharedCandidateLiveServiceNodes"])
        self.assertIsNone(report["next"]["evidencePlan"]["humanReviewState"])

    def test_mixed_workflow_and_runtime_routes_online_evidence_per_row(self) -> None:
        report = build_report(self.context(
            changes=[self.change("workflow", ["X06"]), self.change("runtime", ["S01"])],
            affected_reasons={"X06": ["workflow"], "S01": ["source runtime"]},
        ))

        plan = report["next"]["evidencePlan"]
        self.assertEqual(["S01"], plan["ciRegressionNodes"])
        self.assertEqual(["S01"], plan["ciControlledFixtureReplayNodes"])
        self.assertEqual(["S01"], plan["sharedCandidateTaskFlowNodes"])
        self.assertEqual(["S01"], plan["sharedCandidateLiveServiceNodes"])
        self.assertEqual("PENDING", plan["humanReviewState"])
        self.assertNotIn("X06", plan["sharedCandidateTaskFlowNodes"])

    def test_contract_only_change_keeps_runtime_lanes_empty(self) -> None:
        report = build_report(self.context(
            changes=[self.change("contract", ["B01", "S01", "X06"])],
            affected_reasons={node: ["contract"] for node in ("B01", "S01", "X06")},
        ))

        plan = report["next"]["evidencePlan"]
        self.assertFalse(plan["ciRequired"])
        self.assertEqual([], plan["sharedCandidateTaskFlowNodes"])
        self.assertEqual([], plan["sharedCandidateLiveServiceNodes"])

    def test_contract_row_does_not_turn_unrelated_nodes_into_runtime_journeys(self) -> None:
        report = build_report(self.context(
            changes=[self.change("contract", ["B03", "S01"]), self.change("runtime", ["S01"])],
            affected_reasons={"B03": ["contract"], "S01": ["source runtime"]},
        ))

        self.assertEqual(["S01"], report["next"]["evidencePlan"]["sharedCandidateTaskFlowNodes"])
        self.assertFalse(report["summary"]["requiresJourneySelection"])


    def test_evidence_only_change_stays_in_ci_without_shared_candidate_work(self) -> None:
        report = build_report(self.context(
            changes=[self.change("evidence", ["S02", "X01"])],
            affected_reasons={node: ["test changed"] for node in ("S02", "X01")},
        ))

        plan = report["next"]["evidencePlan"]
        self.assertTrue(plan["ciRequired"])
        self.assertEqual(["S02", "X01"], plan["ciRegressionNodes"])
        self.assertEqual(["S02", "X01"], plan["ciControlledFixtureReplayNodes"])
        self.assertEqual([], plan["sharedCandidateTaskFlowNodes"])
        self.assertIsNone(plan["humanReviewState"])

    def test_build_only_requires_ci_with_no_node_ids(self) -> None:
        report = build_report(self.context(
            changes=[self.change("build", [])],
            affected_reasons={},
        ))

        self.assertTrue(report["summary"]["requiresGradleBuild"])
        self.assertFalse(report["summary"]["requiresDevicePass"])
        self.assertEqual({
            "ciRequired": True,
            "ciRegressionNodes": [],
            "ciControlledFixtureReplayNodes": [],
            "sharedCandidateTaskFlowNodes": [],
            "sharedCandidateLiveServiceNodes": [],
            "humanReviewState": None,
            "standaloneFixtureReview": False,
        }, report["next"]["evidencePlan"])

    def test_source_runtime_selects_build_replay_live_and_human_lanes(self) -> None:
        report = build_report(self.context(
            changes=[self.change("runtime", ["S01"])],
            affected_reasons={"S01": ["source runtime changed"]},
            changed_kotlin_files={"Source.kt"},
        ))

        plan = report["next"]["evidencePlan"]
        self.assertTrue(report["summary"]["requiresGradleBuild"])
        self.assertTrue(report["summary"]["requiresDevicePass"])
        self.assertEqual(["S01"], plan["ciControlledFixtureReplayNodes"])
        self.assertEqual(["S01"], plan["sharedCandidateTaskFlowNodes"])
        self.assertEqual(["S01"], plan["sharedCandidateLiveServiceNodes"])
        self.assertEqual(["STANDARD"], report["summary"]["requiresDeviceProfiles"])
        self.assertEqual(["EINK"], report["summary"]["deferredProfilesAffected"])
        self.assertEqual(["Source.kt"], report["summary"]["changedKotlinFiles"])

    def test_force_full_and_cold_start_keep_conservative_active_scope(self) -> None:
        all_nodes = ["B01", "S01", "X06"]
        for change, force_full in (
            (self.change("review-request", all_nodes), True),
            (self.change("unknown", all_nodes), False),
        ):
            with self.subTest(change=change["class"]):
                report = build_report(self.context(
                    changes=[change],
                    affected_reasons={node: ["full scope"] for node in all_nodes},
                    force_full_review=force_full,
                ))
                plan = report["next"]["evidencePlan"]
                self.assertTrue(report["summary"]["requiresDevicePass"])
                self.assertEqual(all_nodes, plan["sharedCandidateTaskFlowNodes"])
                self.assertEqual(["S01", "X06"], plan["sharedCandidateLiveServiceNodes"])
                self.assertEqual("PENDING", plan["humanReviewState"])

    def test_deferred_nodes_remain_accounted_without_entering_active_evidence(self) -> None:
        context = self.context(
            changes=[self.change("runtime", ["E01", "S01"])],
            affected_reasons={"E01": ["deferred source"], "S01": ["active source"]},
        )
        context.review_policy["nodeExecution"]["deferredStages"] = [{"nodePrefixes": ["E"]}]
        report = build_report(context)

        self.assertEqual(["E01"], report["summary"]["deferredNodes"])
        self.assertEqual(["S01"], report["next"]["evidencePlan"]["sharedCandidateTaskFlowNodes"])

    @staticmethod
    def change(change_class: str, nodes: list[str]) -> dict:
        return {"class": change_class, "affectedNodes": nodes}

    @staticmethod
    def context(
        *,
        changes: list[dict],
        affected_reasons: dict[str, list[str]],
        changed_kotlin_files: set[str] | None = None,
        force_full_review: bool = False,
    ) -> ReportBuildContext:
        return ReportBuildContext(
            baseline_path=None,
            baseline_build_id="baseline-build",
            baseline_available=True,
            force_full_review=force_full_review,
            changes=changes,
            affected_reasons=affected_reasons,
            changed_kotlin_files=changed_kotlin_files or set(),
            node_ids=["B03", "X06"],
            catalog_version=5,
            current_files={"Reader.kt": "sha256"},
            current_build_id="current-build",
            review_policy={
                "mode": "phase4a-production-standard-first",
                "activeProfiles": ["STANDARD"],
                "deferredProfiles": [{"profile": "EINK"}],
                "resume": {"trigger": "explicit"},
                "nodeExecution": {
                    "activeStage": "PHASE4A_PRODUCTION_IMPLEMENTATION",
                    "activeNodePrefixes": ["L", "B", "M", "S", "X"],
                    "actualOnlineRequirements": {"nodePrefixes": ["S", "X"]},
                    "deferredStages": [],
                },
            },
            review_policy_path=".agents/skills/tsuyomi-android-review/review-policy.json",
            review_policy_hash="policy-sha256",
        )


if __name__ == "__main__":
    unittest.main()
