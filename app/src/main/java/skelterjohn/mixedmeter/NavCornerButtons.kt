package skelterjohn.mixedmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val BottomNavButtonSize = 100.dp
val BottomNavEdgePadding = 16.dp
val SequenceNavIconButtonSize = 48.dp
val SequenceNavIconButtonMinSize = 36.dp
private val SequenceNavControlsDialGap = 12.dp

/** Minimum bottom inset when the system reports none (older edge-to-edge, or consumed insets). */
private val MinimumNavigationBarHeight = 48.dp

/**
 * Bottom inset for the system navigation bar only (not the IME).
 *
 * Do not use [WindowInsets.safeDrawing] here — it includes keyboard height when the IME is open,
 * which would push the whole UI (including the dial) upward.
 */
@Composable
fun navigationBarBottomInset(): Dp {
    val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return navBars.takeIf { it > 0.dp } ?: MinimumNavigationBarHeight
}

/** Reserves space above the system navigation bar (back / home / recents or gesture handle). */
@Composable
fun Modifier.navigationBarBottomPadding(): Modifier = composed {
    padding(bottom = navigationBarBottomInset())
}

fun sequenceNavIconButtonSize(maxRowWidth: Dp): Dp {
    val defaultRowWidth = SequenceNavIconButtonSize * 3 + 8.dp
    val maxLeftWidth = maxRowWidth - CircleDisplaySize - SequenceNavControlsDialGap
    if (maxLeftWidth >= defaultRowWidth) return SequenceNavIconButtonSize
    return minOf(
        SequenceNavIconButtonSize,
        ((maxLeftWidth - 8.dp) / 3).coerceAtLeast(SequenceNavIconButtonMinSize),
    )
}

@Composable
fun SequenceNavControls(
    loopEnabled: Boolean,
    onLoopChange: (Boolean) -> Unit,
    hasSavedSequence: Boolean,
    hasItems: Boolean,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    iconButtonSize: Dp = SequenceNavIconButtonSize,
) {
    val theme = currentAppTheme()
    val iconSize = (iconButtonSize.value * 28f / SequenceNavIconButtonSize.value).dp
    Column(
        modifier = modifier.height(CircleDisplaySize),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = loopEnabled,
                onCheckedChange = onLoopChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = theme.text,
                    uncheckedColor = theme.text,
                    checkmarkColor = Color.White,
                ),
            )
            Text(text = "loop", color = theme.text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SequenceNavIconButton(
                icon = Icons.Default.Save,
                contentDescription = "Save sequence",
                onClick = onSave,
                buttonSize = iconButtonSize,
                iconSize = iconSize,
            )
            SequenceNavIconButton(
                icon = Icons.Default.FolderOpen,
                contentDescription = "Load sequence",
                enabled = hasSavedSequence,
                onClick = onLoad,
                buttonSize = iconButtonSize,
                iconSize = iconSize,
            )
            SequenceNavIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Clear sequence",
                enabled = hasItems,
                onClick = onClear,
                buttonSize = iconButtonSize,
                iconSize = iconSize,
            )
        }
        BottomNavIconButton(onClick = onBack) {
            ArrowDropDownNavIcon()
        }
    }
}

@Composable
fun SequenceNavIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = SequenceNavIconButtonSize,
    iconSize: Dp = 28.dp,
) {
    val theme = currentAppTheme()
    Box(
        modifier = modifier
            .size(buttonSize)
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
            modifier = Modifier.size(iconSize),
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

@Composable
fun InfoNavIcon(modifier: Modifier = Modifier) {
    val theme = currentAppTheme()
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Help,
        contentDescription = "Information",
        tint = theme.iconTint,
        modifier = modifier.fillMaxSize(),
    )
}
