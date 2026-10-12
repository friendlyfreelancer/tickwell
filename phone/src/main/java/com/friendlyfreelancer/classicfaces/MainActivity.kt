package com.friendlyfreelancer.classicfaces

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = tickwellColors(isSystemInDarkTheme())) {
                Surface(Modifier.fillMaxSize()) { CompanionScreen() }
            }
        }
    }
}

/** Warm watchmaker tones: gold on near-black at night, navy on ivory by day. Follows the device theme. */
private fun tickwellColors(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFFD6B26A),
        onPrimary = Color(0xFF2A2006),
        primaryContainer = Color(0xFF3A3020),
        onPrimaryContainer = Color(0xFFF3DFB0),
        background = Color(0xFF0E0F13),
        surface = Color(0xFF0E0F13),
        surfaceContainer = Color(0xFF181A20),
        surfaceContainerHigh = Color(0xFF20232B),
        onSurface = Color(0xFFECE7DD),
        onSurfaceVariant = Color(0xFFA9A397),
        outline = Color(0xFF3A3D46),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF1D3884),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDE3F5),
        onPrimaryContainer = Color(0xFF0E1D4A),
        background = Color(0xFFF7F4EE),
        surface = Color(0xFFF7F4EE),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFEFEBE3),
        onSurface = Color(0xFF1B1C20),
        onSurfaceVariant = Color(0xFF5E5A52),
        outline = Color(0xFFD9D3C7),
    )
}

private class Style(@StringRes val name: Int, @DrawableRes val image: Int)

private val STYLES = listOf(
    Style(R.string.style_heritage, R.drawable.gallery_heritage),
    Style(R.string.style_railroad, R.drawable.gallery_railroad),
    Style(R.string.style_pilot, R.drawable.gallery_pilot),
    Style(R.string.style_dress, R.drawable.gallery_dress),
    Style(R.string.style_quartz, R.drawable.gallery_quartz),
    Style(R.string.style_vintage, R.drawable.gallery_vintage),
    Style(R.string.style_field, R.drawable.gallery_field),
    Style(R.string.style_chrono, R.drawable.gallery_chrono),
    Style(R.string.style_diver, R.drawable.gallery_diver),
)

@Composable
private fun CompanionScreen() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            // A soft glow of the accent colour behind the hero watch.
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.maxDimension * 0.6f,
                    )
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            // On an unfolded or tablet screen, keep a readable column in the middle.
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            section(0) { Hero() }
            section(1) { WatchCard() }
            section(2, edgeToEdge = true) { StylesCarousel() }
            section(3) { SetupSteps() }
            section(4) { TickCard() }
        }
    }
}

/**
 * Each section fades and rises into place on first launch, one after another.
 * [edgeToEdge] sections handle their own side margins (the carousel runs to the edges).
 */
private fun LazyListScope.section(order: Int, edgeToEdge: Boolean = false, content: @Composable () -> Unit) = item {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = shown,
        modifier = if (edgeToEdge) Modifier else Modifier.padding(horizontal = 20.dp),
        enter = fadeIn(tween(500, delayMillis = order * 90)) +
            slideInVertically(tween(500, delayMillis = order * 90)) { it / 6 },
    ) { content() }
}

