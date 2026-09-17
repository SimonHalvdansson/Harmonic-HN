package com.simon.harmonichackernews.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.google_sans_flex_rounded_bold
import com.simon.harmonichackernews.resources.google_sans_flex_rounded_regular
import com.simon.harmonichackernews.resources.product_sans_bold
import com.simon.harmonichackernews.resources.product_sans_italic
import com.simon.harmonichackernews.resources.product_sans_regular
import org.jetbrains.compose.resources.Font

val ProductSansFontFamily: FontFamily
    @Composable get() {
        val regular = Font(Res.font.product_sans_regular, FontWeight.Normal)
        val semibold = Font(Res.font.product_sans_bold, FontWeight.SemiBold)
        val italic = Font(
            Res.font.product_sans_italic,
            FontWeight.Normal,
            FontStyle.Italic,
        )
        return remember(regular, semibold, italic) {
            FontFamily(regular, semibold, italic)
        }
    }

val GoogleSansFlexRoundedFontFamily: FontFamily
    @Composable get() {
        val regular = Font(Res.font.google_sans_flex_rounded_regular, FontWeight.Normal)
        val bold = Font(Res.font.google_sans_flex_rounded_bold, FontWeight.Bold)
        return remember(regular, bold) { FontFamily(regular, bold) }
    }
