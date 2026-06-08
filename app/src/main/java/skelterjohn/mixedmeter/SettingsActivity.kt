package skelterjohn.mixedmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
            val themeSetting by remember {
                dataStore.data
                    .map { preferences -> preferences[THEME_KEY] ?: DEFAULT_THEME }
            }.collectAsState(initial = DEFAULT_THEME)

            MixedMeterTheme(themeName = themeSetting) {
                val scope = rememberCoroutineScope()
                val theme = currentAppTheme()
                val beatToneSetting by remember {
                    dataStore.data
                        .map { preferences -> preferences[TONE_KEY] ?: DEFAULT_BEAT_TONE }
                }.collectAsState(initial = DEFAULT_BEAT_TONE)
                val leadToneSetting by remember {
                    dataStore.data
                        .map { preferences -> preferences[LEAD_TONE_KEY] ?: DEFAULT_LEAD_TONE }
                }.collectAsState(initial = DEFAULT_LEAD_TONE)
                val subdivisionToneSetting by remember {
                    dataStore.data
                        .map { preferences -> preferences[SUBDIVISION_TONE_KEY] ?: DEFAULT_SUBDIVISION_TONE }
                }.collectAsState(initial = DEFAULT_SUBDIVISION_TONE)

                Scaffold(
                    containerColor = theme.background,
                    contentWindowInsets = WindowInsets.statusBars,
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings", color = theme.text) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = theme.iconTint,
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = theme.background,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .background(theme.background)
                            .padding(innerPadding)
                            .navigationBarBottomPadding()
                            .padding(16.dp),
                    ) {
                        StringSettingDropdown(
                            label = "Lead tone",
                            options = TONE_OPTIONS,
                            selectedValue = leadToneSetting,
                            onSelect = { value ->
                                scope.launch {
                                    dataStore.edit { it[LEAD_TONE_KEY] = value }
                                }
                            },
                        )
                        StringSettingDropdown(
                            label = "Beat tone",
                            options = TONE_OPTIONS,
                            selectedValue = beatToneSetting,
                            onSelect = { value ->
                                scope.launch {
                                    dataStore.edit { it[TONE_KEY] = value }
                                }
                            },
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        StringSettingDropdown(
                            label = "Subdivision tone",
                            options = TONE_OPTIONS,
                            selectedValue = subdivisionToneSetting,
                            onSelect = { value ->
                                scope.launch {
                                    dataStore.edit { it[SUBDIVISION_TONE_KEY] = value }
                                }
                            },
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        StringSettingDropdown(
                            label = "Theme",
                            options = THEME_OPTIONS,
                            selectedValue = themeSetting,
                            onSelect = { value ->
                                scope.launch {
                                    dataStore.edit { it[THEME_KEY] = value }
                                }
                            },
                            optionSwatchColors = {
                                val optionTheme = appThemeForName(it)
                                listOf(
                                    optionTheme.background,
                                    optionTheme.buttonSurface,
                                    optionTheme.beatBoxInactive,
                                    optionTheme.embossSurface,
                                    optionTheme.dialogSurface,
                                )
                            },
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringSettingDropdown(
    label: String,
    options: List<String>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    optionSwatchColors: ((String) -> List<Color>)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = currentAppTheme()
    Text(label, color = theme.text, modifier = modifier.padding(bottom = 8.dp))

    var expanded by remember { mutableStateOf(false) }
    val displayValue = selectedValue.takeIf { it in options } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                focusedTextColor = DropdownMenuTextColor,
                unfocusedTextColor = DropdownMenuTextColor,
                disabledTextColor = DropdownMenuTextColor,
            ),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        val swatchColors = optionSwatchColors?.invoke(option).orEmpty()
                        if (swatchColors.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                swatchColors.forEach { swatchColor ->
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(swatchColor, RectangleShape)
                                            .border(1.dp, Color.Black, RectangleShape),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(option, color = DropdownMenuTextColor)
                            }
                        } else {
                            Text(option, color = DropdownMenuTextColor)
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
