package ru.whattowear.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/** Общие параметры анимаций: плавно, но сдержанно. Брать отсюда, а не заводить свои tween. */
object MotionSpec {
    const val SHORT = 160
    const val MEDIUM = 240

    fun <T> enter(): TweenSpec<T> = tween(MEDIUM, easing = FastOutSlowInEasing)
    fun <T> exit(): TweenSpec<T> = tween(SHORT, easing = FastOutSlowInEasing)
    fun <T> content(): TweenSpec<T> = tween(MEDIUM, easing = FastOutSlowInEasing)
}
