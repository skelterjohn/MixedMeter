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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme

private const val DISCORD_INVITE_URL = "https://discord.gg/E4XarYpwK"
private const val GITHUB_REPO_URL = "https://github.com/skelterjohn/mixedmeter"
private const val CREATOR_EMAIL = "jasmuth@gmail.com"
private const val CREATOR_EMAIL_SUBJECT = "About Mixed Meter Metronome..."

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
                val context = LocalContext.current
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
                    Column(
                        modifier = Modifier
                            .background(theme.background)
                            .padding(innerPadding)
                            .navigationBarBottomPadding()
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "Join the discord",
                            color = theme.text,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_INVITE_URL)),
                                )
                            },
                        )
                        Text(
                            text = "Email the creator",
                            color = theme.text,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .clickable {
                                    val mailUri = Uri.parse(
                                        "mailto:$CREATOR_EMAIL?subject=${Uri.encode(CREATOR_EMAIL_SUBJECT)}",
                                    )
                                    context.startActivity(Intent(Intent.ACTION_SENDTO, mailUri))
                                },
                        )
                        Text(
                            text = "GitHub",
                            color = theme.text,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)),
                                    )
                                },
                        )
                    }
                }
            }
        }
    }
}
