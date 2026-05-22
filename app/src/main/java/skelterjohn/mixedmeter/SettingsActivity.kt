package skelterjohn.mixedmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MixedMeterTheme {
                val scope = rememberCoroutineScope()
                val toneSetting by remember {
                    dataStore.data
                        .map { preferences -> preferences[TONE_KEY] ?: "bip" }
                }.collectAsState(initial = "bip")

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        Text("Metronome Tone", modifier = Modifier.padding(bottom = 8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = toneSetting == "bip",
                                onClick = {
                                    scope.launch {
                                        dataStore.edit { it[TONE_KEY] = "bip" }
                                    }
                                }
                            )
                            Text("Bip (Soft)", modifier = Modifier.padding(start = 8.dp))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = toneSetting == "beep",
                                onClick = {
                                    scope.launch {
                                        dataStore.edit { it[TONE_KEY] = "beep" }
                                    }
                                }
                            )
                            Text("Beep (High)", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
