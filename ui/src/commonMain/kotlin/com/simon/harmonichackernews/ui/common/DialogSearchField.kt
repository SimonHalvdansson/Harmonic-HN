package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_search
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

/** Shared filled, rounded search input for comment search and selection dialogs. */
@Composable
internal fun DialogSearchField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit,
    placeholder: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val keyboard = LocalSoftwareKeyboardController.current
    val background = if (HarmonicTheme.isDark) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerHighest
    TextField(value, onValueChange, modifier = modifier, enabled = enabled,
        placeholder = { Text(placeholder, fontFamily = ProductSansFontFamily) },
        textStyle = TextStyle(fontFamily = ProductSansFontFamily, fontSize = 16.sp),
        leadingIcon = { Icon(painterResource(Res.drawable.ic_search), null) },
        shape = RoundedCornerShape(32.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = background, unfocusedContainerColor = background,
            disabledContainerColor = background, focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), singleLine = true)
}
