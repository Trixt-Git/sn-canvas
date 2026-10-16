package com.sncanvas.canvas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Supernote's gesture eraser, copied into the canvas: two still fingers in a right-hand corner make the pen erase. */
class GestureEraserTest {
    // A Manta-sized view at 300 ppi: about 0.085 mm per pixel, so 30 mm is about 354 px.
    private val eraser = GestureEraser(mmPerPx = { 25.4 / 300 }, viewSize = { 1920 to 2560 })
    private val mm = 300 / 25.4

    private fun twoFingers(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        gapMs: Long = 50,
        penDown: Boolean = false,
    ) {
        eraser.fingerDown(1, x1.toFloat(), y1.toFloat(), 1_000, penDown)
        eraser.fingerDown(2, x2.toFloat(), y2.toFloat(), 1_000 + gapMs, penDown)
    }

    @Test
    fun `two fingers held in the top or bottom right corner turn it on`() {
        twoFingers(1800.0, 200.0, 1800.0 - 30 * mm, 200.0)
        assertTrue(eraser.isActive)
        val bottom = GestureEraser({ 25.4 / 300 }, { 1920 to 2560 })
        bottom.fingerDown(1, 1850f, 2400f, 0, false)
        bottom.fingerDown(2, (1850 - 30 * mm).toFloat(), 2400f, 100, false)
        assertTrue(bottom.isActive)
    }

    @Test
    fun `not on the left, not in the middle, not split between corners`() {
        twoFingers(100.0, 200.0, 100.0 + 30 * mm, 200.0)
        assertFalse(eraser.isActive)
        val middle = GestureEraser({ 25.4 / 300 }, { 1920 to 2560 })
        middle.fingerDown(1, 1800f, 1280f, 0, false)
        middle.fingerDown(2, (1800 - 30 * mm).toFloat(), 1280f, 0, false)
        assertFalse(middle.isActive)
        val split = GestureEraser({ 25.4 / 300 }, { 1920 to 2560 })
        split.fingerDown(1, 1800f, 200f, 0, false)
        split.fingerDown(2, 1800f, 2400f, 0, false)
        assertFalse(split.isActive)
    }

    @Test
    fun `the fingers have to land together, a sensible distance apart, and before the pen`() {
        twoFingers(1800.0, 200.0, 1800.0 - 30 * mm, 200.0, gapMs = 400)
        assertFalse(eraser.isActive)
        val close = GestureEraser({ 25.4 / 300 }, { 1920 to 2560 })
        close.fingerDown(1, 1800f, 200f, 0, false)
        close.fingerDown(2, (1800 - 5 * mm).toFloat(), 200f, 0, false)
        assertFalse(close.isActive)
        val far = GestureEraser({ 25.4 / 300 }, { 1920 to 2560 })
        far.fingerDown(1, 1900f, 100f, 0, false)
        far.fingerDown(2, (1900 - 60 * mm).toFloat(), 100f, 0, false)
        assertFalse(far.isActive)
        val afterPen = GestureEraser({ 25.4 / 300 }, { 1920 to 2560 })
        afterPen.fingerDown(1, 1800f, 200f, 0, true)
        afterPen.fingerDown(2, (1800 - 30 * mm).toFloat(), 200f, 0, true)
        assertFalse(afterPen.isActive)
    }

    @Test
    fun `moving the fingers lets it go, so they can pinch`() {
        twoFingers(1800.0, 200.0, 1800.0 - 30 * mm, 200.0)
        eraser.fingerMove(1, 1801f, 201f)
        assertTrue(eraser.isActive)
        eraser.fingerMove(1, (1800 + 10 * mm).toFloat(), 200f)
        assertFalse(eraser.isActive)
    }

    @Test
    fun `lifting a finger turns it off`() {
        twoFingers(1800.0, 200.0, 1800.0 - 30 * mm, 200.0)
        eraser.fingerUp(2)
        assertFalse(eraser.isActive)
    }

    @Test
    fun `when the system takes the fingers away for the pen, it lasts for that one stroke`() {
        twoFingers(1800.0, 200.0, 1800.0 - 30 * mm, 200.0)
        eraser.cancelled()
        assertTrue(eraser.isActive)
        eraser.penLifted()
        assertFalse(eraser.isActive)
    }

    @Test
    fun `a pen stroke with the fingers still down leaves it on`() {
        twoFingers(1800.0, 200.0, 1800.0 - 30 * mm, 200.0)
        eraser.penLifted()
        assertTrue(eraser.isActive)
    }
}
