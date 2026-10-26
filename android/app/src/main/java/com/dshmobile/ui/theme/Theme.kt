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

// 标准 iOS 语言，对照用户给的 Kimi 视频提炼，不照搬 Kimi 的品牌：
// 纯白画布、分组灰底、细分隔线、系统蓝作交互色、成功绿；
// 大标题 34、标题 17、正文 17、次要 13；圆角卡片 12、药丸输入 28。
// 深色按 iOS 规范：纯黑画布、#1C1C1E 分组底、#2C2C2E 卡片。
// 不再用之前的悬浮毛玻璃药丸导航，也不再用 ChatGPT 式的黑主色。

/** iOS 系统蓝：选中态、链接、主按钮、用户气泡。 */
val IosBlue = Color(0xFF007AFF)
val IosBlueDark = Color(0xFF0A84FF)

/** iOS 成功绿：已配对、完成态。 */
val IosGreen = Color(0xFF34C759)

/** 保留旧名，避免其他文件 import 断裂；值已改为 iOS 蓝。 */
val DeepSeekBlue = IosBlue
val DeepSeekBlueDark = IosBlueDark

private val LightScheme = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    secondary = IosBlue,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F7),
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF8A8A8E),
    outline = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
)
private val DarkScheme = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    secondary = IosBlueDark,
    background = Color.Black,
    surface = Color(0xFF2C2C2E),
    surfaceVariant = Color(0xFF1C1C1E),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF98989D),
    outline = Color(0xFF38383A),
    error = Color(0xFFFF453A),
)

private val IosShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val IosTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
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
