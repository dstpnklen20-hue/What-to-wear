package ru.whattowear.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Тонкая строка-пометка с иконкой (без заливки и тени): «прогноз неуверенный», «нет сети» и т. п. */
@Composable
fun NoteBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
    emphasized: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (emphasized) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant, MaterialTheme.shapes.medium)
            .padding(start = 14.dp, end = if (action != null) 4.dp else 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (emphasized) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}
