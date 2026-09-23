package com.simon.harmonichackernews.ui

import android.util.SparseArray
import android.view.autofill.AutofillValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginAutofillTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun autofill(field: SemanticsNodeInteraction, value: String) {
        val node = field.fetchSemanticsNode()
        val view = (requireNotNull(node.root) as ViewRootForTest).view
        compose.runOnUiThread {
            view.autofill(SparseArray<AutofillValue>().apply {
                put(node.id, AutofillValue.forText(value))
            })
        }
    }

    @Test fun autofillTargetsDistinctFieldsEvenWhenPasswordIsVisible() {
        val navigation = compose.activity.navigationController
        compose.runOnIdle {
            navigation.dismissWelcomeDialog()
            navigation.dismissChangelogDialog()
            navigation.showLoginDialog()
        }
        val username = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.Username))
        val password = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.Password))
        try {
            autofill(username, "autofill_reader")
            autofill(password, "test_password_42")
            username.assertTextContains("autofill_reader")
            compose.onNodeWithContentDescription("Show password").performClick()
            password.assertTextContains("test_password_42")
            autofill(password, "replacement_password")
            username.assertTextContains("autofill_reader")
            password.assertTextContains("replacement_password")
        } finally {
            compose.runOnIdle { navigation.dismissLoginDialog() }
        }
    }
}
