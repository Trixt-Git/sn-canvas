package com.sncanvas.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a frame can skip, and what a gesture is changing (the speed work, PLAN.md step 1). */
class SceneCullTest {
    private val style = ShapeStyle.DEFAULT
    private val a = MindMap.newNode("a", CanvasTools.ELLIPSE, Point(0.0, 0.0), style)
    private val b = MindMap.newNode("b", CanvasTools.ELLIPSE, Point(5000.0, 0.0), style)
    private val c = MindMap.newNode("c", CanvasTools.ELLIPSE, Point(0.0, 500.0), style)
    private val ab = MindMap.arrowBetween("ab", a, b, style)
    private val elements = listOf(ab, a, b, c)
    private val screen = WorldRect(-500.0, -500.0, 500.0, 500.0)

    @Test
    fun `what is off screen is skipped, and a connector crossing the screen is not`() {
        assertTrue(SceneCull.isVisible(a, elements, screen, 0.0))
        assertFalse(SceneCull.isVisible(b, elements, screen, 0.0))
        assertTrue(SceneCull.isVisible(ab, elements, screen, 0.0))
        // Just off the edge, the margin keeps what might still poke in (an arrowhead, a thick outline).
        assertFalse(SceneCull.isVisible(c, elements, WorldRect(-500.0, -500.0, 500.0, 400.0), 0.0))
        assertTrue(SceneCull.isVisible(c, elements, WorldRect(-500.0, -500.0, 500.0, 400.0), 50.0))
    }

    @Test
    fun `a rotated shape is judged by where its corners are`() {
        val bar = Element(id = "bar", type = "rectangle", x = 0.0, y = 0.0, width = 1000.0, height = 10.0, rotation = Math.PI / 2)
        // Upright it would end at y = 10; turned, it reaches 495 down.
        assertTrue(SceneCull.isVisible(bar, listOf(bar), WorldRect(-100.0, 400.0, 1000.0, 600.0), 0.0))
    }

    @Test
    fun `nothing has changed when nothing was edited`() {
        assertEquals(emptySet<String>(), SceneCull.changedIds(elements, elements))
        assertEquals(emptySet<String>(), SceneCull.changedIds(elements, elements.toList()))
    }

    @Test
    fun `a moved node changes, and so does every connector bound to it`() {
        val moved = elements.map { if (it.id == "a") MindMap.shifted(it, 10.0, 0.0) else it }
        assertEquals(setOf("a", "ab"), SceneCull.changedIds(elements, moved))
    }

    @Test
    fun `a node added or removed counts as changed`() {
        val d = MindMap.newNode("d", CanvasTools.ELLIPSE, Point(0.0, 900.0), style)
        assertEquals(setOf("d"), SceneCull.changedIds(elements, elements + d))
        assertEquals(setOf("c"), SceneCull.changedIds(elements, elements - c))
    }
}
