package com.sncanvas.canvas

import kotlin.math.hypot

/**
 * Supernote's gesture eraser, inside the canvas (PLAN.md step 7). In the
 * note, holding two fingers of the other hand still in a corner turns the pen
 * into the eraser until they lift; the note's own gesture does not reach a
 * plugin's window, so this copies its rules (support.supernote.com, "Gesture
 * Eraser"), in left-handed mode, since Wil holds them on the right:
 *
 * - two fingers, landing within [LANDING_WINDOW_MS] of each other, before the pen;
 * - both in the same right-hand corner, the top one or the bottom one ([CORNER_SHARE]);
 * - [MIN_APART_MM] to [MAX_APART_MM] apart;
 * - held still: moving either more than [STILL_MM] lets them go, so a pinch still zooms.
 *
 * While it is on, the pen erases whatever tool is in hand. Lifting either
 * finger turns it off. If the system takes the fingers' touch stream away
 * when the pen lands (Android can, for a pen on another input device), there
 * is no lift to wait for, so it stays on for that one pen stroke and then
 * goes off.
 *
 * Pure: [CanvasView] feeds it finger positions in view pixels, with how many
 * millimetres a pixel is.
 */
internal class GestureEraser(
    private val mmPerPx: () -> Double,
    private val viewSize: () -> Pair<Int, Int>,
) {
    private data class Finger(
        val x: Float,
        val y: Float,
        val downAtMs: Long,
    )

    private val fingers = LinkedHashMap<Int, Finger>()

    /** Whether the pen erases right now. */
    var isActive = false
        private set

    // The finger stream was taken away while on: off again once the pen lifts.
    private var untilPenLifts = false

    /** A finger landed at ([x], [y]) at [timeMs]; [penDown] says the pen was already on the screen. */
    fun fingerDown(
        id: Int,
        x: Float,
        y: Float,
        timeMs: Long,
        penDown: Boolean,
    ) {
        fingers[id] = Finger(x, y, timeMs)
        if (!isActive && !penDown && fingers.size == 2 && isTheGesture(fingers.values.toList())) {
            isActive = true
            untilPenLifts = false
        }
    }

    /** A finger moved: moving away from where it landed lets the gesture go, so the fingers can pinch instead. */
    fun fingerMove(
        id: Int,
        x: Float,
        y: Float,
    ) {
        val finger = fingers[id] ?: return
        if (isActive && hypot((x - finger.x).toDouble(), (y - finger.y).toDouble()) * mmPerPx() > STILL_MM) isActive = false
    }

    /** A finger lifted. */
    fun fingerUp(id: Int) {
        fingers.remove(id)
        if (isActive && !untilPenLifts && fingers.size < 2) isActive = false
    }

    /** The system took the touch stream away: the fingers may well still be down, but nothing more will say so. */
    fun cancelled() {
        fingers.clear()
        if (isActive) untilPenLifts = true
    }

    /** A pen stroke ended: after the finger stream was taken away, that was the one stroke the gesture erases. */
    fun penLifted() {
        if (untilPenLifts) {
            isActive = false
            untilPenLifts = false
        }
    }

    private fun isTheGesture(two: List<Finger>): Boolean {
        val (a, b) = two
        if (kotlin.math.abs(a.downAtMs - b.downAtMs) > LANDING_WINDOW_MS) return false
        val apart = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) * mmPerPx()
        if (apart < MIN_APART_MM || apart > MAX_APART_MM) return false
        val corner = cornerOf(a)
        return corner != null && corner == cornerOf(b)
    }

    /** Which right-hand corner [finger] is in, "top" or "bottom", or null for neither. */
    private fun cornerOf(finger: Finger): String? {
        val (width, height) = viewSize()
        if (width <= 0 || height <= 0 || finger.x < width * (1 - CORNER_SHARE)) return null
        return when {
            finger.y <= height * CORNER_SHARE -> "top"
            finger.y >= height * (1 - CORNER_SHARE) -> "bottom"
            else -> null
        }
    }

    companion object {
        const val LANDING_WINDOW_MS = 200L
        const val MIN_APART_MM = 18.0
        const val MAX_APART_MM = 55.0
        const val STILL_MM = 4.0

        /** How far in from the right edge, and down from the top or up from the bottom, a corner reaches, as a share of the view. */
        const val CORNER_SHARE = 0.35

        const val MM_PER_INCH = 25.4
    }
}
