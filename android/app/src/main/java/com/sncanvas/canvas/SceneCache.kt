package com.sncanvas.canvas

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * The canvas drawn once and kept (PLAN.md step 1; the developer's own TODO in
 * [CanvasView]: "a cached world bitmap ... and a cheap pass during gestures").
 *
 * Every frame used to draw every element again, even while only one thing was
 * moving under the pen. Now the elements that are not changing are drawn into
 * this bitmap once, and a frame copies the bitmap and draws only what the
 * gesture is changing on top ([SceneCull.changedIds]). The bitmap is drawn
 * again only when something it shows has changed: the elements (a committed
 * edit), the view (a pan or zoom has settled), what is left out of it (a
 * gesture starts or ends), or the text an open editor hides.
 *
 * While the view itself moves (a pan, a pinch, the minimap), the last drawing
 * is slid and scaled under the finger instead ([drawMoved]), as the
 * developer's PanPreview did for pans; a crisp drawing follows once the view
 * settles.
 *
 * One bitmap the size of the view. Drawn on the UI thread only.
 */
internal class SceneCache(
    private val renderer: CanvasRenderer,
) {
    private var bitmap: Bitmap? = null
    private var drawnElements: List<Element>? = null
    private var drawnAt: ViewTransform? = null
    private var drawnWithout: Set<String> = emptySet()
    private var drawnHiding: ElementPainter.HiddenText? = null

    /** How many times the bitmap has been drawn, for the speed log. */
    var renders = 0
        private set

    /**
     * Makes sure the bitmap shows [elements] at [transform], [width] by [height], without the elements in
     * [leftOut] and without the text [hidden] under an open editor; draws it again only if it does not
     * already. True when it had to.
     */
    fun ensure(
        elements: List<Element>,
        transform: ViewTransform,
        width: Int,
        height: Int,
        leftOut: Set<String> = emptySet(),
        hidden: ElementPainter.HiddenText? = null,
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        val current = bitmap
        val fresh =
            current != null &&
                current.width == width &&
                current.height == height &&
                drawnElements === elements &&
                drawnAt == transform &&
                drawnWithout == leftOut &&
                drawnHiding == hidden
        if (fresh) return false
        // Not recycled when replaced: the last frame drawn may still hold it, and the garbage collector frees it safely.
        val target =
            current?.takeIf { it.width == width && it.height == height } ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        renderer.drawBackground(canvas, width.toFloat(), height.toFloat())
        renderer.drawElements(
            canvas,
            elements,
            transform,
            StylePalette.EINK,
            hidden,
            skip = leftOut,
            visible = transform.visibleRect(width.toDouble(), height.toDouble()),
        )
        bitmap = target
        drawnElements = elements
        drawnAt = transform
        drawnWithout = leftOut
        drawnHiding = hidden
        renders++
        return true
    }

    /** Copies the bitmap onto [canvas] as it was drawn; false when there is none yet. */
    fun draw(canvas: Canvas): Boolean {
        val current = bitmap ?: return false
        canvas.drawBitmap(current, 0f, 0f, null)
        return true
    }

    /**
     * The last drawing moved from where it was drawn onto [transform], for a frame while the view itself is
     * moving: no element is drawn, so a pan or pinch costs the same on any canvas. False with no drawing yet.
     */
    fun drawMoved(
        canvas: Canvas,
        transform: ViewTransform,
        width: Int,
        height: Int,
    ): Boolean {
        val base = drawnAt
        val current = bitmap
        if (base == null || current == null) return false
        // What the drawing does not cover once it is moved is empty canvas, not whatever was there last frame.
        renderer.drawBackground(canvas, width.toFloat(), height.toFloat())
        val scale = (transform.zoom / base.zoom).toFloat()
        canvas.save()
        canvas.translate(
            ((base.viewportX - transform.viewportX) * transform.zoom).toFloat(),
            (
                (base.viewportY - transform.viewportY) *
                    transform.zoom
            ).toFloat(),
        )
        canvas.scale(scale, scale)
        canvas.drawBitmap(current, 0f, 0f, null)
        canvas.restore()
        return true
    }

    /** Lets the bitmap go, as the view leaves the screen; the next frame draws a new one. */
    fun forget() {
        bitmap = null
        drawnElements = null
        drawnAt = null
    }
}
