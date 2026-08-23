/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.tsuyomi.prototype.uiatlas.components.AtlasIcons
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasNavigationActions
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasNavigation
import org.tsuyomi.prototype.uiatlas.model.AtlasReaderPresentationActions
import org.tsuyomi.prototype.uiatlas.model.LocalAtlasReaderPresentation
import org.tsuyomi.prototype.uiatlas.model.AtlasFamily
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasThemeKind
import org.tsuyomi.prototype.uiatlas.model.AtlasVariant
import org.tsuyomi.prototype.uiatlas.navigation.PrototypeRouteStackSaver
import org.tsuyomi.prototype.uiatlas.navigation.initialPrototypeStack
import org.tsuyomi.prototype.uiatlas.runtime.LocalPrototypeRuntime
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRuntime
import org.tsuyomi.prototype.uiatlas.review.ReviewPanel
import org.tsuyomi.prototype.uiatlas.review.ReviewNodeCatalog
import org.tsuyomi.prototype.uiatlas.screens.LibraryAtlasScreen
import org.tsuyomi.prototype.uiatlas.screens.MoreAtlasScreen
import org.tsuyomi.prototype.uiatlas.screens.SourceAtlasScreen
import org.tsuyomi.prototype.uiatlas.theme.AtlasEnvironment
import org.tsuyomi.prototype.uiatlas.theme.AtlasTheme
import org.tsuyomi.prototype.uiatlas.theme.AtlasMotion

/**
 * Atlas shell. Renders the current [AtlasContext] through the family dispatch and — outside
 * capture mode — adds the product-faithful three-root navigator.
 *
 * Family composable contract (implemented by the route-family slices):
 *
 * ```
 * @Composable fun LibraryAtlasScreen(context: AtlasContext, modifier: Modifier = Modifier)
 * @Composable fun SourceAtlasScreen(context: AtlasContext, modifier: Modifier = Modifier)
 * @Composable fun MoreAtlasScreen(context: AtlasContext, modifier: Modifier = Modifier)
 * ```
 *
 * Each family screen composes its own [org.tsuyomi.prototype.uiatlas.components.AtlasScaffold]
 * with an [org.tsuyomi.prototype.uiatlas.components.AtlasTopBar]. The immutable context is the
 * complete launch configuration; capture mode renders only the requested route surface.
 */
