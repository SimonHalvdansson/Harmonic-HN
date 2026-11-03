package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.network.AiModel
import com.simon.harmonichackernews.network.AiModelCatalogRepository
import com.simon.harmonichackernews.network.AiModelCatalogSort
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.ui.settings.AiBaseUrlPreset
import com.simon.harmonichackernews.ui.settings.AiModelSelectorDialog
import com.simon.harmonichackernews.ui.settings.AiSummaryBaseUrlDialog
import com.simon.harmonichackernews.ui.settings.AiSummaryTextDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiDialogStateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun promptDraftSurvivesRecreationWithoutSaving() {
        var saves = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Theme {
                AiSummaryTextDialog(
                    title = "System prompt", hint = "Prompt", initialValue = "Initial prompt",
                    defaultValue = "Default", minLines = 3, maxLines = 8, textSizeSp = 15,
                    trimValue = false, allowEmpty = true, showReset = true, asciiInput = false,
                    onSave = { saves++; null }, onDismiss = {},
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Keep this unsaved\nmultiline draft")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Keep this unsaved\nmultiline draft")))
        assertEquals(0, saves)
    }

    @Test
    fun baseUrlDraftSurvivesRecreationWithoutChangingTheProvider() {
        var saves = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Theme {
                AiSummaryBaseUrlDialog(
                    initialUrl = "https://initial.example/v1",
                    presets = listOf(AiBaseUrlPreset("other", "Other provider", "https://other.example/v1")),
                    onSave = { saves++ }, onDismiss = {},
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("https://private.example/custom/v1")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("https://private.example/custom/v1")))
        assertEquals(0, saves)
    }

    @Test
    fun backAndRepeatedSaveDoNotCancelOrDuplicateAnActiveSecureSave() {
        val result = CompletableDeferred<String?>()
        val savedValues = mutableListOf<String>()
        var dismissals = 0
        compose.setContent {
            Theme {
                AiSummaryTextDialog(
                    title = "API key", hint = "Key", initialValue = "initial",
                    defaultValue = "", minLines = 1, maxLines = 1, textSizeSp = 16,
                    trimValue = true, allowEmpty = true, showReset = false, asciiInput = true,
                    onSave = { savedValues += it; result.await() }, onDismiss = { dismissals++ },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("  pending-key  ")
        val click = requireNotNull(compose.onNodeWithText("Save").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action)
        compose.runOnUiThread { click(); click() }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        Espresso.pressBack()
        compose.onNodeWithText("API key").assertExists()
        assertEquals(listOf("pending-key"), savedValues)
        assertEquals(0, dismissals)
        assertFalse(result.isCancelled)
        result.complete(null)
        compose.waitUntil(5_000) { dismissals == 1 }
    }

    @Test
    fun modelDraftFilterAndCatalogScrollSurviveRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Theme {
                AiModelSelectorDialog(
                    initialModel = "fixture/model-0", provider = AiSummaryProviders.defaultProvider,
                    catalogRepository = FixtureModels, onSave = {}, onDismiss = {}, providerIcon = {},
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("ai-model-catalog").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("ai-model-input").performTextReplacement("fixture/unsaved-model")
        compose.onNodeWithTag("ai-model-filter-Free").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("ai-model-catalog").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("ai-model-catalog").performScrollToIndex(10)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("ai-model-catalog").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("ai-model-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("fixture/unsaved-model")))
        compose.onNodeWithTag("ai-model-filter-Free").assertIsSelected()
        compose.onNodeWithText("Fixture model 10").assertExists()
    }

    @Test
    fun modelSelectionCommitsOnceWhenTheSameSaveActionIsInvokedTwice() {
        val saved = mutableListOf<String>()
        var dismissals = 0
        compose.setContent {
            Theme {
                AiModelSelectorDialog(
                    initialModel = "fixture/model-0", provider = AiSummaryProviders.defaultProvider,
                    catalogRepository = FixtureModels, onSave = { saved += it },
                    onDismiss = { dismissals++ }, providerIcon = {},
                )
            }
        }
        compose.onNodeWithTag("ai-model-input").performTextReplacement(" fixture/model-3 ")
        val save = requireNotNull(compose.onNodeWithText("Save model").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action)
        compose.runOnUiThread { save(); save() }
        compose.waitUntil(5_000) { dismissals == 1 }
        assertEquals(listOf("fixture/model-3"), saved)
    }

    private object FixtureModels : AiModelCatalogRepository {
        val models = List(30) { index ->
            AiModel("fixture/model-$index", "fixture/model-$index", "Fixture model $index", 0, 0.0, 0.0, 8192)
        }
        override suspend fun fetchModels(provider: AiSummaryProviders.Provider, sort: AiModelCatalogSort): List<AiModel> {
            delay(25)
            return models
        }
        override suspend fun resolveModel(provider: AiSummaryProviders.Provider, enteredModelId: String?) =
            models.firstOrNull { it.requestId == enteredModelId } ?: models.first().copy(requestId = enteredModelId.orEmpty())
        override suspend fun fetchUptimeLastDay(provider: AiSummaryProviders.Provider, openRouterModelId: String) = 99.9
    }

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        val palette = HarmonicThemeCatalog.resolve("light", false)
        HarmonicTheme(palette.colorScheme, palette.dark, content = content)
    }
}
