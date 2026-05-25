package skelterjohn.mixedmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

val BottomNavButtonSize = 100.dp
val BottomNavEdgePadding = 16.dp
val SequenceNavIconButtonSize = 48.dp
private val NavIconButtonSurface = Color(0xFFAEAEAE)

@Composable
fun SequenceNavIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(SequenceNavIconButtonSize)
            .alpha(if (enabled) 1f else 0.4f)
            .background(NavIconButtonSurface, RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.Black,
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
    Icon(
        imageVector = Icons.Default.ArrowDropUp,
        contentDescription = "Sequences",
        tint = Color.Black,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun ArrowDropDownNavIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.ArrowDropDown,
        contentDescription = "Back to metronome",
        tint = Color.Black,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun AddToSequenceNavIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "Add to sequence",
        tint = Color.Black,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun SettingsNavIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Settings",
        tint = Color.Black,
        modifier = modifier.fillMaxSize(),
    )
}