@Composable
fun AtlasApp(
    initial: AtlasContext,
    runtime: PrototypeRuntime,
    onReaderImmersiveChanged: (Boolean) -> Unit = {},
    onExportReview: (Boolean) -> Unit = {},
    onShareReview: (Boolean) -> Unit = {},
) {
    var currentRootName by rememberSaveable { mutableStateOf(initial.route.family.name) }
    var libraryStack by rememberSaveable(stateSaver = PrototypeRouteStackSaver) {
        mutableStateOf(initialPrototypeStack(initial.route, AtlasFamily.LIBRARY))
    }
    var sourceStack by rememberSaveable(stateSaver = PrototypeRouteStackSaver) {
        mutableStateOf(initialPrototypeStack(initial.route, AtlasFamily.SOURCE))
    }
    var moreStack by rememberSaveable(stateSaver = PrototypeRouteStackSaver) {
        mutableStateOf(initialPrototypeStack(initial.route, AtlasFamily.MORE))
    }
    var stateName by rememberSaveable { mutableStateOf(initial.state.name) }
    var profileName by rememberSaveable { mutableStateOf(initial.profile.name) }
    var themeName by rememberSaveable { mutableStateOf(initial.theme.name) }
    var layoutName by rememberSaveable { mutableStateOf(initial.layout?.name.orEmpty()) }
    var viewName by rememberSaveable {
        mutableStateOf(
            if (runtime.persistent) runtime.repository.string("library.view", initial.libraryView.name)
            else initial.libraryView.name,
        )
    }
    var reducedMotion by rememberSaveable { mutableStateOf(initial.reducedMotion) }
    var variantRaw by rememberSaveable { mutableStateOf(initial.variant?.toString().orEmpty()) }
    var readerChromeVisible by rememberSaveable {
        mutableStateOf(
            initial.route == AtlasRoute.BOOK_READER &&
                (initial.primaryState != AtlasPageState.CONTENT || initial.showOfflineBanner || initial.showModal ||
                    initial.libraryView == AtlasLibraryView.READ_LATER),
        )
    }
    var selectedSearchSourceId by rememberSaveable { mutableStateOf(initial.selectedSearchSourceId) }
    var readerImmersive by rememberSaveable { mutableStateOf(initial.readerImmersive) }

    var reviewOpen by rememberSaveable { mutableStateOf(false) }
    val currentRoot = AtlasFamily.valueOf(currentRootName)
    fun currentStack(): List<AtlasRoute> = when (currentRoot) {
        AtlasFamily.LIBRARY -> libraryStack
        AtlasFamily.SOURCE -> sourceStack
        AtlasFamily.MORE -> moreStack
    }
    fun setStack(family: AtlasFamily, value: List<AtlasRoute>) {
        when (family) {
            AtlasFamily.LIBRARY -> libraryStack = value
            AtlasFamily.SOURCE -> sourceStack = value
            AtlasFamily.MORE -> moreStack = value
        }
    }
    fun push(route: AtlasRoute) {
        val stack = currentStack()
        if (stack.last() != route) setStack(currentRoot, stack + route)
    }
    fun pushSearch(sourceId: String?) {
        selectedSearchSourceId = sourceId
        push(AtlasRoute.SEARCH)
    }
    fun pop() {
        val stack = currentStack()
        if (stack.size > 1) setStack(currentRoot, stack.dropLast(1))
    }
    fun selectRoot(family: AtlasFamily) {
        // Root selection is an explicit reset. Re-selecting 书架 always returns to 全部书籍.
        currentRootName = family.name
        setStack(family, listOf(AtlasRoute.rootOf(family)))
        if (family == AtlasFamily.LIBRARY) {
            viewName = AtlasLibraryView.ALL.name
            if (runtime.persistent) runtime.repository.putString("library.view", viewName, "LibraryRootSelected")
        }
    }
    fun navigateInRoot(family: AtlasFamily, route: AtlasRoute) {
        currentRootName = family.name
        val stack = when (family) {
            AtlasFamily.LIBRARY -> libraryStack
            AtlasFamily.SOURCE -> sourceStack
            AtlasFamily.MORE -> moreStack
        }
        setStack(family, if (stack.last() == route) stack else stack + route)
    }
    fun selectLibraryView(view: AtlasLibraryView) {
        viewName = view.name
    }

    val route = currentStack().last()
    val context = AtlasContext(
        route = route,
        state = AtlasPageState.valueOf(stateName),
        profile = AtlasProfile.valueOf(profileName),
        theme = AtlasThemeKind.valueOf(themeName),
        variant = AtlasVariant.parse(variantRaw.ifEmpty { null }),
        layout = layoutName.ifEmpty { null }?.let(AtlasLayout::valueOf),
        libraryView = AtlasLibraryView.valueOf(viewName),
        selectedSearchSourceId = selectedSearchSourceId,
        reducedMotion = reducedMotion,
        tutorial = initial.tutorial,
        capture = initial.capture,
        inlineModalPreview = initial.inlineModalPreview,
        simulateSystemUi = initial.simulateSystemUi,
        readerImmersive = readerImmersive,
        review = initial.review,
        readerSeekPreview = initial.readerSeekPreview,
    )
    val environment = AtlasEnvironment(
        profile = context.profile,
        theme = context.theme,
        reducedMotion = context.reducedMotion,
        stateArt = when {
            context.variant?.id != 'G' -> org.tsuyomi.prototype.uiatlas.theme.AtlasStateArt.TYPOGRAPHIC
            context.variant.option == "b" -> org.tsuyomi.prototype.uiatlas.theme.AtlasStateArt.EMOTICON
            else -> org.tsuyomi.prototype.uiatlas.theme.AtlasStateArt.TYPOGRAPHIC
        },
    )
    val navigation = AtlasNavigationActions(::push, ::pushSearch, ::navigateInRoot, ::pop, ::selectLibraryView, ::selectRoot)
    val readerPresentation = AtlasReaderPresentationActions(
        setImmersive = { readerImmersive = it },
        setChromeVisible = { readerChromeVisible = it },
    )
    val readerImmersiveActive = route == AtlasRoute.BOOK_READER && readerImmersive && !readerChromeVisible
    SideEffect { onReaderImmersiveChanged(readerImmersiveActive) }
    LaunchedEffect(route) {
        reviewOpen = false
    }
    LaunchedEffect(context.route, context.libraryView, context.state, context.readerSeekPreview) {
        runtime.reviews.markVisited(ReviewNodeCatalog.resolve(context).id)
    }

    BackHandler(enabled = currentStack().size > 1) { pop() }

    AtlasTheme(environment = environment) {
        CompositionLocalProvider(
            LocalAtlasNavigation provides navigation,
            LocalAtlasReaderPresentation provides readerPresentation,
            LocalPrototypeRuntime provides runtime,
        ) {
            AtlasSystemViewport(
                simulate = context.simulateSystemUi,
                immersive = readerImmersiveActive,
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (initial.capture) {
                        AtlasDispatch(context, Modifier.fillMaxSize())
                    } else if (route == AtlasRoute.BOOK_READER || context.instantMotion) {
                        AtlasDispatch(context, Modifier.fillMaxSize())
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = route,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                                transitionSpec = {
                                    fadeIn(tween(AtlasMotion.FADE_IN_MS)) togetherWith fadeOut(tween(AtlasMotion.FADE_OUT_MS))
                                },
                                label = "AtlasRouteTransition",
                            ) { displayedRoute ->
                                AtlasDispatch(context.copy(route = displayedRoute), Modifier.fillMaxSize())
                            }
                            AtlasNavigator(
                                current = currentRoot,
                                reviewOpen = reviewOpen,
                                onSelectFamily = ::selectRoot,
                                onOpenReview = { reviewOpen = true; readerChromeVisible = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (route == AtlasRoute.BOOK_READER && !initial.capture && !readerImmersiveActive && !reviewOpen) {
                        SmallFloatingActionButton(
                            onClick = { reviewOpen = true; readerChromeVisible = true },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                .padding(start = 8.dp, bottom = 8.dp)
                                .size(40.dp),
                        ) {
                            Icon(
                                imageVector = AtlasIcons.Edit,
                                contentDescription = "审阅",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (reviewOpen) {
                        ReviewPanel(
                            context = context,
                            runtime = runtime,
                            onDismiss = { reviewOpen = false },
                            onExport = onExportReview,
                            onShare = onShareReview,
                        )
                    }
                }
            }
        }
    }
}

/** Deterministic screenshot viewport: visible system bars by default, Reader-only immersive opt-in. */
@Composable
private fun AtlasSystemViewport(
    simulate: Boolean,
    immersive: Boolean,
    content: @Composable () -> Unit,
) {
    if (!simulate) {
        content()
        return
    }
    val surface = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    if (immersive) {
        Box(Modifier.fillMaxSize().background(surface)) {
            Box(Modifier.fillMaxSize().padding(top = 32.dp)) { content() }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 5.dp)
                    .size(18.dp)
                    .background(Color.Black, CircleShape),
            )
        }
    } else {
        Column(Modifier.fillMaxSize().background(surface)) {
            AtlasSimulatedStatusBar(surface, ink)
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            AtlasSimulatedNavigationBar(surface, ink)
        }
    }
}

@Composable
private fun AtlasSimulatedStatusBar(surface: Color, ink: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(surface)
            .padding(horizontal = 14.dp),
    ) {
        Text("9:41", modifier = Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.labelMedium, color = ink)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 5.dp)
                .size(18.dp)
                .background(Color.Black, CircleShape),
        )
        Text("▮▮  ᯤ  ▰", modifier = Modifier.align(Alignment.CenterEnd), style = MaterialTheme.typography.labelSmall, color = ink)
    }
}

