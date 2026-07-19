package postmannen.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColumnResizeTest {

    @Test
    fun `positive delta grows left and shrinks right by the same amount`() {
        assertEquals(30 to 20, resizeColumns(leftWidth = 25, rightWidth = 25, deltaColumns = 5, minWidth = 10))
    }

    @Test
    fun `negative delta shrinks left and grows right by the same amount`() {
        assertEquals(20 to 30, resizeColumns(leftWidth = 25, rightWidth = 25, deltaColumns = -5, minWidth = 10))
    }

    @Test
    fun `zero delta leaves widths unchanged`() {
        assertEquals(25 to 25, resizeColumns(leftWidth = 25, rightWidth = 25, deltaColumns = 0, minWidth = 10))
    }

    @Test
    fun `clamps left width at the minimum when dragged too far left`() {
        assertEquals(10 to 40, resizeColumns(leftWidth = 25, rightWidth = 25, deltaColumns = -100, minWidth = 10))
    }

    @Test
    fun `clamps right width at the minimum when dragged too far right`() {
        assertEquals(40 to 10, resizeColumns(leftWidth = 25, rightWidth = 25, deltaColumns = 100, minWidth = 10))
    }

    @Test
    fun `total space preserved across a clamped resize`() {
        val (left, right) = resizeColumns(leftWidth = 12, rightWidth = 8, deltaColumns = 3, minWidth = 10)
        assertEquals(20, left + right)
    }

    @Test
    fun `returns input unchanged when total space is below twice the minimum`() {
        assertEquals(9 to 8, resizeColumns(leftWidth = 9, rightWidth = 8, deltaColumns = 5, minWidth = 10))
    }
}
