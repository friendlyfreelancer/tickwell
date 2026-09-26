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
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) {
                    darkColorScheme(primary = Color(0xFFD6B26A))
                } else {
                    lightColorScheme(primary = Color(0xFF1D3884))
                },
            ) {
                Surface(Modifier.fillMaxSize()) { CompanionScreen() }
            }
        }
    }
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        full {
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
            }
        }
        full { WatchCard() }
        full { Header(R.string.styles_header) }
        items(STYLES) { style ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(style.image),
                    contentDescription = stringResource(style.name),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(style.name), style = MaterialTheme.typography.titleMedium)
            }
        }
        full { Header(R.string.setup_header) }
        full {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.setup_1))
                Text(stringResource(R.string.setup_2))
                Text(stringResource(R.string.setup_3))
            }
        }
        full { Header(R.string.tick_header) }
        full { Text(stringResource(R.string.tick_body)) }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Header(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
}

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

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.watch_header), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    when (state) {
                        WatchState.Checking -> R.string.watch_checking
                        WatchState.NoWatch -> R.string.watch_none
                        WatchState.Missing -> R.string.watch_missing
                        is WatchState.Installed -> R.string.watch_ready
                    }
                )
            )
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
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
            Text(
                stringResource(R.string.requirement),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Declared by the watch app in res/values/wear.xml. */
private const val WATCH_CAPABILITY = "classicfaces_watch"
/** Handled by the watch app's PhoneMessageService. */
private const val OPEN_PATH = "/classicfaces/open"
