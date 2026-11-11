package dev.blank.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

private val Green = Color(0xFF16A34A)
private val GreenDeep = Color(0xFF15803D)
private val GreenSoft = Color(0xFFDCFCE7)
private val GreenInk = Color(0xFF14532D)
private val Ink = Color(0xFF111827)
private val InkSoft = Color(0xFF5B6472)
private val Hairline = Color(0xFFE6EAF0)
private val Inset = Color(0xFFF4F6F8)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenSoft,
    onPrimaryContainer = GreenInk,
    secondary = GreenDeep,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Inset,
    onSurfaceVariant = InkSoft,
    outline = Hairline,
    outlineVariant = Color(0xFFEDF0F4),
)

private fun TextStyle.gothic() = copy(fontFamily = FontFamily.SansSerif)

private val GothicTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.gothic(),
        displayMedium = base.displayMedium.gothic(),
        displaySmall = base.displaySmall.gothic(),
        headlineLarge = base.headlineLarge.gothic(),
        headlineMedium = base.headlineMedium.gothic(),
        headlineSmall = base.headlineSmall.gothic(),
        titleLarge = base.titleLarge.gothic(),
        titleMedium = base.titleMedium.gothic(),
        titleSmall = base.titleSmall.gothic(),
        bodyLarge = base.bodyLarge.gothic(),
        bodyMedium = base.bodyMedium.gothic(),
        bodySmall = base.bodySmall.gothic(),
        labelLarge = base.labelLarge.gothic(),
        labelMedium = base.labelMedium.gothic(),
        labelSmall = base.labelSmall.gothic(),
    )
}

@Composable
fun BlankMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = GothicTypography,
        content = content,
    )
}