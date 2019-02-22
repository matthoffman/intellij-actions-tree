package com.gitlab.lae.intellij.actions.tree

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.awt.event.KeyEvent.*
import javax.swing.KeyStroke
import javax.swing.KeyStroke.getKeyStroke

@RunWith(Parameterized::class)
class KeyStrokeLabelTest(
        private val expected: String,
        private val keyStroke: KeyStroke
) {

    companion object {

        @JvmStatic
        @Parameters(name = "{0}, {1}")
        fun data(): Collection<Any> {
            return listOf(
                    arrayOf("`", getKeyStroke(VK_BACK_QUOTE, 0)),
                    arrayOf("\\", getKeyStroke(VK_BACK_SLASH, 0)),
                    arrayOf("[", getKeyStroke(VK_OPEN_BRACKET, 0)),
                    arrayOf("]", getKeyStroke(VK_CLOSE_BRACKET, 0)),
                    arrayOf(",", getKeyStroke(VK_COMMA, 0)),
                    arrayOf("=", getKeyStroke(VK_EQUALS, 0)),
                    arrayOf("-", getKeyStroke(VK_MINUS, 0)),
                    arrayOf(".", getKeyStroke(VK_PERIOD, 0)),
                    arrayOf("'", getKeyStroke(VK_QUOTE, 0)),
                    arrayOf(";", getKeyStroke(VK_SEMICOLON, 0)),
                    arrayOf("/", getKeyStroke(VK_SLASH, 0)),
                    arrayOf("@", getKeyStroke('@')),
                    arrayOf("<", getKeyStroke('<')),
                    arrayOf("|", getKeyStroke('|'))
            )
        }
    }

    @Test
    fun test() {
        assertEquals(expected, getKeyText(keyStroke))
    }
}