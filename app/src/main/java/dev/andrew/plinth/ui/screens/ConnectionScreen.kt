package dev.andrew.plinth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrew.plinth.data.mpd.ConnectionState

/** Ports MPD can listen on. Anything else is a typo, not an intention. */
fun parsePort(text: String): Int? {
    val value = text.trim()
    if (value.isEmpty() || value.length > 5) return null
    return value.toIntOrNull()?.takeIf { it in 1..65535 }
}

/**
 * Where a fresh install starts, and where the app goes when the streamer's address
 * changes. No auth: this MPD accepts connections from the LAN without a password.
 */
@Composable
fun ConnectionScreen(
    initialHost: String,
    initialState: ConnectionState,
    initialPort: Int = 6600,
    onConnect: (host: String, port: Int) -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf(initialPort.toString()) }
    val port = parsePort(portText)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Plinth", color = Color(0xFFF6F5F3), fontSize = 40.sp)
        Text(
            "Connect to your streamer",
            color = Color(0xFFF6F5F3).copy(alpha = 0.62f),
            fontSize = 16.sp,
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it },
            label = { Text("Port") },
            singleLine = true,
            isError = port == null,
            supportingText = { if (port == null) Text("1–65535") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Button(
            onClick = { port?.let { onConnect(host.trim(), it) } },
            enabled = port != null && host.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7ED9C3)),
        ) {
            Text("Connect", color = Color(0xFF0A0A0C))
        }

        Text(
            text = when (initialState) {
                is ConnectionState.Up -> "Connected"
                is ConnectionState.Connecting -> "Connecting…"
                is ConnectionState.Unavailable -> "Unreachable: ${initialState.message}"
            },
            color = Color(0xFFF6F5F3).copy(alpha = 0.52f),
            fontSize = 13.sp,
        )
    }
}
