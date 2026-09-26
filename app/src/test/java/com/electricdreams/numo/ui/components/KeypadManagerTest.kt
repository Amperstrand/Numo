package com.electricdreams.numo.ui.components

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.GridLayout
import androidx.test.core.app.ApplicationProvider
import com.electricdreams.numo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeypadManagerTest {

    private lateinit var context: Context
    private lateinit var keypad: GridLayout

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_Numo,
        )
        keypad = GridLayout(context)
        KeypadManager(context, keypad) {}
    }

    private fun key(label: String): Button = (0 until keypad.childCount)
        .map { keypad.getChildAt(it) as Button }
        .first { it.text == label }

    @Test
    fun `backspace and clear keys are named for TalkBack`() {
        val delete = context.getString(R.string.keypad_delete_content_description)
        val clear = context.getString(R.string.keypad_clear_content_description)

        assertEquals(delete, key("<").contentDescription)
        assertEquals(clear, key("C").contentDescription)
    }

    @Test
    fun `digit keys are read by their label`() {
        (0..9).forEach { digit -> assertNull(key(digit.toString()).contentDescription) }
    }
}
