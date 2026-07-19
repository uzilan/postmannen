package postmannen.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatTextWrapTest {

    @Test
    fun `short line is left unchanged`() {
        assertEquals("hello", wrapText("hello", 20))
    }

    @Test
    fun `wraps at word boundaries`() {
        assertEquals("hello\nworld", wrapText("hello world", 6))
    }

    @Test
    fun `hard-breaks a single word longer than width`() {
        assertEquals("abcde\nfg", wrapText("abcdefg", 5))
    }

    @Test
    fun `preserves existing newlines and blank lines`() {
        assertEquals("a\n\nb", wrapText("a\n\nb", 10))
    }

    @Test
    fun `non-positive width returns text unchanged`() {
        assertEquals("hello world", wrapText("hello world", 0))
    }
}
