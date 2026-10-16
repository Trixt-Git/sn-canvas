package com.sncanvas.canvas

/**
 * The pencil's two weights (PLAN.md step 3), fixed width, no pressure.
 *
 * Each weight is two numbers that have to agree: [firmwareSize] is what the
 * Supernote's own pen engine is told, and it inks the stroke live while the
 * pen moves; [widthPx] is the width the canvas saves the stroke at and draws
 * it with once the pen rests. If they disagree, a stroke visibly changes
 * thickness about two seconds after it is written, when the canvas's own
 * drawing takes over from the firmware's ink.
 *
 * FINE is the developer's measured pair (firmware 1000 is 9 px on screen, on
 * a 300 ppi Nomad; the Manta is 300 ppi too). BOLD assumes the firmware's
 * size is linear in width, which has not been measured: if a bold stroke
 * jumps in thickness as it settles, this is the number to tune.
 */
enum class PenWeight(
    val id: String,
    val firmwareSize: Int,
    val widthPx: Double,
) {
    FINE("fine", 1000, 9.0),
    BOLD("bold", 2000, 18.0),
    ;

    /** The firmware pen that inks this weight live: the needle nib, black. */
    internal val firmwarePen: FirmwarePen get() = FirmwarePen.canvasPen(firmwareSize)

    companion object {
        /** The weight with [id], or [fallback] for an id this build does not know. */
        fun byId(
            id: String?,
            fallback: PenWeight = FINE,
        ): PenWeight = entries.firstOrNull { it.id == id } ?: fallback
    }
}
