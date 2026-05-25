package skelterjohn.mixedmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
                val beatToneSetting by remember {
                    dataStore.data
                        .map { preferences -> preferences[TONE_KEY] ?: DEFAULT_TONE }
                }.collectAsState(initial = DEFAULT_TONE)
                val leadToneSetting by remember {
                    dataStore.data
                        .map { preferences -> preferences[LEAD_TONE_KEY] ?: DEFAULT_TONE }
                }.collectAsState(initial = DEFAULT_TONE)

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
                        ToneSettingDropdown(
                            label = "Lead tone",
                            selectedValue = leadToneSetting,
                            onSelect = { value ->
                                scope.launch {
                                    dataStore.edit { it[LEAD_TONE_KEY] = value }
                                }
                            },
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        ToneSettingDropdown(
                            label = "Beat tone",
                            selectedValue = beatToneSetting,
                            onSelect = { value ->
                                scope.launch {
                                    dataStore.edit { it[TONE_KEY] = value }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToneSettingDropdown(
    label: String,
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(label, modifier = modifier.padding(bottom = 8.dp))

    var expanded by remember { mutableStateOf(false) }
    val displayValue = selectedValue.takeIf { it in TONE_OPTIONS } ?: TONE_OPTIONS.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        TextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TONE_OPTIONS.forEach { tone ->
                DropdownMenuItem(
                    text = { Text(tone) },
                    onClick = {
                        onSelect(tone)
                        expanded = false
                    }
                )
            }
        }
    }
}
