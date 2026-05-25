package skelterjohn.mixedmeter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.stringPreferencesKey

val THEME_KEY = stringPreferencesKey("theme_setting")

val THEME_OPTIONS = listOf("Gray", "Slate", "Sand", "Moss", "Dusk")

const val DEFAULT_THEME = "Gray"

/** Dropdown fields and menus use black text on light Material surfaces. */
val DropdownMenuTextColor = Color.Black

data class AppThemeColors(
    val name: String,
    val background: Color,
    val buttonSurface: Color,
    val buttonBorder: Color,
    val iconTint: Color,
    val text: Color,
    val embossSurface: Color,
    val embossSurfaceActive: Color,
    val embossSurfaceDragging: Color,
    val embossHighlight: Color,
    val embossShadow: Color,
    val dialogSurface: Color,
)

fun appThemeForName(name: String): AppThemeColors =
    when (name) {
        "Slate" -> AppThemes.slate
        "Sand" -> AppThemes.sand
        "Moss" -> AppThemes.moss
        "Dusk" -> AppThemes.dusk
        else -> AppThemes.gray
    }

object AppThemes {
    val gray = AppThemeColors(
        name = "Gray",
        background = Color(0xFF9E9E9E),
        buttonSurface = Color(0xFFAEAEAE),
        buttonBorder = Color.Black,
        iconTint = Color.Black,
        text = Color.Black,
        embossSurface = Color(0xFF969696),
        embossSurfaceActive = Color(0xFFB8B8B8),
        embossSurfaceDragging = Color(0xFFA8A8A8),
        embossHighlight = Color(0xFFDADADA),
        embossShadow = Color(0xFF454545),
        dialogSurface = Color(0xFFE0E0E0),
    )

    val slate = AppThemeColors(
        name = "Slate",
        background = Color(0xFF4A5568),
        buttonSurface = Color(0xFF718096),
        buttonBorder = Color(0xFF1A202C),
        iconTint = Color(0xFFF7FAFC),
        text = Color(0xFFF7FAFC),
        embossSurface = Color(0xFF5A6578),
        embossSurfaceActive = Color(0xFF7A8798),
        embossSurfaceDragging = Color(0xFF667384),
        embossHighlight = Color(0xFF9AA8BA),
        embossShadow = Color(0xFF2D3748),
        dialogSurface = Color(0xFF5A6578),
    )

    val sand = AppThemeColors(
        name = "Sand",
        background = Color(0xFFC9B896),
        buttonSurface = Color(0xFFD9CDB0),
        buttonBorder = Color(0xFF5C4033),
        iconTint = Color(0xFF3E2723),
        text = Color(0xFF3E2723),
        embossSurface = Color(0xFFBFAA88),
        embossSurfaceActive = Color(0xFFD4C4A8),
        embossSurfaceDragging = Color(0xFFC9B896),
        embossHighlight = Color(0xFFE8DCC8),
        embossShadow = Color(0xFF6B5344),
        dialogSurface = Color(0xFFE0D4C0),
    )

    val moss = AppThemeColors(
        name = "Moss",
        background = Color(0xFF5D7A5D),
        buttonSurface = Color(0xFF8FA88F),
        buttonBorder = Color(0xFF1E3A1E),
        iconTint = Color(0xFF0F1F0F),
        text = Color(0xFF0F1F0F),
        embossSurface = Color(0xFF6B886B),
        embossSurfaceActive = Color(0xFF9AB89A),
        embossSurfaceDragging = Color(0xFF7A947A),
        embossHighlight = Color(0xFFB4D4B4),
        embossShadow = Color(0xFF2D4A2D),
        dialogSurface = Color(0xFF8FA88F),
    )

    val dusk = AppThemeColors(
        name = "Dusk",
        background = Color(0xFF5C4B6E),
        buttonSurface = Color(0xFF8B7A9E),
        buttonBorder = Color(0xFF2D2438),
        iconTint = Color(0xFFF5F0FA),
        text = Color(0xFFF5F0FA),
        embossSurface = Color(0xFF6E5C82),
        embossSurfaceActive = Color(0xFF9A8AB0),
        embossSurfaceDragging = Color(0xFF7E6C92),
        embossHighlight = Color(0xFFB8A8C8),
        embossShadow = Color(0xFF3A3048),
        dialogSurface = Color(0xFF7E6C92),
    )
}

val LocalAppTheme = staticCompositionLocalOf { AppThemes.gray }

@Composable
fun ProvideAppTheme(
    themeName: String,
    content: @Composable () -> Unit,
) {
    val colors = appThemeForName(
        themeName.takeIf { it in THEME_OPTIONS } ?: DEFAULT_THEME,
    )
    CompositionLocalProvider(LocalAppTheme provides colors, content = content)
}

@Composable
@ReadOnlyComposable
fun currentAppTheme(): AppThemeColors = LocalAppTheme.current
