package com.gekkotron.nidoo.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Inter = FontFamily(
    variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600), variable(R.font.inter, 700),
)
val Manrope = FontFamily(variable(R.font.manrope, 600), variable(R.font.manrope, 700))

@Immutable
data class NidooType(
    val display: TextStyle,
    val cardName: TextStyle,
    val cardValue: TextStyle,
    val label: TextStyle,
    val body: TextStyle,
)

/** Night Glass titles are bold Inter; Ivoire titles use Manrope bold. */
fun nidooType(dark: Boolean) = NidooType(
    display = if (dark) {
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.02).em)
    } else {
        TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.03).em)
    },
    cardName = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    cardValue = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
)

internal fun materialTypography(): Typography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontFamily = Inter), titleMedium = titleMedium.copy(fontFamily = Inter),
        titleSmall = titleSmall.copy(fontFamily = Inter), bodyLarge = bodyLarge.copy(fontFamily = Inter),
        bodyMedium = bodyMedium.copy(fontFamily = Inter), bodySmall = bodySmall.copy(fontFamily = Inter),
        labelLarge = labelLarge.copy(fontFamily = Inter), labelMedium = labelMedium.copy(fontFamily = Inter),
        labelSmall = labelSmall.copy(fontFamily = Inter),
    )
}
