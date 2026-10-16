package com.sncanvas.canvas

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Mind maps on the canvas (Wil's fork, PLAN.md steps 4-11). A **node** is a
 * plain rectangle or ellipse. A **branch** runs along connectors: a line or
 * arrow bound at its start to node A and at its end to node B makes B a child
 * of A. Freehand writing can be **attached** to the node it was written in, so
 * it moves and is deleted with that node. A node can be **collapsed**, which
 * takes its whole branch out of the canvas and keeps it inside the node until
 * it is expanded again.
 *
 * Everything here is pure, like [CanvasCore]: it takes element lists and gives
 * back new ones, and [CanvasController] makes each result one undo step.
 * Nothing is stored about the tree itself; it is read off the connectors every
 * time, so a branch drawn by hand with the arrow tool counts exactly like one
 * the auto-arrow made.
 */
@Suppress("TooManyFunctions") // one home for the mind-map rules, each small
object MindMap {
    /** How much of a stroke has to fall inside a node for it to count as written in that node. */
    const val ATTACH_SHARE = 0.6

    /** The gap tidying leaves between neighbouring children, and between a node and its children, in world units. */
    const val TIDY_GAP = 48.0

    /** The widest fan tidying spreads a node's children over, when the node has a parent of its own. */
    private const val MAX_FAN = PI * 1.2

    /** How far apart tidying spreads neighbouring children in a fan, before the fan gets as wide as it may. */
    private const val FAN_STEP = PI / 6

    /**
     * A tapped node's width and height for each style size, in world units: wide 2:1, so a handwritten
     * label has room to run across it. World units, not screen pixels, so a node is the same size on the
     * canvas whatever the zoom was when it was placed.
     */
    fun nodeSize(size: SizeStyle): Pair<Double, Double> =
        when (size) {
            SizeStyle.S -> 160.0 to 80.0
            SizeStyle.M -> 240.0 to 120.0
            SizeStyle.L -> 320.0 to 160.0
            SizeStyle.XL -> 440.0 to 220.0
        }

    /** Whether [element] is a mind-map node: a plain rectangle or ellipse (not an image, a table or text). */
    fun isNode(element: Element): Boolean =
        (element.type == CanvasTools.RECTANGLE || element.type == CanvasTools.ELLIPSE) &&
            !element.hasEndpoints() &&
            element.image == null &&
            element.table == null

    fun center(element: Element): Point = Point(element.x + element.width / 2, element.y + element.height / 2)

    /** The topmost node under [world], or null. */
    fun nodeAt(
        world: Point,
        elements: List<Element>,
    ): Element? = elements.lastOrNull { isNode(it) && it.containsPoint(world.x, world.y) }

    /** A new node of [type] (rectangle or ellipse) centred on [center], [size]d by its style, in [style]. */
    fun newNode(
        id: String,
        type: String,
        center: Point,
        style: ShapeStyle,
    ): Element {
        val (width, height) = nodeSize(style.size)
        return Element(
            id = id,
            type = type,
            x = center.x - width / 2,
            y = center.y - height / 2,
            width = width,
            height = height,
            style = style,
        )
    }

    /** An arrow from [from]'s centre to [to]'s, both ends bound, so it follows either node as it moves. */
    fun arrowBetween(
        id: String,
        from: Element,
        to: Element,
        style: ShapeStyle,
    ): Element {
        val start = center(from)
        val end = center(to)
        return Element(
            id = id,
            type = CanvasTools.ARROW,
            startX = start.x,
            startY = start.y,
            endX = end.x,
            endY = end.y,
            startElementId = from.id,
            endElementId = to.id,
            style = style.copy(fill = FillStyle.NONE),
        )
    }

    // ---- Writing attached to nodes -------------------------------------------------------------

    /**
     * [stroke], attached to the topmost node at least [ATTACH_SHARE] of it was written inside, or to
     * nothing when it was written in no node. Anything but a freehand stroke comes back as it is.
     */
    fun attachTo(
        stroke: Element,
        elements: List<Element>,
    ): Element {
        val points = stroke.points ?: return stroke
        if (points.isEmpty()) return stroke.copy(attachedTo = null)
        val world = StrokeElements.worldPoints(stroke).map { stroke.toWorld(it.x, it.y) }
        val node =
            elements.lastOrNull { candidate ->
                candidate.id != stroke.id &&
                    isNode(candidate) &&
                    world.count { candidate.containsPoint(it.x, it.y) } >= world.size * ATTACH_SHARE
            }
        return stroke.copy(attachedTo = node?.id)
    }

    /** The writing attached to any of the nodes in [nodeIds]. */
    fun attachmentsOf(
        nodeIds: Set<String>,
        elements: List<Element>,
    ): List<Element> = elements.filter { it.attachedTo != null && it.attachedTo in nodeIds }

    /**
     * [after], an edit of [before], with every node's writing kept with it: writing attached to a node
     * whose centre moved moves by as much (a resize moves it with the centre, never scales it); writing
     * attached to a node that is gone goes too; and writing moved on its own is attached again to
     * whatever node it now sits in. Writing the edit already moved itself is left where it put it.
     */
    fun follow(
        before: List<Element>,
        after: List<Element>,
    ): List<Element> {
        if (before === after || after.none { it.points != null }) return after
        val old = before.associateBy { it.id }
        val nodesAfter = after.filter(::isNode).associateBy { it.id }
        val moved = HashMap<String, Point>()
        for ((id, node) in nodesAfter) {
            val was = old[id]?.takeIf(::isNode) ?: continue
            val delta = Point(center(node).x - center(was).x, center(node).y - center(was).y)
            if (delta.x != 0.0 || delta.y != 0.0) moved[id] = delta
        }
        val gone =
            old.values
                .filter { isNode(it) && it.id !in nodesAfter }
                .map { it.id }
                .toSet()
        return after.mapNotNull { element ->
            val owner = element.attachedTo
            val previous = old[element.id]
            when {
                element.points == null -> element
                owner != null && owner in gone -> null
                // Carried: unchanged by the edit itself, so it goes where its node went.
                owner != null && previous === element && owner in moved -> moved.getValue(owner).let { shifted(element, it.x, it.y) }
                // Moved on its own: it belongs to whatever node it was put down in now.
                previous != null && previous !== element && hasMoved(previous, element) -> attachTo(element, after)
                else -> element
            }
        }
    }

    private fun hasMoved(
        was: Element,
        now: Element,
    ): Boolean = was.x != now.x || was.y != now.y || was.width != now.width || was.height != now.height

    // ---- The tree --------------------------------------------------------------------------

    /** [id]'s children: the nodes at the far end of connectors that start at it, in canvas order. */
    fun childrenOf(
        id: String,
        elements: List<Element>,
    ): List<Element> {
        val nodes = elements.filter(::isNode).associateBy { it.id }
        return elements
            .filter { it.hasEndpoints() && it.startElementId == id && it.endElementId != id }
            .mapNotNull { nodes[it.endElementId] }
            .distinctBy { it.id }
    }

    /** [id]'s parent: the node at the start of the first connector that ends at it, or null for a root. */
    fun parentOf(
        id: String,
        elements: List<Element>,
    ): Element? {
        val nodes = elements.filter(::isNode).associateBy { it.id }
        return elements
            .filter { it.hasEndpoints() && it.endElementId == id && it.startElementId != id }
            .firstNotNullOfOrNull { nodes[it.startElementId] }
    }

    /** Every node below [id], however deep, not counting [id] itself; safe on maps that loop back on themselves. */
    fun descendantsOf(
        id: String,
        elements: List<Element>,
    ): Set<String> {
        val children = HashMap<String, MutableList<String>>()
        val nodeIds = elements.filter(::isNode).map { it.id }.toSet()
        for (connector in elements) {
            val from = connector.startElementId ?: continue
            val to = connector.endElementId ?: continue
            if (connector.hasEndpoints() &&
                from in nodeIds &&
                to in nodeIds &&
                from != to
            ) {
                children.getOrPut(from) { mutableListOf() } += to
            }
        }
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque(children[id].orEmpty())
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (next == id || !seen.add(next)) continue
            queue.addAll(children[next].orEmpty())
        }
        return seen
    }

    /**
     * [id] and everything below it, in canvas order: its nodes, the connectors between them, and the
     * writing in them. What exporting a branch exports.
     */
    fun branch(
        id: String,
        elements: List<Element>,
    ): List<Element> {
        val nodes = descendantsOf(id, elements) + id
        return elements.filter { element ->
            element.id in nodes ||
                (element.attachedTo != null && element.attachedTo in nodes) ||
                (element.hasEndpoints() && element.startElementId in nodes && element.endElementId in nodes)
        }
    }

    // ---- Collapsing --------------------------------------------------------------------------

    fun canCollapse(
        element: Element,
        elements: List<Element>,
    ): Boolean = isNode(element) && element.collapsed == null && childrenOf(element.id, elements).isNotEmpty()

    fun canExpand(element: Element): Boolean = element.collapsed != null

    /**
     * [elements] with node [id]'s branch folded away: every node below it, every connector touching
     * one of them, and the writing in them, taken out and kept inside the node. Nothing to fold (no
     * children, already collapsed, no such node) leaves the elements as they were.
     */
    fun collapse(
        id: String,
        elements: List<Element>,
    ): List<Element> {
        val node = elements.find { it.id == id }?.takeIf { canCollapse(it, elements) } ?: return elements
        val below = descendantsOf(id, elements)
        val hidden =
            elements.filter { element ->
                element.id in below ||
                    (element.attachedTo != null && element.attachedTo in below) ||
                    (element.hasEndpoints() && (element.startElementId in below || element.endElementId in below))
            }
        val hiddenIds = hidden.map { it.id }.toSet()
        val folded = node.copy(collapsed = hidden, collapsedCenter = center(node))
        return elements.filterNot { it.id in hiddenIds }.map { if (it.id == id) folded else it }
    }

    /**
     * [elements] with collapsed node [id]'s branch back, just after the node, moved by however far the
     * node has moved since it was collapsed. An element whose id is somehow already on the canvas again
     * stays folded away rather than appear twice.
     */
    fun expand(
        id: String,
        elements: List<Element>,
    ): List<Element> {
        val index = elements.indexOfFirst { it.id == id }
        val node = elements.getOrNull(index) ?: return elements
        val hidden = node.collapsed ?: return elements
        val was = node.collapsedCenter ?: center(node)
        val now = center(node)
        val present = elements.map { it.id }.toSet()
        val restored = hidden.filterNot { it.id in present }.map { shifted(it, now.x - was.x, now.y - was.y) }
        val opened = node.copy(collapsed = null, collapsedCenter = null)
        return elements.take(index) + opened + restored + elements.drop(index + 1)
    }

    /** How many nodes a collapsed [node] is hiding, however deep, for the badge that says so. */
    fun hiddenCount(node: Element): Int = node.collapsed.orEmpty().sumOf { (if (isNode(it)) 1 else 0) + hiddenCount(it) }

    /** How far past a node's right edge its branch badge sits, in screen pixels, so it is as easy to tap at any zoom. */
    const val BADGE_OFFSET_PX = 40.0

    /** Where [node]'s branch badge sits: just past its right edge, halfway down. */
    fun branchBadgePoint(
        node: Element,
        zoom: Double,
    ): Point = node.toWorld(node.x + node.width + BADGE_OFFSET_PX / zoom, node.y + node.height / 2)

    /**
     * Whether [node] shows a branch badge: always when it is collapsed ("+3", tap to bring the branch
     * back), and when it is selected on its own with a branch to fold away ("−", tap to fold it).
     */
    fun showsBadge(
        node: Element,
        elements: List<Element>,
        selectedIds: Set<String>,
    ): Boolean = canExpand(node) || (selectedIds.size == 1 && node.id in selectedIds && canCollapse(node, elements))

    /** The topmost node whose branch badge is within [tolerance] of [world], or null. */
    fun branchBadgeAt(
        world: Point,
        elements: List<Element>,
        selectedIds: Set<String>,
        zoom: Double,
        tolerance: Double,
    ): Element? =
        elements.lastOrNull { node ->
            isNode(node) &&
                showsBadge(node, elements, selectedIds) &&
                branchBadgePoint(node, zoom).let { hypot(it.x - world.x, it.y - world.y) } <= tolerance
        }

    // ---- Tidying -----------------------------------------------------------------------------

    /**
     * [elements] with node [id]'s children spaced evenly around it, each taking its own branch with
     * it. A root's children go all the way round; a child's children fan out on the side away from its
     * own parent. The children keep the order they were in, going round, and sit far enough out that
     * neighbours do not overlap. Writing in the moved nodes comes along ([follow]).
     */
    fun tidy(
        id: String,
        elements: List<Element>,
    ): List<Element> {
        val node = elements.find { it.id == id }?.takeIf(::isNode) ?: return elements
        val kids = childrenOf(id, elements)
        if (kids.isEmpty()) return elements
        val hub = center(node)
        val parent = parentOf(id, elements)
        val outward = parent?.let { atan2(hub.y - center(it).y, hub.x - center(it).x) } ?: -PI / 2
        val fullCircle = parent == null
        // Going round from the outward direction: all the way round for a root, either side of it for a fan.
        val ordered =
            kids.sortedBy { kid ->
                val turn = angleFrom(outward, atan2(center(kid).y - hub.y, center(kid).x - hub.x))
                if (fullCircle && turn < 0) turn + 2 * PI else turn
            }
        val angles = spread(ordered.size, outward, fullCircle)
        val radius = tidyRadius(node, ordered, angles)
        var tidied = elements
        ordered.forEachIndexed { i, kid ->
            val target = Point(hub.x + radius * cos(angles[i]), hub.y + radius * sin(angles[i]))
            val from = center(kid)
            val subtree = descendantsOf(kid.id, tidied) + kid.id
            tidied = shiftNodes(tidied, subtree, target.x - from.x, target.y - from.y)
        }
        return follow(elements, tidied)
    }

    /** The angle of [angle] measured from [base], in -π..π: what keeps a fan's order from wrapping round. */
    private fun angleFrom(
        base: Double,
        angle: Double,
    ): Double = atan2(sin(angle - base), cos(angle - base))

    /** Where [count] children go: evenly all the way round, or in a fan centred on [outward]. */
    private fun spread(
        count: Int,
        outward: Double,
        fullCircle: Boolean,
    ): List<Double> =
        if (fullCircle) {
            List(count) { i -> outward + 2 * PI * i / count }
        } else {
            val fan = minOf(MAX_FAN, FAN_STEP * (count - 1))
            List(count) { i -> if (count == 1) outward else outward - fan / 2 + fan * i / (count - 1) }
        }

    /** Far enough out that no child touches [node] or the children beside it. */
    private fun tidyRadius(
        node: Element,
        kids: List<Element>,
        angles: List<Double>,
    ): Double {
        val widest = kids.maxOf { hypot(it.width, it.height) }
        val clearOfNode = hypot(node.width, node.height) / 2 + widest / 2 + TIDY_GAP
        if (kids.size < 2) return clearOfNode
        // The children are evenly spaced, so the first two say how far apart they all are.
        val step = angles[1] - angles[0]
        val clearOfNeighbours = (widest + TIDY_GAP) / (2 * sin(step / 2))
        return maxOf(clearOfNode, clearOfNeighbours)
    }

    /** The nodes in [ids] moved by ([dx], [dy]), with the stored points of connectors running between two of them. */
    private fun shiftNodes(
        elements: List<Element>,
        ids: Set<String>,
        dx: Double,
        dy: Double,
    ): List<Element> =
        elements.map { element ->
            when {
                element.id in ids -> shifted(element, dx, dy)
                element.hasEndpoints() && element.startElementId in ids && element.endElementId in ids -> shifted(element, dx, dy)
                else -> element
            }
        }

    /**
     * [element] moved by ([dx], [dy]): its box and its stored endpoints. A collapsed node's record of where it was
     * collapsed stays put, so the further it has moved from there, the further its branch moves when it comes back.
     */
    fun shifted(
        element: Element,
        dx: Double,
        dy: Double,
    ): Element =
        if (dx == 0.0 && dy == 0.0) {
            element
        } else {
            element.copy(
                x = element.x + dx,
                y = element.y + dy,
                startX = element.startX?.plus(dx),
                startY = element.startY?.plus(dy),
                endX = element.endX?.plus(dx),
                endY = element.endY?.plus(dy),
            )
        }
}
