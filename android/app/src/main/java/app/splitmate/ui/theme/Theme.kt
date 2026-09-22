package app.splitmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Original SplitMate brand palette (teal + amber) - not derived from any existing app's colors.
val SplitMateTeal = Color(0xFF0F6B5C)
val SplitMateAmber = Color(0xFFF2A93B)
val SplitMateTealDark = Color(0xFF69D9C4)
val SplitMateAmberDark = Color(0xFFF7C067)

private val LightColors = lightColorScheme(
    primary = SplitMateTeal,
    secondary = SplitMateAmber,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = SplitMateTealDark,
    secondary = SplitMateAmberDark,
    error = Color(0xFFF2B8B5),
)

@Composable
fun SplitMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

/** Semantic colors used consistently for money across the app. */
object BalanceColors {
    val Positive = Color(0xFF1E8E5A) // you are owed
    val Negative = Color(0xFFD1495B) // you owe
}
