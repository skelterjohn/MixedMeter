package skelterjohn.mixedmeter

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val BottomNavButtonSize = 100.dp
val BottomNavEdgePadding = 16.dp

@Composable
fun RowScope.BottomNavIconButton(
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
fun SettingsNavIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Settings",
        tint = Color.Black,
        modifier = modifier.fillMaxSize(),
    )
}
