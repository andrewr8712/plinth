package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
    onStayAwake: (Boolean) -> Unit = {},
    onOpenConnection: () -> Unit = {},
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
        Text(
            text = listOfNotNull(
                output?.let { if (it.enabled) "Enabled" else "Disabled" },
                output?.let { if (it.dopAttribute) "DoP" else "PCM" },
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
                "output untouched; DSD travels as DoP, never as native DSD.",
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
