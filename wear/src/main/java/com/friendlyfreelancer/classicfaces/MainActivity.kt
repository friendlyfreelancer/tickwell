package com.friendlyfreelancer.classicfaces

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleToggle(intent)) return
        setContent { TickScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleToggle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Restarts the service if the system stopped it while enabled.
        TickSettings.syncService(this)
    }

    /** The tile opens this activity just to flip the setting. */
    private fun handleToggle(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_TOGGLE, false) != true) return false
        TickSettings.setEnabled(this, !TickSettings.isEnabled(this))
        finish()
        return true
    }

    companion object {
        const val EXTRA_TOGGLE = "toggle"
    }
}

@Composable
private fun TickScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TickSettings.isEnabled(context)) }
    var volume by remember { mutableIntStateOf(TickSettings.volume(context)) }
    var leadMs by remember { mutableIntStateOf(TickSettings.leadMs(context)) }
    val listState = rememberScalingLazyListState()

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item { ListHeader { Text(stringResource(R.string.app_name)) } }
                item { WatchFaceSection() }
                item { ListHeader { Text(stringResource(R.string.tick_header)) } }
                item {
                    ToggleChip(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            TickSettings.setEnabled(context, it)
                        },
                        label = { Text(stringResource(R.string.ticking)) },
                        toggleControl = { Switch(checked = enabled) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { Caption(stringResource(R.string.volume, volume)) }
                item {
                    StepSlider(volume, 1..TickSettings.VOLUME_STEPS) {
                        volume = it
                        TickSettings.setVolume(context, it)
                    }
                }
                item { Caption(stringResource(R.string.sync, leadMs)) }
                item {
                    StepSlider(
                        leadMs,
                        0..TickSettings.MAX_LEAD_MS step TickSettings.LEAD_STEP_MS,
                    ) {
                        leadMs = it
                        TickSettings.setLeadMs(context, it)
                    }
                }
                item { Caption(stringResource(R.string.help), small = true) }
            }
        }
    }
}

/** Shows whether the bundled face is in use, and offers to switch to it. */
@Composable
private fun WatchFaceSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<WatchFaceInstaller.State?>(null) }
    var canActivate by remember { mutableStateOf(WatchFaceInstaller.canActivate(context)) }

    suspend fun refresh() {
        state = WatchFaceInstaller.state(context)
        canActivate = WatchFaceInstaller.canActivate(context)
    }

    suspend fun activate() {
        WatchFaceInstaller.activate(context)
        refresh()
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { if (granted) activate() else refresh() }
    }

    LaunchedEffect(Unit) {
        WatchFaceInstaller.ensureInstalled(context)
        refresh()
    }

    when {
        state == null -> Unit
        state == WatchFaceInstaller.State.ACTIVE ->
            Caption(stringResource(R.string.face_active))
        state == WatchFaceInstaller.State.MISSING ->
            Caption(stringResource(R.string.face_missing))
        canActivate -> Chip(
            label = { Text(stringResource(R.string.face_use)) },
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, SET_ACTIVE) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) scope.launch { activate() } else permission.launch(SET_ACTIVE)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        else -> Caption(stringResource(R.string.face_manual))
    }
}

private const val SET_ACTIVE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

@Composable
private fun StepSlider(value: Int, range: IntProgression, onChange: (Int) -> Unit) {
    InlineSlider(
        value = value,
        onValueChange = onChange,
        valueProgression = range,
        decreaseIcon = { Icon(InlineSliderDefaults.Decrease, stringResource(R.string.less)) },
        increaseIcon = { Icon(InlineSliderDefaults.Increase, stringResource(R.string.more)) },
        segmented = false,
    )
}

@Composable
private fun Caption(text: String, small: Boolean = false) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = if (small) MaterialTheme.typography.caption3 else MaterialTheme.typography.caption1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
