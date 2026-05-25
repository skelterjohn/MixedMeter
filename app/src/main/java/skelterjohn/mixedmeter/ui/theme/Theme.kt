package skelterjohn.mixedmeter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import skelterjohn.mixedmeter.ProvideAppTheme
import skelterjohn.mixedmeter.appThemeForName
import skelterjohn.mixedmeter.DEFAULT_THEME

@Composable
fun MixedMeterTheme(
    themeName: String = DEFAULT_THEME,
    content: @Composable () -> Unit,
) {
    val appTheme = appThemeForName(themeName)
    ProvideAppTheme(themeName) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                background = appTheme.background,
                surface = appTheme.dialogSurface,
                onBackground = appTheme.text,
                onSurface = appTheme.text,
                primary = appTheme.buttonSurface,
                onPrimary = appTheme.text,
            ),
            typography = Typography,
            content = content,
        )
    }
}
