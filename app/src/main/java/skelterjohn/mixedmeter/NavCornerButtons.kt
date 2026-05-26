package skelterjohn.mixedmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val BottomNavButtonSize = 100.dp
val BottomNavEdgePadding = 16.dp
val SequenceNavIconButtonSize = 48.dp

/** Minimum bottom inset when the system reports none (older edge-to-edge, or consumed insets). */
private val MinimumNavigationBarHeight = 48.dp

/**
 * Bottom inset for the system navigation bar (3-button or gesture).
 *
 * Uses the largest of [WindowInsets.navigationBars], [WindowInsets.systemBars], and
 * [WindowInsets.safeDrawing] so Android 15 edge-to-edge (including translucent 3-button nav)
 * gets enough clearance. When all are zero, [MinimumNavigationBarHeight] is used.
 */
@Composable
fun navigationBarBottomInset(): Dp {
    val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val systemBars = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    return maxOf(navBars, systemBars, safeDrawing)
        .takeIf { it > 0.dp }
        ?: MinimumNavigationBarHeight
}

/** Reserves space above the system navigation bar (back / home / recents or gesture handle). */
@Composable
fun Modifier.navigationBarBottomPadding(): Modifier = composed {
    padding(bottom = navigationBarBottomInset())
}

@Composable
fun SequenceNavIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = currentAppTheme()
    Box(
        modifier = modifier
            .size(SequenceNavIconButtonSize)
            .alpha(if (enabled) 1f else 0.4f)
            .background(theme.buttonSurface, RoundedCornerShape(6.dp))
            .border(1.dp, theme.buttonBorder, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = theme.iconTint,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
fun BottomNavIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(BottomNavButtonSize),
    ) {
        content()
    }
}

@Composable
fun ArrowDropUpNavIcon(modifier: Modifier = Modifier) {
    val theme = currentAppTheme()
    Icon(
        imageVector = Icons.Default.ArrowDropUp,
        contentDescription = "Sequences",
        tint = theme.iconTint,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun ArrowDropDownNavIcon(modifier: Modifier = Modifier) {
    val theme = currentAppTheme()
    Icon(
        imageVector = Icons.Default.ArrowDropDown,
        contentDescription = "Back to metronome",
        tint = theme.iconTint,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun AddToSequenceNavIcon(modifier: Modifier = Modifier) {
    val theme = currentAppTheme()
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "Add to sequence",
        tint = theme.iconTint,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun SettingsNavIcon(modifier: Modifier = Modifier) {
    val theme = currentAppTheme()
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Settings",
        tint = theme.iconTint,
        modifier = modifier.fillMaxSize(),
    )
}
