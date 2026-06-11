package skelterjohn.mixedmeter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme

private const val DISCORD_INVITE_URL = "https://discord.gg/E4XarYpwK"
private const val GITHUB_REPO_URL = "https://github.com/skelterjohn/mixedmeter"
private const val CREATOR_EMAIL = "jasmuth@gmail.com"
private const val CREATOR_EMAIL_SUBJECT = "About Mixed Meter Metronome..."

@Composable
private fun InfoFeature(
    header: String,
    body: String,
    textColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = header,
            color = textColor,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = textColor,
        )
    }
}

@Composable
private fun InfoCollection(
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Email the creator",
            color = textColor,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                val mailUri = Uri.parse(
                    "mailto:$CREATOR_EMAIL?subject=${Uri.encode(CREATOR_EMAIL_SUBJECT)}",
                )
                context.startActivity(Intent(Intent.ACTION_SENDTO, mailUri))
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Discord",
                color = textColor,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_INVITE_URL)),
                    )
                },
            )
            Text(
                text = "GitHub",
                color = textColor,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)),
                    )
                },
            )
        }
        InfoFeature(
            header = "BPM",
            body = "Drag the circular dial to set tempo. " +
                "Press the center to start or stop; beat boxes highlight with each click.",
            textColor = textColor,
        )
        InfoFeature(
            header = "Time Signature",
            body = "Tap the numerator and denominator above the dial to set your meter. " +
                "Add up to four time-signature components for mixed meters. " +
                "Tap the note value next to the BPM to choose what gets the click.",
            textColor = textColor,
        )
        InfoFeature(
            header = "Beat accents",
            body = "Tap a beat box to cycle lead, beat, or inactive. " +
                "Lead beats show a dot and can sound different from regular beats.",
            textColor = textColor,
        )
        InfoFeature(
            header = "Subdivisions",
            body = "Tap the button inside the BPM dial to add 2–5 clicks per beat, or turn subdivisions off.",
            textColor = textColor,
        )
        InfoFeature(
            header = "Sequences",
            body = "Build multi-step routines with different meters and tempos. " +
                "Set repeats per step, scale tempo with the percent dial, and enable loop to repeat the whole sequence. " +
                "Save and load named sequences, or add the current meter from the main screen bottom bar.",
            textColor = textColor,
        )
        InfoFeature(
            header = "Settings",
            body = "Pick separate lead, beat, and subdivision tones; each previews when selected. " +
                "Choose a visual theme for the app.",
            textColor = textColor,
        )
        InfoFeature(
            header = "Navigation",
            body = "On the main screen, swipe up with two fingers for Sequence or left for Settings. " +
                "From Sequence swipe down to return; from Settings swipe left for this page.",
            textColor = textColor,
        )
    }
}

class InformationActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithSlideLeft()
                }
            },
        )
        setContent {
            val themeSetting by remember {
                dataStore.data
                    .map { preferences -> preferences[THEME_KEY] ?: DEFAULT_THEME }
            }.collectAsState(initial = DEFAULT_THEME)

            MixedMeterTheme(themeName = themeSetting) {
                val theme = currentAppTheme()

                Scaffold(
                    containerColor = theme.background,
                    contentWindowInsets = WindowInsets.statusBars,
                    modifier = Modifier
                        .fillMaxSize()
                        .twoFingerSwipe(
                            onSwipeRight = { finishWithSlideLeft() },
                        ),
                    topBar = {
                        TopAppBar(
                            title = { Text("Information", color = theme.text) },
                            navigationIcon = {
                                IconButton(onClick = { finishWithSlideLeft() }) {
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
                ) { innerPadding ->
                    InfoCollection(
                        textColor = theme.text,
                        modifier = Modifier
                            .background(theme.background)
                            .padding(innerPadding)
                            .navigationBarBottomPadding()
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
