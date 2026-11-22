package com.gekkotron.nidoo.designsystem

import com.gekkotron.nidoo.designsystem.resources.*
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
@Composable
private fun variable(res: FontResource, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Inter, a variable font, at the four weights the app uses (CMP resources, so the same file on Android and iOS). */
@Composable
fun interFamily(): FontFamily {
    val fonts = listOf(400, 500, 600, 700).map { variable(Res.font.inter, it) }
    return remember(fonts) { FontFamily(fonts) }
}

/** Manrope, the Ivoire titles' variable font. */
@Composable
fun manropeFamily(): FontFamily {
    val fonts = listOf(600, 700).map { variable(Res.font.manrope, it) }
    return remember(fonts) { FontFamily(fonts) }
}

@Immutable
data class NidooType(
    val display: TextStyle,
    val cardName: TextStyle,
    val cardValue: TextStyle,
    val label: TextStyle,
    val body: TextStyle,
)

/** Night Glass titles are bold Inter; Ivoire titles use Manrope bold. */
fun nidooType(dark: Boolean, inter: FontFamily = FontFamily.Default, manrope: FontFamily = FontFamily.Default) = NidooType(
    display = if (dark) {
        TextStyle(fontFamily = inter, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.02).em)
    } else {
        TextStyle(fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.03).em)
    },
    cardName = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    cardValue = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    label = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    body = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
)

internal fun materialTypography(inter: FontFamily): Typography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontFamily = inter), titleMedium = titleMedium.copy(fontFamily = inter),
        titleSmall = titleSmall.copy(fontFamily = inter), bodyLarge = bodyLarge.copy(fontFamily = inter),
        bodyMedium = bodyMedium.copy(fontFamily = inter), bodySmall = bodySmall.copy(fontFamily = inter),
        labelLarge = labelLarge.copy(fontFamily = inter), labelMedium = labelMedium.copy(fontFamily = inter),
        labelSmall = labelSmall.copy(fontFamily = inter),
    )
}
