package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

@OptIn(ExperimentalLayoutApi::class)
class SettingsDialogInsetsTest {
    @Test
    fun backgroundFollowsAnimatedEditorInsetsInsteadOfItsEarlyFinalInset() {
        val state = SettingsDialogInsetsState()
        val profile = MutableWindowInsets(WindowInsets(bottom = 24))
        val editor = MutableWindowInsets(WindowInsets(bottom = 24))
        state.register(profile)
        state.register(editor)

        profile.insets = WindowInsets(bottom = 320)
        assertEquals(24, state.current!!.getBottom(Density(1f)))

        editor.insets = WindowInsets(bottom = 96)
        assertEquals(96, state.current!!.getBottom(Density(1f)))
        editor.insets = WindowInsets(bottom = 180)
        assertEquals(180, state.current!!.getBottom(Density(1f)))
    }

    @Test
    fun closingEditorReturnsControlToSurvivingProfileWindow() {
        val state = SettingsDialogInsetsState()
        val profile = MutableWindowInsets(WindowInsets(bottom = 320))
        state.register(profile)
        val profileSource = state.current
        val closeEditor = state.register(WindowInsets(bottom = 320))

        closeEditor()

        assertSame(profileSource, state.current)
        profile.insets = WindowInsets(bottom = 160)
        assertEquals(160, state.current!!.getBottom(Density(1f)))
        profile.insets = WindowInsets(bottom = 24)
        assertEquals(24, state.current!!.getBottom(Density(1f)))
    }

    @Test
    fun disposingBackgroundDoesNotReplaceTheForemostWindow() {
        val state = SettingsDialogInsetsState()
        val closeProfile = state.register(WindowInsets(bottom = 24))
        val editor = WindowInsets(bottom = 160)
        val closeEditor = state.register(editor)
        val editorSource = state.current

        closeProfile()
        assertSame(editorSource, state.current)
        closeEditor()
        assertNull(state.current)
    }

    @Test
    fun windowsSharingAnInsetsInstanceStillHaveIndependentLifetimes() {
        val state = SettingsDialogInsetsState()
        val shared = WindowInsets(bottom = 24)
        val closeFirst = state.register(shared)
        val closeSecond = state.register(shared)
        val secondSource = state.current

        closeFirst()
        closeFirst()
        assertSame(secondSource, state.current)
        closeSecond()
        assertNull(state.current)
    }

    @Test
    fun equalInsetsFromDifferentWindowsStillReplaceThePaddingSource() {
        val state = SettingsDialogInsetsState()
        state.register(WindowInsets(bottom = 24))
        val profileSource = state.current
        state.register(WindowInsets(bottom = 24))

        assertNotEquals(profileSource, state.current)
    }
}
