package com.simon.harmonichackernews

import android.annotation.SuppressLint
import android.app.Application
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalComposeUiApi::class)
// The crash path includes subcomposition inside a lazy item; the constraints are immaterial.
@SuppressLint("UnusedBoxWithConstraintsScope")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class AndroidComposeFocusTest {
    @get:Rule(order = 0) val focusPolicy = object : ExternalResource() {
        private var previousBypass = true
        override fun before() {
            previousBypass = ComposeUiFlags.isBypassUnfocusableComposeViewEnabled
            configureAndroidComposeFocus()
        }
        override fun after() {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
            ComposeUiFlags.isBypassUnfocusableComposeViewEnabled = previousBypass
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun configureKeyboardMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @Test fun removingFocusedNativeInputDoesNotReenterLazyComposition() {
        // With the bypass focus path enabled, removing this focused view searches for the
        // offscreen button during applyChanges and re-enters the list's measurement/composition.
        var visible by mutableStateOf(true)
        lateinit var input: EditText
        compose.setContent {
            LazyColumn(Modifier.height(200.dp)) {
                item {
                    if (visible) AndroidView(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        factory = { context -> EditText(context).also { input = it } },
                    )
                }
                items(20) { index ->
                    BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
                        if (index == 19) Button(onClick = {}) { Text("Next focus target") }
                        else Text("Row $index")
                    }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(input.requestFocus())
            assertTrue(input.hasFocus())
            visible = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(!input.isAttachedToWindow) }
    }

    @Test fun nativeInputStillAcceptsKeyboardTyping() {
        lateinit var input: EditText
        lateinit var nextInput: EditText
        compose.setContent {
            AndroidView(factory = { context ->
                CommentsWebViewHost(context).apply {
                    webViewContainer.addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        input = EditText(context).apply { id = View.generateViewId() }
                        nextInput = EditText(context).apply { id = View.generateViewId() }
                        addView(input)
                        addView(nextInput)
                    })
                }.root
            })
        }
        compose.runOnIdle {
            assertTrue(input.isFocusable)
            assertTrue(input.requestFocus(View.FOCUS_FORWARD))
            assertTrue(input.hasFocus())
            input.rootView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
            input.rootView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A))
            assertEquals("a", input.text.toString())
            assertSame(nextInput, input.focusSearch(View.FOCUS_FORWARD))
            assertTrue(nextInput.requestFocus(View.FOCUS_FORWARD))
            nextInput.rootView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B))
            nextInput.rootView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_B))
            assertEquals("b", nextInput.text.toString())
            assertSame(input, nextInput.focusSearch(View.FOCUS_BACKWARD))
        }
    }

    @Test fun movingFocusedNativeInputDoesNotReenterLazyComposition() {
        var moved by mutableStateOf(false)
        lateinit var input: EditText
        compose.setContent {
            val retained = remember {
                movableContentOf {
                    LazyColumn(Modifier.height(200.dp)) {
                        item {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                factory = { context -> EditText(context).also { input = it } },
                            )
                        }
                        items(20) { index ->
                            BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
                                if (index == 19) Button(onClick = {}) { Text("Next focus target") }
                                else Text("Row $index")
                            }
                        }
                    }
                }
            }
            Column {
                Box { if (!moved) retained() }
                Box { if (moved) retained() }
            }
        }
        compose.runOnIdle {
            assertTrue(input.requestFocus())
            assertTrue(input.hasFocus())
            input.setText("Keep this draft")
            moved = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(input.isAttachedToWindow)
            assertEquals("Keep this draft", input.text.toString())
            assertTrue(input.requestFocus())
        }
    }
}
