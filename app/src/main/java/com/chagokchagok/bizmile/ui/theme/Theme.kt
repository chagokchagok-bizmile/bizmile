package com.chagokchagok.bizmile.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Dark, high-contrast palette for an in-vehicle display. */
object Biz {
    val Bg = Color(0xFF0E1116)
    val Surface = Color(0xFF171C24)
    val SurfaceHigh = Color(0xFF202733)
    val Line = Color(0xFF2B3442)
    val Text = Color(0xFFE8ECF2)
    val Muted = Color(0xFF8B95A5)
    val Primary = Color(0xFF5B9BFF)
    val OnPrimary = Color(0xFF06162E)
    val Ok = Color(0xFF3DDC97)
    val Warn = Color(0xFFFFB454)
    val Danger = Color(0xFFFF6B6B)
    val Personal = Color(0xFFB79CFF)
    val Commute = Color(0xFF7FD4E8)
}

private val colors = darkColorScheme(
    primary = Biz.Primary,
    onPrimary = Biz.OnPrimary,
    background = Biz.Bg,
    onBackground = Biz.Text,
    surface = Biz.Surface,
    onSurface = Biz.Text,
    surfaceVariant = Biz.SurfaceHigh,
    onSurfaceVariant = Biz.Muted,
    outline = Biz.Line,
    error = Biz.Danger,
)

// Larger than phone defaults: read at arm's length from the driver's seat.
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, lineHeight = 50.sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
)

/**
 * The Pleos Connect display is 2560x1440 at 160 dpi, so 1 dp = 1 px and phone-sized type is far
 * too small from the driver's seat. Scale the whole UI so the app area is laid out ~1120 dp wide.
 */
@Composable
fun BizmileTheme(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scale = (LocalConfiguration.current.screenWidthDp / 1120f).coerceIn(1f, 1.6f)
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale),
        LocalContentColor provides Biz.Text,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
