/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.feature.settings

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.then
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipe
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.tsuyomi.core.display.DisplayDecisionReason
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayProfile
import org.tsuyomi.core.display.MotionPolicy
import org.tsuyomi.core.preferences.DisplayPreference
import org.tsuyomi.core.preferences.DisplayPreferences
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.shared.backup.PortableReaderPreferences

class ReaderDefaultsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fittingEinkDeviceGroupHasNoPageBarAndChoicesKeepIntrinsicWidths() {
        val display = mutableStateOf(displayEnvironment(DisplayProfile.EINK))
        val preferences = mutableStateOf(PortableReaderPreferences())
        render(display, preferences)
        showProject("device")
        composeRule.onAllNodesWithTag("settings-reader-controls-pagination").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-reader-project-pagination").assertCountEquals(0)
        showProject("typography")
        showControlItem("typography", "font-family")
        val paneWidth = composeRule.onNodeWithTag("settings-reader-controls").fetchSemanticsNode().boundsInRoot.width
        listOf("system", "sans", "serif", "monospace").forEach { option ->
            val choice = composeRule.onNodeWithTag("settings-reader-choice-font-family-$option")
            choice.assertIsDisplayed()
            assertTrue("Unrelated choice buttons must not become full-width rows", choice.fetchSemanticsNode().boundsInRoot.width < paneWidth)
        }
        val choiceRows = listOf("system", "sans", "serif", "monospace").map { option ->
            composeRule.onNodeWithTag("settings-reader-choice-font-family-$option").fetchSemanticsNode().boundsInRoot
        }.groupBy { it.top }.toSortedMap().values.toList()
        assertTrue("The narrow fixture exercises more than one option row", choiceRows.size > 1)
        val rowGap = with(composeRule.density) { org.tsuyomi.core.ui.theme.TsuyomiSpacing.Sm.roundToPx() }
        choiceRows.zipWithNext().forEach { (previous, next) ->
            assertTrue("Wrapped setting button frames must not touch",
                next.minOf { it.top } - previous.maxOf { it.bottom } >= rowGap - 1f)
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun einkProjectSidebarIsMeasuredFromLabelsWhenTheContentViewportChanges() {
        val environment = displayEnvironment(DisplayProfile.EINK)
        val viewportWidth = mutableStateOf(320.dp)
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(640.dp, 640.dp))) {
                DisplayEnvironmentProvider(environment) {
                    TsuyomiTheme(environment) {
                        ReaderDefaultsScreen(PortableReaderPreferences(), environment, {}, Modifier.width(viewportWidth.value))
                    }
                }
            }
        }
        val navigation = composeRule.onNodeWithTag("settings-reader-project-pager")
        val initialWidth = navigation.fetchSemanticsNode().boundsInRoot.width
        val label = composeRule.onNode(hasText("排版") and hasAnyAncestor(hasTestTag("settings-reader-project-pager")), useUnmergedTree = true)
            .fetchSemanticsNode()
        val padding = with(label.layoutInfo.density) { 24.dp.toPx() }
        assertTrue("The sidebar contains only labels, minimal padding and the touch target", initialWidth <= label.boundsInRoot.width + padding + 1f)
        composeRule.runOnIdle { viewportWidth.value = 600.dp }
        assertEquals("Additional viewport width belongs to the controls, not the sidebar", initialWidth, navigation.fetchSemanticsNode().boundsInRoot.width, 1f)
        val controls = composeRule.onNodeWithTag("settings-reader-controls").fetchSemanticsNode().boundsInRoot
        assertTrue(controls.width > initialWidth * 3f)
        composeRule.onAllNodesWithTag("settings-reader-project-pagination").assertCountEquals(0)
    }

    @Test
    fun einkNumericSliderSupportsTrackTapDragAndExactDiscreteEndpoints() {
        val display = mutableStateOf(displayEnvironment(DisplayProfile.EINK))
        val preferences = mutableStateOf(PortableReaderPreferences(fontScale = 12.0 / 18.0))
        render(display, preferences)
        showControlItem("typography", "font-size")
        composeRule.onNodeWithTag("settings-reader-value-font-size").assertTextEquals("12sp")
        val slider = composeRule.onNodeWithTag("settings-reader-slider-font-size").assertIsDisplayed()
        val sliderNode = slider.fetchSemanticsNode()
        val range = sliderNode.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(12f..32f, range.range)
        assertEquals(19, range.steps)
        assertEquals(12f, range.current, 0f)
        assertEquals(listOf("字号"), sliderNode.config[SemanticsProperties.ContentDescription])
        assertEquals("12sp", sliderNode.config[SemanticsProperties.StateDescription])
        assertTrue("The accessibility target must retain native SetProgress", sliderNode.config.contains(SemanticsActions.SetProgress))
        // Compose measures minimum sizes in integer pixels; do not confuse rounding with clipping.
        val minimumTarget = with(sliderNode.layoutInfo.density) { 48.dp.roundToPx() }
        val bounds = sliderNode.boundsInRoot
        val touchBounds = sliderNode.touchBoundsInRoot
        val geometry = "density=${sliderNode.layoutInfo.density}, minimum=${minimumTarget}px, " +
            "size=${sliderNode.size}, bounds=$bounds, touchBounds=$touchBounds"
        assertTrue("The native slider must reserve a 48dp target: $geometry", sliderNode.size.height >= minimumTarget)
        assertTrue("The native slider's 48dp accessibility target must not be clipped: $geometry", bounds.height >= minimumTarget)
        assertTrue("The native slider must retain a 48dp touch target: $geometry", touchBounds.height >= minimumTarget)

        fun assertFontSize(expected: Float) {
            assertEquals("Font sizes remain discrete", expected.toInt().toFloat(), expected, 0f)
            composeRule.onNodeWithTag("settings-reader-value-font-size").assertTextEquals("${expected.toInt()}sp")
            assertEquals(expected, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
            composeRule.runOnIdle { assertEquals(expected.toDouble(), preferences.value.fontScale!! * 18.0, 0.000001) }
        }

        slider.performTouchInput { click(Offset(width * 0.55f, centerY)) }
        val tappedSize = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("A track tap must move away from the initial endpoint", tappedSize in 13f..31f)
        assertFontSize(tappedSize)
        slider.performTouchInput {
            swipe(Offset(width * 0.6f, centerY), Offset(width * 0.9f, centerY), durationMillis = 200)
        }
        val draggedSize = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("A drag must follow the track toward its maximum", draggedSize >= 27f)
        assertFontSize(draggedSize)

        slider.performTouchInput { click(Offset(width - 1f, centerY)) }
        assertFontSize(32f)
        slider.performTouchInput { click(Offset(0f, centerY)) }
        assertFontSize(12f)
        // Native draggable consumes touch slop before forwarding deltas; carry the captured drag
        // past each track edge by that amount so the slider itself reaches the exact endpoint.
        slider.performTouchInput {
            swipe(center, Offset(right + viewConfiguration.touchSlop, centerY), durationMillis = 200)
        }
        assertFontSize(32f)
        slider.performTouchInput {
            swipe(center, Offset(left - viewConfiguration.touchSlop, centerY), durationMillis = 200)
        }
        assertFontSize(12f)

        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(32f) }
        assertFontSize(32f)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
        assertFontSize(12f)
    }

    @Test
    fun einkProjectButtonsSelectDirectlyAndRetainValuesAcrossProjectProfileAndScreenReentry() {
        val display = mutableStateOf(displayEnvironment(DisplayProfile.EINK))
        val preferences = mutableStateOf(PortableReaderPreferences())
        val visible = mutableStateOf(true)
        render(display, preferences, visible = visible)

        showProject("device")
        composeRule.onNodeWithTag("settings-reader-project-device").assertIsSelected()
        composeRule.onNodeWithText("保持屏幕常亮").performClick()
        composeRule.runOnIdle { assertEquals(false, preferences.value.keepAwake) }

        showProject("typography")
        showControlItem("typography", "font-size")
        composeRule.onNodeWithTag("settings-reader-slider-font-size")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(19f) }
        composeRule.onNodeWithTag("settings-reader-value-font-size").assertTextEquals("19sp")
        showControlItem("typography", "font-family")
        composeRule.onNodeWithTag("settings-reader-choice-font-family-serif").performClick()
        composeRule.onNodeWithTag("settings-reader-choice-font-family-serif").assertIsSelected()

        showProject("device")
        showProject("typography")
        showControlItem("typography", "font-family")
        composeRule.onNodeWithTag("settings-reader-choice-font-family-serif").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(false, preferences.value.keepAwake)
            assertEquals("serif", preferences.value.fontFamily)
            assertEquals(19.0 / 18.0, preferences.value.fontScale!!, 0.000001)
        }

        composeRule.runOnIdle { display.value = displayEnvironment(DisplayProfile.STANDARD) }
        composeRule.runOnIdle {
            assertEquals(false, preferences.value.keepAwake)
            assertEquals("serif", preferences.value.fontFamily)
            assertEquals(19.0 / 18.0, preferences.value.fontScale!!, 0.000001)
        }
        composeRule.runOnIdle { display.value = displayEnvironment(DisplayProfile.EINK) }
        composeRule.onNodeWithTag("settings-reader-project-typography").assertIsSelected()
        showControlItem("typography", "font-family")
        showControlItem("typography", "font-size")
        composeRule.onNodeWithTag("settings-reader-value-font-size").assertTextEquals("19sp")
        showControlItem("typography", "font-family")
        composeRule.onNodeWithTag("settings-reader-choice-font-family-serif").assertIsSelected()
        composeRule.runOnIdle { visible.value = false }
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithTag("settings-reader-project-typography").assertIsSelected()
        showControlItem("typography", "font-family")
        showControlItem("typography", "font-size")
        composeRule.onNodeWithTag("settings-reader-value-font-size").assertTextEquals("19sp")
        showControlItem("typography", "font-family")
        composeRule.onNodeWithTag("settings-reader-choice-font-family-serif").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(false, preferences.value.keepAwake)
            assertEquals("serif", preferences.value.fontFamily)
            assertEquals(19.0 / 18.0, preferences.value.fontScale!!, 0.000001)
        }
    }

    @Test
    fun einkColorDraftAndCursorSurvivePageProjectAndProfileChanges() {
        val display = mutableStateOf(displayEnvironment(DisplayProfile.EINK))
        val preferences = mutableStateOf(PortableReaderPreferences())
        render(display, preferences)

        showProject("typography")
        showControlItem("typography", "foreground-color")
        val colorField = composeRule.onNodeWithTag("settings-reader-foreground-color")
        colorField.performTextInput("#12")
        colorField.assertIsFocused()
        colorField.performTextInputSelection(TextRange(1))
        colorField.assertDraft("#12", TextRange(1))

        showControlItem("typography", "background-color")
        showControlItem("typography", "foreground-color")
        colorField.assertDraft("#12", TextRange(1)).assertIsFocused()

        showProject("device")
        showProject("typography")
        val projectReturnedField = composeRule.onNodeWithTag("settings-reader-foreground-color")
        projectReturnedField.assertDraft("#12", TextRange(1)).assertIsFocused()

        composeRule.runOnIdle { display.value = displayEnvironment(DisplayProfile.STANDARD) }
        composeRule.runOnIdle { display.value = displayEnvironment(DisplayProfile.EINK) }
        showControlItem("typography", "foreground-color")
        val restoredField = composeRule.onNodeWithTag("settings-reader-foreground-color")
        restoredField.assertDraft("#12", TextRange(1)).assertIsFocused()
        restoredField.performTextInput("3")
        restoredField.assertDraft("#312", TextRange(2))
        composeRule.runOnIdle { assertEquals(null, preferences.value.foregroundColor) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun einkAllProjectAndControlItemsFitNarrowLargeTypeViewportWithoutScrolling() {
        val display = mutableStateOf(displayEnvironment(DisplayProfile.EINK))
        val preferences = mutableStateOf(PortableReaderPreferences())
        render(display, preferences, fontScale = 2f)

        val expectedItems = controlItemKeys
        val visitedItems = mutableMapOf<String, Set<String>>()
        for (project in projectKeys) {
            showProject(project)
            composeRule.onNodeWithTag("settings-reader-project-$project").assertIsSelected()
            visitedItems[project] = visitControlPages(project)
        }

        expectedItems.forEach { (project, keys) ->
            assertEquals(keys.toSet(), visitedItems[project])
        }
        assertTrue(composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun einkLongSettingsPaginationHasTruthfulFirstAndLastActions() {
        val display = mutableStateOf(displayEnvironment(DisplayProfile.EINK))
        val preferences = mutableStateOf(PortableReaderPreferences(fontFamily = "serif", keepAwake = false))
        render(display, preferences, fontScale = 2f)
        val first = composeRule.onNodeWithTag("settings-reader-controls-pagination-first")
        val last = composeRule.onNodeWithTag("settings-reader-controls-pagination-last")
        first.assertIsDisplayed().assertIsNotEnabled()
        last.assertIsDisplayed().performClick()
        last.assertIsNotEnabled()
        first.assertIsDisplayed()
        composeRule.onNodeWithTag("settings-reader-control-item-typography-background-color").assertIsDisplayed()
        assertCurrentPanesFit()
        first.performClick()
        first.assertIsNotEnabled()
        composeRule.onNodeWithTag("settings-reader-control-item-typography-font-size").assertIsDisplayed()
        assertCurrentPanesFit()
        composeRule.runOnIdle {
            assertEquals("serif", preferences.value.fontFamily)
            assertEquals(false, preferences.value.keepAwake)
        }
    }

    @Test
    fun einkDefaultFlowRetainsRequestedStandardFlowWithoutPretendingToControlReaderSession() {
        val display = mutableStateOf(displayEnvironment(DisplayProfile.EINK))
        val preferences = mutableStateOf(PortableReaderPreferences(flow = "scroll"))
        render(display, preferences)
        showProject("page")
        listOf("scroll", "paged", "dual").forEach { option ->
            showControlItem("page", "flow")
            composeRule.onNodeWithTag("settings-reader-choice-flow-$option")
                .assertIsNotEnabled()
        }
        assertEquals("scroll", preferences.value.flow)
        composeRule.runOnIdle { display.value = displayEnvironment(DisplayProfile.STANDARD) }
        assertEquals("scroll", preferences.value.flow)
        composeRule.runOnIdle { display.value = displayEnvironment(DisplayProfile.EINK) }
        assertEquals("scroll", preferences.value.flow)
    }

    @Test
    fun standardReaderDefaultsRemainOneFullScrollableForm() {
        val display = mutableStateOf(displayEnvironment(DisplayProfile.STANDARD))
        val preferences = mutableStateOf(PortableReaderPreferences())
        render(display, preferences)

        composeRule.onNodeWithText("字号").assertExists()
        composeRule.onNodeWithText("保持屏幕常亮").assertExists()
        composeRule.onAllNodesWithTag("settings-reader-project-pager").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-reader-controls").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun render(
        display: MutableState<DisplayEnvironment>,
        preferences: MutableState<PortableReaderPreferences>,
        fontScale: Float = 1f,
        visible: MutableState<Boolean>? = null,
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 480.dp))
                    .then(DeviceConfigurationOverride.FontScale(fontScale)),
            ) {
                val screenStateHolder = rememberSaveableStateHolder()
                if (visible?.value != false) {
                    screenStateHolder.SaveableStateProvider("reader-defaults") {
                        val currentDisplay = display.value
                        DisplayEnvironmentProvider(currentDisplay) {
                            TsuyomiTheme(currentDisplay) {
                                ReaderDefaultsScreen(
                                    preferences = preferences.value,
                                    environment = currentDisplay,
                                    onPreferencesChanged = { preferences.value = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showProject(project: String) {
        showMeasuredItem("settings-reader-project-$project", "settings-reader-project-pagination", 16)
        composeRule.onNodeWithTag("settings-reader-project-$project").assertIsDisplayed().performClick()
    }

    private fun showControlItem(project: String, item: String) {
        showMeasuredItem("settings-reader-control-item-$project-$item", "settings-reader-controls-pagination", 48)
    }

    private fun showMeasuredItem(tag: String, paginationTag: String, pageBound: Int) {
        if (composeRule.onNodeWithTag(tag).isDisplayed()) return
        rewindMeasuredPages(paginationTag, pageBound)
        repeat(pageBound) {
            if (composeRule.onNodeWithTag(tag).isDisplayed()) return
            val next = composeRule.onNodeWithTag("$paginationTag-next")
            check(!next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) {
                "$tag is not reachable through the measured pages"
            }
            next.assertIsDisplayed().performClick()
        }
        error("$tag was not reached within the measured page bound")
    }

    private fun rewindMeasuredPages(paginationTag: String, pageBound: Int) {
        repeat(pageBound) {
            val previous = composeRule.onNodeWithTag("$paginationTag-previous")
            if (composeRule.onAllNodesWithTag("$paginationTag-previous").fetchSemanticsNodes().isEmpty()) return
            if (previous.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) return
            previous.assertIsDisplayed().performClick()
        }
        error("$paginationTag did not reach its first measured page")
    }

    private fun SemanticsNodeInteraction.assertDraft(text: String, selection: TextRange) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))

    private fun visitControlPages(project: String): Set<String> {
        val expected = checkNotNull(controlItemKeys[project])
        rewindMeasuredPages("settings-reader-controls-pagination", 48)
        val visited = mutableSetOf<String>()
        val expectedChoices = when (project) {
            "typography" -> listOf("font-family-system", "font-family-sans", "font-family-serif", "font-family-monospace",
                "font-weight-regular", "font-weight-medium", "text-alignment-start", "text-alignment-justify",
                "text-alignment-center", "text-alignment-end")
            "page" -> listOf("flow-scroll", "flow-paged", "flow-dual", "theme-paper", "theme-warm", "theme-night", "theme-black", "theme-ink-green")
            else -> emptyList()
        }
        val visitedChoices = mutableSetOf<String>()
        var hasNextPage = true
        var pageCount = 0
        while (hasNextPage && pageCount < 48) {
            assertCurrentPanesFit()
            expected.forEach { key ->
                val itemTag = "settings-reader-control-item-$project-$key"
                if (composeRule.onNodeWithTag(itemTag).isDisplayed()) {
                    composeRule.onNodeWithTag(itemTag).assertIsDisplayed()
                    visited += key
                }
            }
            expectedChoices.forEach { choice ->
                val node = composeRule.onNodeWithTag("settings-reader-choice-$choice")
                if (node.isDisplayed()) {
                    node.assertIsDisplayed()
                    visitedChoices += choice
                }
            }
            val next = composeRule.onNodeWithTag("settings-reader-controls-pagination-next")
            hasNextPage = composeRule.onAllNodesWithTag("settings-reader-controls-pagination-next")
                .fetchSemanticsNodes().isNotEmpty() && !next.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
            if (hasNextPage) {
                next.performClick()
                pageCount += 1
            }
        }
        assertTrue("All measured controls for $project must finish within the page bound", pageCount < 48)
        assertEquals("Every complete choice must be reachable through the same group pages", expectedChoices.toSet(), visitedChoices)
        val returned = mutableSetOf<String>()
        repeat(48) {
            assertCurrentPanesFit()
            expected.filterTo(returned) { key ->
                composeRule.onNodeWithTag("settings-reader-control-item-$project-$key").isDisplayed()
            }
            val previous = composeRule.onNodeWithTag("settings-reader-controls-pagination-previous")
            if (composeRule.onAllNodesWithTag("settings-reader-controls-pagination-previous").fetchSemanticsNodes().isEmpty()) {
                assertEquals("Fitted groups must cover all complete controls without a page bar", visited, returned)
                return visited
            }
            if (previous.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) {
                assertEquals("Previous pages must cover the same complete controls", visited, returned)
                return visited
            }
            previous.assertIsDisplayed().performClick()
        }
        error("All measured controls for $project must return within the page bound")
    }

    private fun assertCurrentPanesFit() {
        assertTrue(composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isEmpty())
        assertTrue(
            composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
        )
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val projectPane = composeRule.onNodeWithTag("settings-reader-project-pager").fetchSemanticsNode().boundsInRoot
        val controlsPane = composeRule.onNodeWithTag("settings-reader-controls").fetchSemanticsNode().boundsInRoot
        assertTrue("The current project controls pane must be wider than project choices", controlsPane.width > projectPane.width)
        val minimumTarget = with(composeRule.density) { 48.dp.toPx() }
        val interactiveNodes = composeRule.onAllNodes(hasClickAction())
        interactiveNodes.fetchSemanticsNodes().forEachIndexed { index, node ->
            if (interactiveNodes[index].isDisplayed()) {
                assertTrue("Complete interactive widths must remain at least 48dp", node.boundsInRoot.width >= minimumTarget)
                assertTrue("Complete interactive heights must remain at least 48dp", node.boundsInRoot.height >= minimumTarget)
                assertTrue(node.boundsInRoot.top >= root.top && node.boundsInRoot.bottom <= root.bottom)
            }
        }
        assertTrue(projectPane.left >= root.left && projectPane.right <= root.right)
        assertTrue(controlsPane.left >= root.left && controlsPane.right <= root.right)

        val projectFooterTop = composeRule.onAllNodesWithTag("settings-reader-project-pagination-previous")
            .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top ?: projectPane.bottom
        listOf("first", "previous", "next", "last").forEach { direction ->
            composeRule.onAllNodesWithTag("settings-reader-project-pagination-$direction")
                .fetchSemanticsNodes().forEach { node ->
                    val bounds = node.boundsInRoot
                    assertTrue(bounds.left >= projectPane.left && bounds.right <= projectPane.right)
                }
        }
        val visibleProjects = projectKeys.flatMap { project ->
            composeRule.onAllNodesWithTag("settings-reader-project-$project").fetchSemanticsNodes()
        }.filter { project -> composeRule.onNodeWithTag(project.config[SemanticsProperties.TestTag]).isDisplayed() }
        assertTrue("At least the selected project choice must be visible", visibleProjects.isNotEmpty())
        visibleProjects.forEach { project ->
            assertTrue("Project buttons must remain above their page controls", project.boundsInRoot.bottom <= projectFooterTop)
        }
        visibleProjects.sortedBy { it.boundsInRoot.top }.zipWithNext().forEach { (previous, next) ->
            val gap = with(composeRule.density) { org.tsuyomi.core.ui.theme.TsuyomiSpacing.Sm.roundToPx() }
            assertTrue("Project navigation frames must retain measured vertical spacing",
                next.boundsInRoot.top - previous.boundsInRoot.bottom >= gap - 1f)
        }

        val controlsFooterTop = composeRule.onAllNodesWithTag("settings-reader-controls-pagination-previous")
            .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top ?: controlsPane.bottom
        listOf("first", "previous", "next", "last").forEach { direction ->
            composeRule.onAllNodesWithTag("settings-reader-controls-pagination-$direction")
                .fetchSemanticsNodes().forEach { node ->
                    val bounds = node.boundsInRoot
                    assertTrue(bounds.left >= controlsPane.left && bounds.right <= controlsPane.right)
                }
        }
        val controls = composeRule.onAllNodesWithTag("settings-reader-control-item")
        val visibleControls = controls.fetchSemanticsNodes().filterIndexed { index, _ -> controls[index].isDisplayed() }
        assertTrue("At least one measured setting item must be visible", visibleControls.isNotEmpty())
        visibleControls.forEach { item ->
            assertTrue(item.boundsInRoot.left >= controlsPane.left)
            assertTrue(item.boundsInRoot.right <= controlsPane.right)
            assertTrue("Control items must remain above their page controls", item.boundsInRoot.bottom <= controlsFooterTop)
        }
        visibleControls.sortedBy { it.boundsInRoot.top }.zipWithNext().forEach { (previous, next) ->
            val gap = with(composeRule.density) { org.tsuyomi.core.ui.theme.TsuyomiSpacing.Md.roundToPx() }
            assertTrue("Complete settings groups must retain measured inter-group spacing",
                next.boundsInRoot.top - previous.boundsInRoot.bottom >= gap - 1f)
        }
    }

    private fun displayEnvironment(profile: DisplayProfile) = DisplayEnvironment(
        preferences = DisplayPreferences(
            displayPreference = if (profile == DisplayProfile.EINK) DisplayPreference.EINK else DisplayPreference.STANDARD,
        ),
        effectiveProfile = profile,
        decisionReason = if (profile == DisplayProfile.EINK) {
            DisplayDecisionReason.MANUAL_EINK
        } else {
            DisplayDecisionReason.MANUAL_STANDARD
        },
        detectedDeviceLabel = null,
        dynamicColorEligible = false,
        dynamicColorEffective = false,
        effectiveDarkTheme = false,
        motionPolicy = if (profile == DisplayProfile.EINK) MotionPolicy.INSTANT else MotionPolicy.STANDARD,
        redrawEpoch = 0,
    )

    private companion object {
        val projectKeys = listOf("typography", "page", "navigation", "device")

        val controlItemKeys = mapOf(
            "typography" to listOf(
                "font-size",
                "line-height",
                "horizontal-margin",
                "paragraph-spacing",
                "font-family",
                "font-weight",
                "letter-spacing",
                "first-line-indent",
                "vertical-margin",
                "text-alignment",
                "foreground-color",
                "background-color",
            ),
            "page" to listOf(
                "flow",
                "flow-constraint",
                "theme",
                "theme-constraint",
                "effective-constraints",
            ),
            "navigation" to listOf("volume-paging", "progress", "immersive"),
            "device" to listOf("keep-awake", "lock-portrait"),
        )
    }
}