@Composable
private fun AtlasSimulatedNavigationBar(surface: Color, ink: Color) {
    Box(Modifier.fillMaxWidth().height(24.dp).background(surface)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 108.dp, height = 4.dp)
                .background(ink, CircleShape),
        )
    }
}

/** Route-family dispatch: the only place family composables are invoked. */
@Composable
private fun AtlasDispatch(context: AtlasContext, modifier: Modifier = Modifier) {
    when (context.route) {
        // Canonical book surfaces are owned by the Source family screen even though
        // AtlasRoute.family keeps them in LIBRARY for root/navigator semantics.
        AtlasRoute.BOOK_DETAIL,
        AtlasRoute.BOOK_READER,
        -> SourceAtlasScreen(context, modifier)
        else -> when (context.route.family) {
            AtlasFamily.LIBRARY -> LibraryAtlasScreen(context, modifier)
            AtlasFamily.SOURCE -> SourceAtlasScreen(context, modifier)
            AtlasFamily.MORE -> MoreAtlasScreen(context, modifier)
        }
    }
}

/** Product roots plus one prototype-only review utility in the existing navigation bar. */
@Composable
private fun AtlasNavigator(
    current: AtlasFamily,
    reviewOpen: Boolean,
    onSelectFamily: (AtlasFamily) -> Unit,
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth().testTag("atlas-bottom-navigation"),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) {
        NavigationBarItem(
            selected = current == AtlasFamily.LIBRARY,
            onClick = { onSelectFamily(AtlasFamily.LIBRARY) },
            icon = { Icon(AtlasIcons.Shelf, contentDescription = null) },
            label = { Text(AtlasStrings.ROOT_LIBRARY) },
        )
        NavigationBarItem(
            selected = current == AtlasFamily.SOURCE,
            onClick = { onSelectFamily(AtlasFamily.SOURCE) },
            icon = { Icon(AtlasIcons.Compass, contentDescription = null) },
            label = { Text(AtlasStrings.ROOT_BROWSE) },
        )
        NavigationBarItem(
            selected = current == AtlasFamily.MORE,
            onClick = { onSelectFamily(AtlasFamily.MORE) },
            icon = { Icon(AtlasIcons.More, contentDescription = null) },
            label = { Text(AtlasStrings.ROOT_MORE) },
        )
        NavigationBarItem(
            selected = reviewOpen,
            onClick = onOpenReview,
            icon = { Icon(AtlasIcons.Edit, contentDescription = null) },
            label = { Text("审阅") },
        )
    }
}
