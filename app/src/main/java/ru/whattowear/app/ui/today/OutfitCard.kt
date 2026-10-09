package ru.whattowear.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.whattowear.app.ui.components.ItemPhoto
import ru.whattowear.app.ui.components.SectionLabel
import ru.whattowear.app.ui.components.ThinDivider
import ru.whattowear.app.ui.navigation.MotionSpec
import ru.whattowear.domain.outfit.ComfortRating
import ru.whattowear.domain.outfit.OutfitPiece
import ru.whattowear.domain.outfit.OutfitRecommendation

/** Карточка подобранного комплекта: фото вещей, почему так, советы и честные предупреждения. */
@Composable
fun OutfitCard(
    recommendation: OutfitRecommendation?,
    wardrobeEmpty: Boolean,
    onNextVariant: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        SectionLabel(
            when {
                recommendation?.conditions?.window?.isTomorrow == true -> "Что надеть завтра"
                else -> "Что надеть сегодня"
            },
            Modifier.padding(bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.large)
                .padding(vertical = 16.dp),
        ) {
            when {
                recommendation == null -> Text(
                    "Прогноз не покрывает время вне дома — обновите прогноз.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                recommendation.outfit == null -> GenericAdvice(recommendation, wardrobeEmpty, onAddItem, onOpenWardrobe)
                else -> {
                    AnimatedContent(
                        targetState = recommendation.outfit!!.pieces,
                        transitionSpec = { fadeIn(MotionSpec.content()) togetherWith fadeOut(MotionSpec.exit()) },
                        label = "outfit",
                    ) { pieces -> PiecesRow(pieces) }
                    Details(recommendation, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (recommendation.variantCount > 1) {
                        ThinDivider(Modifier.padding(top = 12.dp))
                        TextButton(onClick = onNextVariant, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                            Icon(Icons.Outlined.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Другой вариант · ${recommendation.variantIndex + 1} из ${recommendation.variantCount}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PiecesRow(pieces: List<OutfitPiece>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        items(pieces, key = { it.item.id }) { piece ->
            Column(
                Modifier.width(96.dp).semantics {
                    contentDescription = "${piece.role.title}: ${piece.item.name}" + if (piece.removable) ", можно снять днём" else ""
                },
            ) {
                ItemPhoto(piece.item, Modifier.size(96.dp), iconSize = 32.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    piece.role.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (piece.removable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(piece.item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (piece.removable) {
                    Text("снять днём", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Details(recommendation: OutfitRecommendation, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            recommendation.explanation.forEach { line ->
                Text("· $line", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        recommendation.tips.forEach { NoteLine(Icons.Outlined.Lightbulb, it, accent = true) }
        recommendation.warnings.forEach { NoteLine(Icons.Outlined.Info, it, accent = false) }
    }
}

@Composable
private fun NoteLine(icon: ImageVector, text: String, accent: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GenericAdvice(
    recommendation: OutfitRecommendation,
    wardrobeEmpty: Boolean,
    onAddItem: () -> Unit,
    onOpenWardrobe: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Checkroom, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(
                if (wardrobeEmpty) "Гардероб пока пуст" else "Нет подходящих вещей",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        recommendation.genericAdvice?.let {
            Text("В целом по погоде: $it", style = MaterialTheme.typography.bodyLarge)
        }
        Details(recommendation)
        Text(
            "Добавьте свои вещи — и приложение будет собирать комплект из них.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddItem) { Text("Добавить вещь") }
            if (!wardrobeEmpty) OutlinedButton(onClick = onOpenWardrobe) { Text("Гардероб") }
        }
    }
}

/** Вечерний вопрос «как вам было?» — по ответам сдвигается личный температурный порог. */
@Composable
fun FeedbackCard(onRate: (ComfortRating) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Как вам было сегодня в этом комплекте?", style = MaterialTheme.typography.titleMedium)
        Text(
            "По ответам приложение подстроит подбор под вас.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ComfortRating.COLD to "Холодно", ComfortRating.OK to "Нормально", ComfortRating.HOT to "Жарко").forEach { (rating, label) ->
                FilterChip(selected = false, onClick = { onRate(rating) }, label = { Text(label) })
            }
        }
    }
}
