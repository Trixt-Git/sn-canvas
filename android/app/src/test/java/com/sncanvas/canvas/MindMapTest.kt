package com.sncanvas.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Mind maps (Wil's fork): nodes, branches read off the arrows, writing that sticks to nodes, collapsing and tidying. */
class MindMapTest {
    private val style = ShapeStyle.DEFAULT

    private fun node(
        id: String,
        cx: Double,
        cy: Double,
    ) = MindMap.newNode(id, CanvasTools.ELLIPSE, Point(cx, cy), style)

    private fun arrow(
        from: Element,
        to: Element,
    ) = MindMap.arrowBetween("${from.id}->${to.id}", from, to, style)

    /** A short scribble across ([x], [y]), as a written word. */
    private fun word(
        id: String,
        x: Double,
        y: Double,
    ) = StrokeElements.fromSamples(id, (0..10).map { StrokePoint(x - 20 + it * 4.0, y + (it % 2) * 6.0, 0.5) })!!

    // centre -> a -> a1, centre -> b
    private val centre = node("centre", 0.0, 0.0)
    private val a = node("a", 400.0, 0.0)
    private val b = node("b", -400.0, 0.0)
    private val a1 = node("a1", 800.0, 0.0)
    private val map = listOf(arrow(centre, a), arrow(centre, b), arrow(a, a1), centre, a, b, a1)

    @Test
    fun `nodes are plain rectangles and ellipses, sized 2 to 1 by the style size`() {
        assertTrue(MindMap.isNode(centre))
        assertTrue(MindMap.isNode(MindMap.newNode("r", CanvasTools.RECTANGLE, Point(0.0, 0.0), style)))
        assertFalse(MindMap.isNode(arrow(centre, a)))
        assertFalse(MindMap.isNode(TableElements.create("t", Point(0.0, 0.0), Point(320.0, 96.0))))
        SizeStyle.entries.forEach { size ->
            val (width, height) = MindMap.nodeSize(size)
            assertEquals(2.0, width / height, 0.0)
        }
        val big = MindMap.newNode("n", CanvasTools.ELLIPSE, Point(10.0, 20.0), style.copy(size = SizeStyle.XL))
        assertEquals(Point(10.0, 20.0), MindMap.center(big))
        assertTrue(MindMap.nodeSize(SizeStyle.S).first < MindMap.nodeSize(SizeStyle.XL).first)
    }

    @Test
    fun `the arrow between two nodes is bound at both ends and never filled`() {
        val joined = MindMap.arrowBetween("j", centre, a, style.copy(fill = FillStyle.SOLID))
        assertEquals(listOf("centre", "a"), listOf(joined.startElementId, joined.endElementId))
        assertEquals(FillStyle.NONE, joined.style.fill)
        assertEquals(CanvasTools.ARROW, joined.type)
    }

    @Test
    fun `the tree is read off the arrows`() {
        assertEquals(listOf("a", "b"), MindMap.childrenOf("centre", map).map { it.id })
        assertEquals("centre", MindMap.parentOf("a", map)?.id)
        assertNull(MindMap.parentOf("centre", map))
        assertEquals(setOf("a", "b", "a1"), MindMap.descendantsOf("centre", map))
        assertEquals(setOf("a1"), MindMap.descendantsOf("a", map))
        assertEquals(emptySet<String>(), MindMap.descendantsOf("a1", map))
    }

    @Test
    fun `a map that loops back on itself still ends`() {
        val looped = map + arrow(a1, centre)
        assertEquals(setOf("a", "b", "a1"), MindMap.descendantsOf("centre", looped))
    }

    @Test
    fun `writing mostly inside a node is attached to it, and writing outside to nothing`() {
        assertEquals("a", MindMap.attachTo(word("w", 400.0, 0.0), map).attachedTo)
        assertNull(MindMap.attachTo(word("w", 200.0, 300.0), map).attachedTo)
        // A shape, not writing, comes back as it was.
        assertSame(centre, MindMap.attachTo(centre, map))
    }

    @Test
    fun `writing goes where its node goes, by the node's centre, and is never scaled`() {
        val w = MindMap.attachTo(word("w", 400.0, 0.0), map)
        val before = map + w
        val moved = before.map { if (it.id == "a") MindMap.shifted(it, 50.0, -20.0) else it }
        val after = MindMap.follow(before, moved).first { it.id == "w" }
        assertEquals(w.x + 50.0, after.x, 1e-9)
        assertEquals(w.y - 20.0, after.y, 1e-9)
        assertEquals(w.width, after.width, 1e-9)
        // A resize from one corner moves the centre by half as much, and the writing with it.
        val resized = before.map { if (it.id == "a") it.copy(width = it.width + 100.0) else it }
        assertEquals(w.x + 50.0, MindMap.follow(before, resized).first { it.id == "w" }.x, 1e-9)
    }

    @Test
    fun `writing goes when its node goes, and writing moved on its own finds its new node`() {
        val w = MindMap.attachTo(word("w", 400.0, 0.0), map)
        val before = map + w
        assertTrue(MindMap.follow(before, before.filterNot { it.id == "a" }).none { it.id == "w" })
        val draggedToB = before.map { if (it.id == "w") MindMap.shifted(it, -800.0, 0.0) else it }
        assertEquals("b", MindMap.follow(before, draggedToB).first { it.id == "w" }.attachedTo)
    }

    @Test
    fun `writing moved together with its node is not moved twice`() {
        val w = MindMap.attachTo(word("w", 400.0, 0.0), map)
        val before = map + w
        val both = before.map { if (it.id == "a" || it.id == "w") MindMap.shifted(it, 10.0, 0.0) else it }
        assertEquals(w.x + 10.0, MindMap.follow(before, both).first { it.id == "w" }.x, 1e-9)
    }

    @Test
    fun `collapsing folds the branch into the node and expanding brings it back beside it`() {
        val w = MindMap.attachTo(word("w", 800.0, 0.0), map)
        val start = map + w
        assertTrue(MindMap.canCollapse(a, start))
        val folded = MindMap.collapse("a", start)
        // a1, the arrow to it and the writing in it are gone from the canvas, and kept in a.
        assertEquals(listOf("centre->a", "centre->b", "centre", "a", "b"), folded.map { it.id })
        val held = folded.first { it.id == "a" }
        assertEquals(setOf("a->a1", "a1", "w"), held.collapsed!!.map { it.id }.toSet())
        assertEquals(1, MindMap.hiddenCount(held))
        assertTrue(MindMap.canExpand(held))
        assertFalse(MindMap.canCollapse(held, folded))

        // Moved while collapsed: the branch comes back moved by as much.
        val moved = folded.map { if (it.id == "a") MindMap.shifted(it, 0.0, 100.0) else it }
        val opened = MindMap.expand("a", moved)
        assertEquals(start.map { it.id }.toSet(), opened.map { it.id }.toSet())
        assertEquals(100.0, MindMap.center(opened.first { it.id == "a1" }).y, 1e-9)
        assertEquals(w.y + 100.0, opened.first { it.id == "w" }.y, 1e-9)
        assertNull(opened.first { it.id == "a" }.collapsed)
    }

    @Test
    fun `collapsing a node with nothing below it changes nothing`() {
        assertSame(map, MindMap.collapse("a1", map))
        assertSame(map, MindMap.expand("a1", map))
        assertSame(map, MindMap.collapse("nobody", map))
    }

    @Test
    fun `a collapsed branch inside a collapsed branch comes back whole`() {
        val inner = MindMap.collapse("a", map)
        val outer = MindMap.collapse("centre", inner)
        assertEquals(listOf("centre"), outer.map { it.id })
        // a and b, and a1 folded inside a.
        assertEquals(3, MindMap.hiddenCount(outer.single()))
        val back = MindMap.expand("a", MindMap.expand("centre", outer))
        assertEquals(map.map { it.id }.toSet(), back.map { it.id }.toSet())
    }

    @Test
    fun `a collapsed branch survives a save and a load`() {
        val folded = MindMap.collapse("a", map + MindMap.attachTo(word("w", 800.0, 0.0), map))
        val loaded = CanvasJson.deserializeElements(CanvasJson.serializeElements(folded))
        val held = loaded.first { it.id == "a" }
        assertEquals(setOf("a->a1", "a1", "w"), held.collapsed!!.map { it.id }.toSet())
        assertEquals("a1", held.collapsed!!.first { it.id == "w" }.attachedTo)
        assertNotNull(held.collapsedCenter)
        assertEquals(MindMap.expand("a", folded).map { it.id }, MindMap.expand("a", loaded).map { it.id })
    }

    @Test
    fun `a branch is the node, everything below it, the arrows between them and their writing`() {
        val w = MindMap.attachTo(word("w", 800.0, 0.0), map)
        assertEquals(listOf("a->a1", "a", "a1", "w"), MindMap.branch("a", map + w).map { it.id })
        assertEquals(listOf("b"), MindMap.branch("b", map).map { it.id })
    }

    @Test
    fun `tidying spaces a root's children all the way round, clear of each other`() {
        val crowded = listOf(centre) + (1..6).map { node("k$it", 50.0 * it, 30.0) }.flatMap { listOf(arrow(centre, it), it) }
        val tidied = MindMap.tidy("centre", crowded)
        val kids = MindMap.childrenOf("centre", tidied)
        val distances = kids.map { hypot(MindMap.center(it).x, MindMap.center(it).y) }
        distances.forEach { assertEquals(distances.first(), it, 1e-6) }
        for (i in kids.indices) {
            for (j in i + 1 until kids.size) {
                val p = kids[i]
                val q = kids[j]
                val overlap = p.x < q.x + q.width && q.x < p.x + p.width && p.y < q.y + q.height && q.y < p.y + p.height
                assertFalse("${p.id} and ${q.id} overlap", overlap)
            }
        }
    }

    @Test
    fun `tidying fans a child's children out away from its parent, taking their branches and writing along`() {
        val a2 = node("a2", 700.0, 300.0)
        val a1kid = node("a1kid", 1200.0, 0.0)
        val w = MindMap.attachTo(word("w", 1200.0, 0.0), listOf(a1kid))
        val start = map + listOf(arrow(a, a2), a2, arrow(a1, a1kid), a1kid, w)
        val tidied = MindMap.tidy("a", start)
        // Both children end up further right than a: on the side away from the centre.
        MindMap.childrenOf("a", tidied).forEach { assertTrue(MindMap.center(it).x > MindMap.center(a).x) }
        // a1's own child moved by as much as a1 did, and its writing with it.
        val a1Moved = MindMap.center(tidied.first { it.id == "a1" }).x - MindMap.center(a1).x
        val kidMoved = MindMap.center(tidied.first { it.id == "a1kid" }).x - MindMap.center(a1kid).x
        assertEquals(a1Moved, kidMoved, 1e-6)
        assertEquals(w.x + kidMoved, tidied.first { it.id == "w" }.x, 1e-6)
    }

    @Test
    fun `tidying a node with no children changes nothing`() {
        assertSame(map, MindMap.tidy("a1", map))
    }

    @Test
    fun `a collapsed node always shows its badge, and a node with a branch shows one only when selected alone`() {
        val folded = MindMap.collapse("a", map)
        val held = folded.first { it.id == "a" }
        assertTrue(MindMap.showsBadge(held, folded, emptySet()))
        assertFalse(MindMap.showsBadge(centre, map, emptySet()))
        assertTrue(MindMap.showsBadge(centre, map, setOf("centre")))
        assertFalse(MindMap.showsBadge(centre, map, setOf("centre", "a")))
        assertFalse(MindMap.showsBadge(a1, map, setOf("a1")))
        val badge = MindMap.branchBadgePoint(held, 1.0)
        assertEquals("a", MindMap.branchBadgeAt(badge, folded, emptySet(), 1.0, 10.0)?.id)
        assertNull(MindMap.branchBadgeAt(Point(badge.x + 50, badge.y), folded, emptySet(), 1.0, 10.0))
    }
}
