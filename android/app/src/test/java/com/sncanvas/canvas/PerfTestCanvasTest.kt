package com.sncanvas.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The speed test map: heavy, always the same, and made of what a real mind map holds. */
class PerfTestCanvasTest {
    private val elements = PerfTestCanvas.elements()
    private val nodes = elements.filter { it.type == CanvasTools.RECTANGLE || it.type == CanvasTools.ELLIPSE }
    private val arrows = elements.filter { it.type == CanvasTools.ARROW }
    private val ink = elements.filter { it.type == CanvasTools.DRAW }

    @Test
    fun `the map is exactly the same every time, so timings compare fairly`() {
        assertEquals(elements, PerfTestCanvas.elements())
    }

    @Test
    fun `a hundred nodes, an arrow to each but the centre, and two words of ink in each`() {
        assertEquals(100, nodes.size)
        assertEquals(99, arrows.size)
        assertEquals(200, ink.size)
        assertEquals(elements.size, elements.map { it.id }.toSet().size)
    }

    @Test
    fun `every arrow is bound to two nodes, so it follows them as they move`() {
        val ids = nodes.map { it.id }.toSet()
        assertTrue(arrows.all { it.startElementId in ids && it.endElementId in ids })
    }

    @Test
    fun `nodes are wide 2 to 1 and drawn with the hand-drawn outline`() {
        assertTrue(nodes.all { it.width == it.height * 2 })
        assertTrue(nodes.all { it.style.dash == DashStyle.DRAW })
    }

    @Test
    fun `no two nodes overlap and each word sits inside its own node`() {
        fun overlap(
            a: Element,
            b: Element,
        ) = a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
        val overlapping = nodes.indices.sumOf { i -> (i + 1 until nodes.size).count { j -> overlap(nodes[i], nodes[j]) } }
        assertEquals(0, overlapping)
        val byId = nodes.associateBy { it.id }
        assertTrue(
            ink.all { word ->
                val node = byId.getValue("perf-node-" + word.id.removePrefix("perf-ink-").substringBefore("-"))
                word.x >= node.x &&
                    word.x + word.width <= node.x + node.width &&
                    word.y >= node.y &&
                    word.y + word.height <= node.y + node.height
            },
        )
    }

    @Test
    fun `arrows come first, so the nodes and their ink draw over them`() {
        assertEquals(arrows, elements.take(arrows.size))
    }

    @Test
    fun `it survives a save and a load`() {
        assertEquals(elements.size, CanvasJson.deserializeElements(CanvasJson.serializeElements(elements)).size)
    }
}
