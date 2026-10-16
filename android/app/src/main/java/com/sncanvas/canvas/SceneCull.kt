package com.sncanvas.canvas

/**
 * What a frame can skip (PLAN.md step 1): on an infinite canvas most of the
 * content is usually off screen, and drawing it costs as much as drawing what
 * shows. Also which elements a gesture is changing, so everything else can come
 * from the cached drawing of the canvas ([SceneCache]) instead of being drawn
 * again on every move.
 *
 * Pure: the view hands over elements and rectangles in world units.
 */
object SceneCull {
    /**
     * Room left around every element's box for what draws outside it, in screen pixels: an arrowhead
     * (at most 60 px), a thick outline's outer half, the hand-drawn wobble.
     */
    const val MARGIN_PX = 72.0

    /**
     * The world rectangle [element] draws inside, generously: its box (rotated, for a rotated one), or
     * for a connector the box round its two resolved ends, plus its own line width.
     */
    fun bounds(
        element: Element,
        elements: List<Element>,
    ): WorldRect {
        val corners =
            if (element.hasEndpoints()) {
                CanvasCore
                    .resolveArrowEndpoints(
                        element,
                        elements,
                    ).toList()
            } else {
                CanvasCore.cornerPoints(element)
            }
        val box = ViewTransforms.boundsOf(corners)
        val line = maxOf(element.strokeWidth ?: 0.0, element.style.size.strokeWidth)
        return WorldRect(box.left - line, box.top - line, box.right + line, box.bottom + line)
    }

    /** Whether [element] can show anywhere in [visible] (world units), widened by [margin] world units on every side. */
    fun isVisible(
        element: Element,
        elements: List<Element>,
        visible: WorldRect,
        margin: Double,
    ): Boolean {
        val box = bounds(element, elements)
        return box.right >= visible.left - margin &&
            box.left <= visible.right + margin &&
            box.bottom >= visible.top - margin &&
            box.top <= visible.bottom + margin
    }

    /**
     * The ids of the elements [shown] draws differently from [held]: what a gesture in progress is
     * changing (moved, resized, faded by the eraser...), plus every connector bound to one of them, since
     * a connector follows the shapes it is bound to without itself changing. Everything else can come
     * from the drawing of [held] as it was.
     */
    fun changedIds(
        held: List<Element>,
        shown: List<Element>,
    ): Set<String> {
        if (held === shown) return emptySet()
        val changed = HashSet<String>()
        if (held.size == shown.size) {
            for (i in held.indices) {
                val was = held[i]
                val now = shown[i]
                if (was !== now) {
                    changed += was.id
                    changed += now.id
                }
            }
        } else {
            val old = held.associateBy { it.id }
            for (now in shown) if (old[now.id] !== now) changed += now.id
            val ids = shown.map { it.id }.toSet()
            for (was in held) if (was.id !in ids) changed += was.id
        }
        if (changed.isEmpty()) return changed
        for (element in shown) {
            if (element.startElementId in changed || element.endElementId in changed) changed += element.id
        }
        return changed
    }
}
