package com.dshmobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dshmobile.data.PairingStore
import com.dshmobile.ui.PairingViewModel

/** Settings — grouped glass sections, 44dp targets, 16sp inputs. */
@Composable
fun SettingsScreen(
    pairingViewModel: PairingViewModel,
    store: PairingStore,
    onModelChange: (String) -> Unit,
) {
    val pairingState by pairingViewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("Settings", subtitle = "Server, model and connection")
        SectionLabel("Server")
        GlassCard {
            IosTextField(value = pairingState.serverUrl, onValueChange = pairingViewModel::onServerUrlChange, placeholder = "Server base URL")
            Text(
                text = if (pairingState.paired) "Paired" else "Not paired",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            IosButton(text = "Clear credentials", onClick = pairingViewModel::disconnect, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Clearing credentials only wipes this phone. Also revoke the device on the host (or use Disconnect on the Pair screen) so the host stops accepting it.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SectionLabel("Model")
        GlassCard {
            IosTextField(value = store.model.orEmpty(), onValueChange = { v -> store.model = v; onModelChange(v) }, placeholder = "Default model (optional)")
        }
        SectionLabel("Connection mode")
        GlassCard {
            Text("LAN: use the host's LAN address on the same network. Tunnel: use the tunnel URL the host plugin advertises. Cloudflare / fixed-relay tunnelling is out of scope for this app's v1 — use the host plugin's tunnel, or a manual tunnel you control, and keep pairing + revocation on.")
            Text(
                "A paired device is a full-control credential: it can drive the host agent. Only pair devices you control.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        VerticalSpacer(32)
    }
}
