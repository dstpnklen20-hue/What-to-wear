package ru.whattowear.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import ru.whattowear.app.R
import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingItem
import java.io.File

@DrawableRes
fun categoryIconRes(category: Category): Int = when (category) {
    Category.OUTERWEAR -> R.drawable.ic_cat_outerwear
    Category.SWEATER -> R.drawable.ic_cat_sweater
    Category.TOP -> R.drawable.ic_cat_top
    Category.BOTTOM -> R.drawable.ic_cat_bottom
    Category.SHOES -> R.drawable.ic_cat_shoes
    Category.HEADWEAR -> R.drawable.ic_cat_headwear
    Category.GLOVES_SCARVES -> R.drawable.ic_cat_gloves
    Category.ACCESSORIES -> R.drawable.ic_cat_accessories
}

fun warmthLabel(warmth: Int): String = when (warmth) {
    1 -> "очень лёгкая"
    2 -> "лёгкая"
    3 -> "средняя"
    4 -> "тёплая"
    else -> "очень тёплая"
}

/** Фото вещи (квадратом) или линейная иконка категории на нейтральном фоне, если фото нет. */
@Composable
fun ItemPhoto(
    item: ClothingItem,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    iconSize: Dp = 36.dp,
) {
    ItemPhoto(item.photoPath, item.category, modifier, shape, iconSize)
}

@Composable
fun ItemPhoto(
    photoPath: String?,
    category: Category,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    iconSize: Dp = 36.dp,
) {
    Box(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder = @Composable {
            Icon(
                painterResource(categoryIconRes(category)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        }
        if (photoPath != null) {
            SubcomposeAsyncImage(
                model = File(photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxWidth()) },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } },
            )
        } else {
            placeholder()
        }
    }
}
