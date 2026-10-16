package com.sncanvas.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mind-map commands as the toolbar, the menu and a tap reach them: each one undoable step, with the selection right. */
class MindMapControllerTest {
    private val published = mutableListOf<CanvasUiState>()
    private var lastId = 0
    private val controller =
        CanvasController(
            fakeMeasurer,
            { "id-${++lastId}" },
            object : CanvasController.Listener {
                override fun onChanged() = Unit

                override fun onUiState(uiState: CanvasUiState) {
                    published += uiState
                }

                override fun onEditText(target: CanvasController.EditTarget) = Unit
            },
        )
    private val ui get() = published.last()
    private val nodes get() = controller.state.elements.filter(MindMap::isNode)
    private val arrows get() = controller.state.elements.filter { it.hasEndpoints() }

    private fun word(
        x: Double,
        y: Double,
    ) = StrokeElements.fromSamples("w-${++lastId}", (0..10).map { StrokePoint(x - 20 + it * 4.0, y, 0.5) })!!

    @Test
    fun `a tap places a node there, and tap after tap builds a branch joined by arrows`() {
        controller.tapNode(CanvasTools.ELLIPSE, Point(0.0, 0.0))
        assertEquals(1, nodes.size)
        assertEquals(Point(0.0, 0.0), MindMap.center(nodes.single()))
        assertEquals(setOf(nodes.single().id), controller.selectedIds)
        assertTrue(ui.nodeSelected)

        controller.tapNode(CanvasTools.RECTANGLE, Point(500.0, 0.0))
        assertEquals(2, nodes.size)
        val (first, second) = nodes
        assertEquals(listOf(first.id to second.id), arrows.map { it.startElementId to it.endElementId })
        assertEquals(setOf(second.id), controller.selectedIds)
        assertEquals(CanvasTools.RECTANGLE, second.type)

        // One undo takes the node and its arrow off together.
        controller.undo()
        assertEquals(1, nodes.size)
        assertTrue(arrows.isEmpty())
    }

    @Test
    fun `a tap on a node selects it, a second tap lets it go, and the next node then starts on its own`() {
        controller.tapNode(CanvasTools.ELLIPSE, Point(0.0, 0.0))
        val first = nodes.single().id
        controller.tapNode(CanvasTools.ELLIPSE, Point(500.0, 0.0))
        controller.tapNode(CanvasTools.ELLIPSE, Point(10.0, 5.0))
        assertEquals(setOf(first), controller.selectedIds)
        assertEquals(2, nodes.size)
        controller.tapNode(CanvasTools.ELLIPSE, Point(10.0, 5.0))
        assertTrue(controller.selectedIds.isEmpty())
        controller.tapNode(CanvasTools.ELLIPSE, Point(0.0, 600.0))
        assertEquals(1, arrows.size)
    }

    @Test
    fun `a node takes its size from the style panel`() {
        controller.setStyle("size", "xl")
        controller.tapNode(CanvasTools.ELLIPSE, Point(0.0, 0.0))
        assertEquals(MindMap.nodeSize(SizeStyle.XL).first, nodes.single().width, 0.0)
    }

    @Test
    fun `writing moves, is copied and is deleted with its node`() {
        controller.tapNode(CanvasTools.ELLIPSE, Point(0.0, 0.0))
        val node = nodes.single()
        controller.insert(MindMap.attachTo(word(0.0, 0.0), controller.state.elements))
        val written = controller.state.elements.single { it.points != null }
        assertEquals(node.id, written.attachedTo)

        controller.select(node.id)
        controller.moveSelected(100.0, 0.0)
        assertEquals(
            written.x + 100.0,
            controller.state.elements
                .single { it.points != null }
                .x,
            1e-9,
        )

        controller.duplicateSelected(16.0)
        val copies = controller.state.elements.filter { it.points != null }
        assertEquals(2, copies.size)
        assertEquals(nodes.map { it.id }.toSet(), copies.map { it.attachedTo }.toSet())
        // The copy of the node is what is selected, not its writing, so it can still be resized.
        assertEquals(1, controller.selectedIds.size)

        controller.deleteSelected()
        assertEquals(1, controller.state.elements.count { it.points != null })
        controller.select(node.id)
        controller.deleteSelected()
        assertTrue(controller.state.elements.isEmpty())
    }

    @Test
    fun `collapse, expand and tidy act on the node selected, and say when they apply`() {
        controller.tapNode(CanvasTools.ELLIPSE, Point(0.0, 0.0))
        val centre = nodes.single().id
        controller.tapNode(CanvasTools.ELLIPSE, Point(300.0, 0.0))
        controller.select(centre)
        controller.tapNode(CanvasTools.ELLIPSE, Point(310.0, 40.0).let { Point(it.x, it.y + 400.0) })
        controller.select(centre)
        assertTrue(ui.canCollapse)
        assertTrue(ui.canTidy)
        assertFalse(ui.canExpand)

        controller.tidySelected()
        val kids = MindMap.childrenOf(centre, controller.state.elements)
        assertEquals(2, kids.size)

        controller.collapseSelected()
        assertEquals(listOf(centre), controller.state.elements.map { it.id })
        assertTrue(ui.canExpand)
        assertFalse(ui.canCollapse)

        controller.expandSelected()
        assertEquals(3, nodes.size)
        controller.undo()
        assertEquals(1, nodes.size)
    }

    @Test
    fun `a badge tap toggles the branch, and leaves the selection on nothing it hid`() {
        controller.tapNode(CanvasTools.ELLIPSE, Point(0.0, 0.0))
        val centre = nodes.single().id
        controller.tapNode(CanvasTools.ELLIPSE, Point(400.0, 0.0))
        controller.toggleCollapsed(centre)
        assertTrue(controller.selectedIds.isEmpty())
        controller.toggleCollapsed(centre)
        assertEquals(2, nodes.size)
    }

    @Test
    fun `the selected branch is what exporting a branch exports`() {
        controller.tapNode(CanvasTools.ELLIPSE, Point(0.0, 0.0))
        val centre = nodes.single().id
        controller.tapNode(CanvasTools.ELLIPSE, Point(400.0, 0.0))
        val child = controller.selectedIds.single()
        assertEquals(listOf(child), controller.selectedBranch()?.map { it.id })
        controller.select(centre)
        assertEquals(3, controller.selectedBranch()?.size)
        controller.select(null)
        assertNull(controller.selectedBranch())
    }

    @Test
    fun `the pencil's weight is published for the toolbar`() {
        controller.republish()
        assertEquals("fine", ui.penWeight)
        controller.setPenWeight(PenWeight.BOLD)
        assertEquals("bold", ui.penWeight)
        assertEquals(PenWeight.BOLD, controller.penWeight)
    }
}
