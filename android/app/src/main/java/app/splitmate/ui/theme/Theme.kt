package app.splitmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Original SplitMate brand palette - a deep teal paired with a warm amber accent,
// not derived from any existing app's colors. Chosen for good contrast in both themes
// and a "financial but friendly" feel rather than a cold corporate one.
val SplitMateTeal = Color(0xFF0F6B5C)
val SplitMateTealContainer = Color(0xFFCDEEE3)
val SplitMateAmber = Color(0xFFB6720A)
val SplitMateAmberContainer = Color(0xFFFFE0B2)
val SplitMateTealDark = Color(0xFF7BDBC4)
val SplitMateTealContainerDark = Color(0xFF0B4F44)
val SplitMateAmberDark = Color(0xFFF7C067)
val SplitMateAmberContainerDark = Color(0xFF5C4000)

private val LightColors = lightColorScheme(
    primary = SplitMateTeal,
    onPrimary = Color.White,
    primaryContainer = SplitMateTealContainer,
    onPrimaryContainer = Color(0xFF00201A),
    secondary = SplitMateAmber,
    onSecondary = Color.White,
    secondaryContainer = SplitMateAmberContainer,
    onSecondaryContainer = Color(0xFF2A1800),
    background = Color(0xFFFBFDFA),
    surface = Color(0xFFFBFDFA),
    surfaceVariant = Color(0xFFE7F0EC),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = SplitMateTealDark,
    onPrimary = Color(0xFF00382D),
    primaryContainer = SplitMateTealContainerDark,
    onPrimaryContainer = SplitMateTealContainer,
    secondary = SplitMateAmberDark,
    onSecondary = Color(0xFF3E2900),
    secondaryContainer = SplitMateAmberContainerDark,
    onSecondaryContainer = SplitMateAmberContainer,
    background = Color(0xFF0E1512),
    surface = Color(0xFF0E1512),
    surfaceVariant = Color(0xFF1C2A25),
    error = Color(0xFFF2B8B5),
)

// Generously rounded corners throughout give the whole app a softer, more premium feel
// than Material's tighter defaults - applied consistently so cards, dialogs, buttons
// and text fields all read as one deliberate system rather than mixed defaults.
private val SplitMateShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun SplitMateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, shapes = SplitMateShapes, typography = SplitMateTypography, content = content)
}

/** Semantic colors used consistently for money across the app - never ad hoc reds/greens per-screen. */
object BalanceColors {
    val Positive = Color(0xFF1E8E5A) // you are owed
    val PositiveContainer = Color(0xFFDCF5E7)
    val Negative = Color(0xFFC5433F) // you owe
    val NegativeContainer = Color(0xFFFBE2E1)
    val PositiveDark = Color(0xFF7FE0AA)
    val NegativeDark = Color(0xFFF2A6A3)
}
