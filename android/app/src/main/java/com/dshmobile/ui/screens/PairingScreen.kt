package com.dshmobile.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dshmobile.ui.PairingViewModel

/**
 * Pairing screen — latest-iOS language: large title, grouped glass
 * cards, 44dp+ targets, 16sp inputs. v1 input is PASTE (not QR scan):
 * paste the pairing link/token shown on the host's "Remote access"
 * panel. QR scanning is a roadmap item, labelled as such.
 * The stored device credential is never displayed here.
 */
@Composable
fun PairingScreen(viewModel: PairingViewModel) {
    val state by viewModel.state.collectAsState()
    var token by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        LargeTitle("Pair device", subtitle = "Connect this phone to your dsh host")
        SectionLabel("Host")
        GlassCard {
            Text("Server base URL", style = MaterialTheme.typography.labelLarge)
            IosTextField(
                value = state.serverUrl, onValueChange = viewModel::onServerUrlChange,
                placeholder = "https://your-host.example or http://192.168.x.x:3080",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Use the LAN address on your own network, or the tunnel URL your host plugin advertises. Never expose dsh directly to the public internet without pairing + revocation.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SectionLabel("Pairing")
        GlassCard {
            Text("Pairing token (paste)", style = MaterialTheme.typography.labelLarge)
            IosTextField(value = token, onValueChange = { token = it }, placeholder = "Paste the token from the host panel", modifier = Modifier.padding(top = 8.dp))
            Text("Device name", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            IosTextField(value = deviceName, onValueChange = { deviceName = it }, placeholder = "e.g. My Android phone", modifier = Modifier.padding(top = 8.dp))
            Row(modifier = Modifier.padding(top = 16.dp)) {
                IosButton(text = if (state.paired) "Re-pair" else "Pair", onClick = { viewModel.pair(token, deviceName) }, enabled = !state.busy)
            }
            if (state.paired) {
                OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Disconnect / revoke this device")
                }
            }
            Text(
                text = if (state.paired) "Status: paired" else "Status: not paired",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            state.message?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
        SectionLabel("QR scan")
        GlassCard {
            Text("QR scanning is not in v1. Paste the token above instead. The host panel still shows a QR code — the token inside it is what you paste.")
        }
        VerticalSpacer(32)
    }
}
