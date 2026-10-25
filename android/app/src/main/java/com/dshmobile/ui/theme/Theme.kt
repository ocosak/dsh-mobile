package com.dshmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Latest-iOS design language, adapted to Android Compose.
// This is NOT native iOS: there is no UIKit blur API here. "Liquid
// Glass" is approximated with translucent surfaces, a specular
// top-edge highlight, soft shadows and rounded geometry, over a
// subtly tinted grouped background — the same visual language,
// rendered with Compose primitives only.

private val IosBlue = Color(0xFF007AFF)
private val IosBlueDark = Color(0xFF0A84FF)

/** Translucent glass tokens, resolved per theme at call sites. */
object IosGlass {
    // Frosted surface: mostly opaque for legibility on Android
    // (true backdrop blur is not a Compose primitive), with a
    // translucent tint so content beneath still reads through.
    fun surface(dark: Boolean): Color =
        if (dark) Color(0xFF1C1C1E).copy(alpha = 0.78f)
        else Color.White.copy(alpha = 0.72f)
    fun surfaceStrong(dark: Boolean): Color =
        if (dark) Color(0xFF2C2C2E).copy(alpha = 0.85f)
        else Color.White.copy(alpha = 0.88f)
    // Specular edge highlight: bright on the top/leading edge.
    fun specular(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.22f)
        else Color.White.copy(alpha = 0.65f)
    fun border(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.12f)
        else Color.Black.copy(alpha = 0.06f)
    fun shadow(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.45f)
        else Color.Black.copy(alpha = 0.10f)
    // Tinted grouped background, light and dark, iOS system-grey style.
    fun background(dark: Boolean): Color =
        if (dark) Color(0xFF000000) else Color(0xFFF2F2F7)
}

private val LightScheme = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    background = Color(0xFFF2F2F7),
    surface = Color.White,
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8A8A8E),
)
private val DarkScheme = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989D),
)

// Latest-iOS geometry: generous radii, 20-28dp for cards/bars.
private val IosShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

// SF-style hierarchy: large title, headline, body, footnote.
// System font on Android approximates SF; weights/sizes follow iOS.
private val IosTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
)

@Composable
fun DshMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = IosTypography,
        shapes = IosShapes,
        content = content,
    )
}
