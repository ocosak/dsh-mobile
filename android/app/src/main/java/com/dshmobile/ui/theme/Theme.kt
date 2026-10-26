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
// 大标题 34、标题 22/17、正文 17/16、次要 13；卡片 12、药丸 28。
// 深色按 iOS 规范：纯黑画布、#1C1C1E 分组底、#2C2C2E 卡片。
// 不再用之前的悬浮毛玻璃药丸导航，也不再用 ChatGPT 式的黑主色。

/** iOS 系统蓝：选中态、链接、主按钮、用户气泡。 */
val IosBlue = Color(0xFF007AFF)
val IosBlueDark = Color(0xFF0A84FF)

/** iOS 成功绿：已配对、完成态。 */
val IosGreen = Color(0xFF34C759)

/**
 * 深色红按钮容器：白字/白图标写在红底上时不能用提亮后的正文红
 * #FF6B60（白字对比度会掉到 2.79:1），单独保留较深的 #FF453A
 * （白字 3.41:1，按钮文案 16sp 半粗体按大字 3:1 计过线）。
 */
val IosRedButtonDark = Color(0xFFFF453A)

/**
 * 红按钮容器色：浅色直接用 error（#D70015，白字 5.38:1）；
 * 深色用单独的深红（见 IosRedButtonDark）。红字正文一律走
 * MaterialTheme.colorScheme.error，不要走这里。
 */
@Composable
fun errorButtonColor(): Color =
    if (isSystemInDarkTheme()) IosRedButtonDark else MaterialTheme.colorScheme.error

private val LightScheme = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    // 次要色与主色同为系统蓝：iOS 只有单一强调色，不另设第二强调色。
    secondary = IosBlue,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F7),
    onBackground = Color.Black,
    onSurface = Color.Black,
    // 次要灰从 #8A8A8E 加深到 #6E6E73：旧色在白底上对比度只有 3.44:1、
    // 在分组灰底上只有 3.08:1，达不到 WCAG AA 对正文的 4.5:1；新色在白底
    // 5.07:1、在分组灰底 4.54:1，两处都过线。深色次要灰 #98989D 在
    // 实际深色面（分组底 #1C1C1E 5.93:1、卡片 #2C2C2E 4.85:1）上都过线，不动。
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE5E5EA),
    // 系统红 #FF3B30 加深到 #D70015：旧色在白底上只有 3.55:1、
    // 在分组灰底上只有 3.18:1，正文达不到 WCAG AA 的 4.5:1；新色在
    // 白底 5.38:1、在分组灰底 4.83:1，两处都过线，仍是纯红系。
    error = Color(0xFFD70015),
    // M3 的容器色（对话框、菜单等）不定义会漏 Material 默认紫调；iOS 语言里
    // 容器就是画布色，全部对齐 surface（白），surfaceTint 同理。
    surfaceTint = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
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
    // 系统红 #FF453A 提亮到 #FF6B60：旧色在卡片面 #2C2C2E 上只有
    // 4.09:1，正文达不到 4.5:1；新色在卡片面 4.99:1、在分组底
    // #1C1C1E 上 6.10:1，两处都过线。注意：这个色只给红字正文，
    // 红按钮容器不能用它（白字会掉到 2.79:1），按钮走 errorButtonColor()。
    error = Color(0xFFFF6B60),
    // 深色容器同样对齐 surface（#2C2C2E），不漏 Material 默认紫调。
    surfaceTint = Color(0xFF2C2C2E),
    surfaceContainerLowest = Color(0xFF2C2C2E),
    surfaceContainerLow = Color(0xFF2C2C2E),
    surfaceContainer = Color(0xFF2C2C2E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF2C2C2E),
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
