package com.sncanvas.canvas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The speed test canvas: a heavy, always identical mind map to measure
 * redraws against ([PerfLog]). A centre node, [BRANCHES] branches around it
 * and [CHILDREN_PER_BRANCH] children around each branch: 100 nodes, each
 * joined to its parent by a bound arrow and labelled with two scribbled
 * "words" of freehand ink. 399 elements in all, drawn with the hand-drawn
 * outline, the most expensive one to draw.
 *
 * Seeded, so every run of the test draws exactly the same thing and timings
 * from before and after a change can be compared fairly.
 */
object PerfTestCanvas {
    const val BRANCHES = 9
    const val CHILDREN_PER_BRANCH = 10
    const val WORDS_PER_NODE = 2

    private const val SEED = 42
    private const val BRANCH_RADIUS = 900.0

    // Children sit on two staggered rings around the centre, alternating, so none overlap.
    private const val CHILD_RING_INNER = 1_800.0
    private const val CHILD_RING_OUTER = 2_200.0

    // Samples per scribbled word: a real pen sends this many and more for a short word.
    private const val SAMPLES_PER_WORD = 80
    private const val WAVES_PER_WORD = 4.0

    // Node sizes (world units), wide 2:1 as the mind-map nodes will be.
    private val CENTRE = Size(320.0, 160.0)
    private val BRANCH = Size(240.0, 120.0)
    private val CHILD = Size(160.0, 80.0)

    private data class Size(
        val width: Double,
        val height: Double,
    )

    /** Every element of the test map, arrows first so the nodes and their ink draw over them. */
    fun elements(): List<Element> {
        val random = Random(SEED)
        val nodes = mutableListOf<Element>()
        val arrows = mutableListOf<Element>()
        val centre = node(id = "perf-node-0", type = CanvasTools.ELLIPSE, centreX = 0.0, centreY = 0.0, size = CENTRE)
        nodes += centre
        for (branch in 0 until BRANCHES) {
            val sector = 2 * PI / BRANCHES
            val angle = sector * branch
            val branchNode =
                node(
                    id = "perf-node-${nodes.size}",
                    type = CanvasTools.RECTANGLE,
                    centreX = BRANCH_RADIUS * cos(angle),
                    centreY = BRANCH_RADIUS * sin(angle),
                    size = BRANCH,
                )
            nodes += branchNode
            arrows += arrow("perf-arrow-${arrows.size}", centre, branchNode)
            for (child in 0 until CHILDREN_PER_BRANCH) {
                // Spread across the branch's own slice of the circle, so branches never mix.
                val childAngle = angle - sector / 2 + sector * (child + 0.5) / CHILDREN_PER_BRANCH
                val ring = if (child % 2 == 0) CHILD_RING_INNER else CHILD_RING_OUTER
                val childNode =
                    node(
                        id = "perf-node-${nodes.size}",
                        type = if (child % 2 == 0) CanvasTools.ELLIPSE else CanvasTools.RECTANGLE,
                        centreX = ring * cos(childAngle),
                        centreY = ring * sin(childAngle),
                        size = CHILD,
                    )
                nodes += childNode
                arrows += arrow("perf-arrow-${arrows.size}", branchNode, childNode)
            }
        }
        val ink = nodes.flatMap { words(it, random) }
        return arrows + nodes + ink
    }

    private fun node(
        id: String,
        type: String,
        centreX: Double,
        centreY: Double,
        size: Size,
    ): Element =
        Element(
            id = id,
            type = type,
            x = centreX - size.width / 2,
            y = centreY - size.height / 2,
            width = size.width,
            height = size.height,
            style = ShapeStyle.DEFAULT,
        )

    /** An arrow from [from]'s centre to [to]'s, both ends bound, so it follows either node as it moves. */
    private fun arrow(
        id: String,
        from: Element,
        to: Element,
    ): Element =
        Element(
            id = id,
            type = CanvasTools.ARROW,
            startX = centreX(from),
            startY = centreY(from),
            endX = centreX(to),
            endY = centreY(to),
            startElementId = from.id,
            endElementId = to.id,
            style = ShapeStyle.DEFAULT,
        )

    /** [WORDS_PER_NODE] scribbled words side by side across the middle of [node], as a handwritten label. */
    private fun words(
        node: Element,
        random: Random,
    ): List<Element> {
        val wordWidth = node.width * 0.7 / WORDS_PER_NODE
        val gap = node.width * 0.1 / WORDS_PER_NODE
        val height = node.height * 0.3
        var left = node.x + node.width * 0.1
        return (0 until WORDS_PER_NODE).mapNotNull { word ->
            val samples =
                (0 until SAMPLES_PER_WORD).map { sample ->
                    val t = sample.toDouble() / (SAMPLES_PER_WORD - 1)
                    val wobble = (random.nextDouble() - 0.5) * height * 0.2
                    StrokePoint(
                        x = left + t * wordWidth,
                        y = centreY(node) + sin(t * 2 * PI * WAVES_PER_WORD) * height / 2 + wobble,
                        pressure = 0.5 + random.nextDouble() * 0.3,
                    )
                }
            left += wordWidth + gap
            StrokeElements.fromSamples("perf-ink-${node.id.removePrefix("perf-node-")}-$word", samples)
        }
    }

    private fun centreX(element: Element): Double = element.x + element.width / 2

    private fun centreY(element: Element): Double = element.y + element.height / 2
}