@Composable
private fun Hero() {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        LiveWatch(
            dial = if (dark) Color(0xFF15171C) else Color(0xFFFBF8F2),
            rim = if (dark) Color(0xFFB8975A) else Color(0xFF9AA3B8),
            marks = colors.onSurface,
            hands = colors.onSurface,
            accent = colors.primary,
            modifier = Modifier.size(200.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int, @StringRes hint: Int? = null) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(stringResource(text), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Rounded panel used by every card on the screen. */
@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(22.dp)
    ) { content() }
}

// ─── Styles ──────────────────────────────────────────────────────────────

private val DIAL_SIZE = 220.dp

@Composable
private fun StylesCarousel() {
    val pager = rememberPagerState { STYLES.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()

    // Turns the dial on by itself; touching the carousel pauses it.
    LaunchedEffect(dragged) {
        if (dragged) return@LaunchedEffect
        while (true) {
            delay(3500)
            pager.animateScrollToPage((pager.currentPage + 1) % STYLES.size, animationSpec = tween(700))
        }
    }

    Column {
        Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle(R.string.styles_header, R.string.styles_hint) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Centres the current dial whatever the screen width; neighbours peek in at the sides.
            val side = ((maxWidth - DIAL_SIZE) / 2).coerceAtLeast(0.dp)
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(DIAL_SIZE),
                contentPadding = PaddingValues(horizontal = side),
                pageSpacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
                    .coerceIn(0f, 1f)
                Image(
                    painter = painterResource(STYLES[page].image),
                    contentDescription = stringResource(STYLES[page].name),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .graphicsLayer {
                            val s = lerp(1f, 0.78f, distance)
                            scaleX = s
                            scaleY = s
                            alpha = lerp(1f, 0.45f, distance)
                        },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            targetState = pager.currentPage,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically { -it / 2 })
            },
            label = "styleName",
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { page ->
            Text(
                stringResource(STYLES[page].name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            repeat(STYLES.size) { i ->
                val selected = i == pager.currentPage
                val width by animateDpAsState(if (selected) 20.dp else 6.dp, label = "dot")
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    label = "dotColor",
                )
                Box(Modifier.height(6.dp).width(width).clip(CircleShape).background(color))
            }
        }
    }
}

// ─── Setup and tick sound ────────────────────────────────────────────────

@Composable
private fun SetupSteps() {
    Column {
        SectionTitle(R.string.setup_header)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(R.string.setup_1, R.string.setup_2, R.string.setup_3).forEachIndexed { i, step ->
                Panel {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${i + 1}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(step),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TickCard() {
    Column {
        SectionTitle(R.string.tick_header)
        Panel {
            Row(verticalAlignment = Alignment.Top) {
                TickPulse(Modifier.padding(top = 6.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.tick_body), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** A dot that beats once a second, like the tick it describes. */
@Composable
private fun TickPulse(modifier: Modifier = Modifier) {
    val beat = rememberInfiniteTransition(label = "tick")
    val ring by beat.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Restart),
        label = "ring",
    )
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(20.dp)
                .scale(0.5f + ring * 0.5f)
                .graphicsLayer { alpha = 1f - ring }
                .clip(CircleShape)
                .background(accent)
        )
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
    }
}

// ─── Watch connection ────────────────────────────────────────────────────

private sealed interface WatchState {
    data object Checking : WatchState
    data object NoWatch : WatchState
    data object Missing : WatchState
    data class Installed(val nodeIds: List<String>) : WatchState
}

/** Finds the paired watch, and whether the watch app is on it. */
private suspend fun findWatch(context: Context): WatchState = try {
    val connected = Wearable.getNodeClient(context).connectedNodes.await()
    val withApp = Wearable.getCapabilityClient(context)
        .getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        .await().nodes.map { it.id }
    when {
        withApp.isNotEmpty() -> WatchState.Installed(withApp)
        connected.isNotEmpty() -> WatchState.Missing
        else -> WatchState.NoWatch
    }
} catch (e: Exception) {
    // No Wear OS companion on this phone, so no watch to talk to.
    WatchState.NoWatch
}

/**
 * Asks the watch app to open itself; true if any watch got the message.
 * A Data Layer message rather than RemoteActivityHelper, which needs Google's
 * Wear OS phone app: Galaxy Watches pair through Galaxy Wearable instead.
 */
private suspend fun openOnWatch(context: Context, nodeIds: List<String>): Boolean {
    val messages = Wearable.getMessageClient(context)
    return nodeIds.map { node ->
        runCatching { messages.sendMessage(node, OPEN_PATH, null).await() }.isSuccess
    }.any { it }
}

/**
 * Opens this app's Play listing on the phone. Play offers the paired watch in
 * its install options, which works with any phone and watch pairing.
 */
private fun openPlayListing(context: Context): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
    )
}.isSuccess
@Composable
private fun WatchCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<WatchState>(WatchState.Checking) }
    var message by remember { mutableStateOf<Int?>(null) }
    var checks by remember { mutableIntStateOf(0) }

    LaunchedEffect(checks) {
        state = WatchState.Checking
        state = findWatch(context)
    }

    fun open(nodeIds: List<String>) = scope.launch {
        message = if (openOnWatch(context, nodeIds)) R.string.check_watch
        else R.string.send_failed
    }

    val statusColor by animateColorAsState(
        when (state) {
            is WatchState.Installed -> Color(0xFF5BC27A)
            WatchState.Missing -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "status",
    )

    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state == WatchState.Checking) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.watch_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                contentKey = { it::class },
                label = "watchState",
            ) { s ->
                Text(
                    stringResource(
                        when (s) {
                            WatchState.Checking -> R.string.watch_checking
                            WatchState.NoWatch -> R.string.watch_none
                            WatchState.Missing -> R.string.watch_missing
                            is WatchState.Installed -> R.string.watch_ready
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = state) {
                    WatchState.Missing -> Button(onClick = {
                        message = if (openPlayListing(context)) R.string.choose_watch
                        else R.string.no_play_store
                    }) {
                        Text(stringResource(R.string.install_on_watch))
                    }
                    is WatchState.Installed -> Button(onClick = { open(s.nodeIds) }) {
                        Text(stringResource(R.string.open_on_watch))
                    }
                    else -> Unit
                }
                if (state != WatchState.Checking) {
                    OutlinedButton(onClick = { message = null; checks++ }) {
                        Text(stringResource(R.string.refresh))
                    }
                }
            }
            AnimatedVisibility(visible = message != null) {
                message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
            }
            Text(
                stringResource(R.string.requirement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Declared by the watch app in res/values/wear.xml. */
private const val WATCH_CAPABILITY = "classicfaces_watch"
/** Handled by the watch app's PhoneMessageService. */
private const val OPEN_PATH = "/classicfaces/open"
