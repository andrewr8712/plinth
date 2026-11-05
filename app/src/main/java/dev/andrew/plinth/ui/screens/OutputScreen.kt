package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.andrew.plinth.data.model.OutputInfo
import dev.andrew.plinth.data.settings.Settings
import dev.andrew.plinth.ui.theme.PlinthColors

/**
 * The output, and the honest note about what this app cannot do. There is no mixer on
 * this streamer, so there is no volume control anywhere in Plinth — the DAC's knob and
 * the amplifier are the only gain stages.
 */
@Composable
fun OutputScreen(
    output: OutputInfo?,
    settings: Settings,
    stateLabel: String,
    isDsd: Boolean = false,
    levelTracks: Int = 0,
    levelStatus: String? = null,
    libraryTracks: Int = 0,
    levelStale: Boolean = false,
    onStayAwake: (Boolean) -> Unit = {},
    onOpenConnection: () -> Unit = {},
    onImportLoudness: () -> Unit = {},
    onMeterOffset: (dsd: Boolean, ms: Int) -> Unit = { _, _ -> },
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PlinthColors.Base)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = "Output",
            color = PlinthColors.Text,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        )

        SectionLabel("Destination")
        Text(
            text = output?.name ?: "No output reported",
            color = PlinthColors.Text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        val transport = when {
            output?.dopAttribute == true -> "DoP"
            isDsd -> "Native DSD"
            else -> "PCM"
        }
        Text(
            text = listOfNotNull(
                output?.let { if (it.enabled) "Enabled" else "Disabled" },
                transport,
                stateLabel,
            ).joinToString(" · "),
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
        )

        SectionLabel("Signal")
        Text(
            text = "Plinth asks the streamer for the file and nothing else. When the strip " +
                "says bit-perfect, MPD decoded the file at its own rate and sent it to the " +
                "output untouched. " + when {
                    output?.dopAttribute == true ->
                        "DSD travels as DoP; the DAC reconstructs it."
                    isDsd ->
                        "DSD travels as native DSD on this output."
                    else ->
                        "PCM is sent to the output untouched."
                },
            color = PlinthColors.Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        SectionLabel("Volume")
        Text(
            text = "Volume is the DAC's knob; this streamer has no mixer.",
            color = PlinthColors.Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp).testTag("no-mixer-note"),
        )

        SectionLabel("Server")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("server-row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${settings.host}:${settings.port}",
                color = PlinthColors.Text,
                style = MaterialTheme.typography.bodyLarge,
            )
            TextAction("Change", onClick = onOpenConnection, testTag = "open-connection")
        }

        SectionLabel("Screen")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Stay awake while charging",
                    color = PlinthColors.Text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Keeps the tablet lit on its stand once the charger is attached.",
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = settings.stayAwakeWhileCharging,
                onCheckedChange = onStayAwake,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PlinthColors.Base,
                    checkedTrackColor = PlinthColors.Accent,
                    uncheckedThumbColor = PlinthColors.Muted,
                    uncheckedTrackColor = PlinthColors.Plate,
                ),
                modifier = Modifier.testTag("stay-awake"),
            )
        }

        SectionLabel("Loudness data")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("loudness-row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        levelStale -> "Loudness data is older than the library"
                        levelTracks > 0 -> "$levelTracks tracks with levels"
                        else -> "No loudness data"
                    },
                    color = PlinthColors.Text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    // Say what the file covers, not just that it exists: a stale MPD path
                    // leaves some tracks without a level, and that should be visible here
                    // rather than discovered at the meter.
                    text = levelStatus ?: when {
                        levelStale -> "Rebuild it with tools/vu-scan; the library changed since the scan."
                        levelTracks == 0 -> "The meter needs a loudness file built by tools/vu-scan."
                        libraryTracks > 0 -> "of $libraryTracks in the library; the rest show no level data."
                        else -> "Imported envelope"
                    },
                    color = PlinthColors.Faint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextAction("Import", onClick = onImportLoudness, testTag = "import-loudness")
        }

        MeterOffsetRow(
            label = "Meter sync · PCM",
            valueMs = settings.meterOffsetPcmMs,
            onChange = { onMeterOffset(false, it) },
            testTag = "meter-offset-pcm",
        )
        MeterOffsetRow(
            label = "Meter sync · DSD",
            valueMs = settings.meterOffsetDsdMs,
            onChange = { onMeterOffset(true, it) },
            testTag = "meter-offset-dsd",
        )
        Text(
            text = "The meter runs behind the streamer by its buffer and the DAC's latency. " +
                "Raise the offset if the bars lead what you hear.",
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
        )
    }
}

@Composable
private fun MeterOffsetRow(
    label: String,
    valueMs: Int,
    onChange: (Int) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PlinthColors.Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(150.dp),
        )
        Slider(
            value = valueMs.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = -500f..500f,
            modifier = Modifier.weight(1f).testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = PlinthColors.Accent,
                activeTrackColor = PlinthColors.Accent,
                inactiveTrackColor = PlinthColors.Plate,
            ),
        )
        Text(
            text = "$valueMs ms",
            color = PlinthColors.Faint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = PlinthColors.Faint,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}
