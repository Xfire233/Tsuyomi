# SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import unittest

from r1_report import REPORT_SCHEMA, ReportBuildContext, build_report


class ReportBuilderTest(unittest.TestCase):
    def test_workflow_only_change_stops_after_r1(self) -> None:
        report = build_report(
            self.context(
                changes=[{"class": "workflow"}],
                affected_reasons={"X06": ["review workflow changed"]},
                changed_kotlin_files=set(),
            ),
        )

        self.assertEqual(REPORT_SCHEMA, report["schema"])
        self.assertEqual("workflow-only", report["summary"]["scope"])
        self.assertFalse(report["summary"]["requiresGradleBuild"])
        self.assertFalse(report["summary"]["requiresDevicePass"])
        self.assertEqual("R1 complete", report["next"]["stage"])
        self.assertEqual(["X06"], report["summary"]["currentStageNodes"])
        self.assertEqual([], report["summary"]["deferredNodes"])

    def test_runtime_reader_change_selects_build_device_and_journey(self) -> None:
        report = build_report(
            self.context(
                changes=[{"class": "runtime"}],
                affected_reasons={"B03": ["reader runtime changed"]},
                changed_kotlin_files={"Reader.kt"},
            ),
        )

        self.assertTrue(report["summary"]["requiresGradleBuild"])
        self.assertTrue(report["summary"]["requiresDevicePass"])
        self.assertTrue(report["summary"]["requiresJourneySelection"])
        self.assertEqual(["STANDARD"], report["summary"]["requiresDeviceProfiles"])
        self.assertEqual(["EINK"], report["summary"]["deferredProfilesAffected"])
        self.assertEqual(["Reader.kt"], report["summary"]["changedKotlinFiles"])

    def test_source_runtime_change_requires_production_build_and_device_work(self) -> None:
        report = build_report(
            self.context(
                changes=[{"class": "runtime"}],
                affected_reasons={"S01": ["source runtime changed"]},
                changed_kotlin_files={"Source.kt"},
            ),
        )

        self.assertTrue(report["summary"]["requiresGradleBuild"])
        self.assertTrue(report["summary"]["requiresDevicePass"])
        self.assertEqual(["S01"], report["summary"]["currentStageNodes"])
        self.assertEqual([], report["summary"]["deferredNodes"])
        self.assertEqual("R2 current-stage review on STANDARD", report["next"]["stage"])

    @staticmethod
    def context(
        *,
        changes: list[dict],
        affected_reasons: dict[str, list[str]],
        changed_kotlin_files: set[str],
    ) -> ReportBuildContext:
        return ReportBuildContext(
            baseline_path=None,
            baseline_build_id="baseline-build",
            baseline_available=True,
            force_full_review=False,
            changes=changes,
            affected_reasons=affected_reasons,
            changed_kotlin_files=changed_kotlin_files,
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
            review_policy_path="tools/skills/tsuyomi-android-review/review-policy.json",
            review_policy_hash="policy-sha256",
        )


if __name__ == "__main__":
    unittest.main()
