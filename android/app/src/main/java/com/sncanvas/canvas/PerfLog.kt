package com.sncanvas.canvas

import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Speed measurements for the redraw work: every frame the view draws becomes
 * one CSV line in `perf-log.csv`, beside the canvases (so in
 * MyStyle/SnCanvas, where Browse & Access can reach it).
 *
 * Each line says when the frame was drawn, which kind of frame it was, what
 * gesture was under way, how many elements the canvas held, how long onDraw
 * took, and how long it had been since the frame before. The last of those
 * matters as much as the first: the view is hardware accelerated, so onDraw
 * only records what to draw and the panel is painted afterwards, on another
 * thread. A frame that is slow to paint shows up as a long gap before the next
 * one, not as a long onDraw.
 *
 * Lines are kept in memory and written in batches on a background thread, so
 * measuring never slows down the drawing it measures.
 */
internal class PerfLog(
    private val folder: () -> File?,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val runInBackground: (Runnable) -> Unit = BACKGROUND::execute,
    private val write: (File, String) -> Unit = ::appendLines,
) {
    private val pending = StringBuilder()
    private var pendingLines = 0
    private var lastFrameStartNs = 0L
    private var lastFlushMs = clockMs()

    /**
     * One drawn frame: [kind] of frame (see [CanvasView.onDraw]), the [gesture] under way if any, the
     * number of [elements] on the canvas, and when onDraw started and ended, from System.nanoTime.
     */
    fun frame(
        kind: String,
        gesture: String?,
        elements: Int,
        startNs: Long,
        endNs: Long,
    ) {
        val sincePreviousNs = startNs - lastFrameStartNs
        // A gap longer than this is the canvas sitting idle between gestures, not a slow frame.
        val gapMs = if (lastFrameStartNs != 0L && sincePreviousNs < IDLE_NS) millis(sincePreviousNs) else ""
        lastFrameStartNs = startNs
        add(line(clockMs().toString(), kind, gesture ?: "none", elements.toString(), millis(endNs - startNs), gapMs))
    }

    /** A marker line between frames, e.g. "load" when a canvas is shown, so runs are easy to tell apart. */
    fun mark(
        what: String,
        elements: Int,
    ) {
        lastFrameStartNs = 0L
        add(line(clockMs().toString(), "mark:$what", "", elements.toString(), "", ""))
    }

    /** Writes whatever is waiting, e.g. as the canvas closes; a no-op with nothing waiting or nowhere to write. */
    fun flush() {
        if (pendingLines == 0) return
        val text = pending.toString()
        pending.setLength(0)
        pendingLines = 0
        lastFlushMs = clockMs()
        // No canvas folder yet (nothing shown): those lines are dropped rather than kept growing.
        val file = folder()?.let { File(it, FILE_NAME) } ?: return
        runInBackground(Runnable { write(file, text) })
    }

    private fun add(line: String) {
        pending.append(line).append('\n')
        pendingLines++
        if (pendingLines >= FLUSH_LINES || clockMs() - lastFlushMs >= FLUSH_MS) flush()
    }

    private fun line(vararg fields: String): String = fields.joinToString(",")

    private fun millis(ns: Long): String = String.format(Locale.US, "%.2f", ns / NS_PER_MS)

    companion object {
        const val FILE_NAME = "perf-log.csv"
        const val OLD_FILE_NAME = "perf-log-old.csv"
        const val HEADER = "time_ms,frame,gesture,elements,draw_ms,gap_ms"

        // Past this size the log starts over, keeping the previous one as OLD_FILE_NAME.
        const val MAX_BYTES = 1_000_000L

        private const val TAG = "SnCanvasPerf"
        private const val FLUSH_LINES = 60
        private const val FLUSH_MS = 2_000L
        private const val IDLE_NS = 1_000_000_000L
        private const val NS_PER_MS = 1_000_000.0

        private val BACKGROUND = Executors.newSingleThreadExecutor()

        /** Appends [text] to [file], with the header first if the file is new; never throws. */
        fun appendLines(
            file: File,
            text: String,
        ) {
            runCatching {
                if (file.length() > MAX_BYTES) {
                    val old = File(file.parentFile, OLD_FILE_NAME)
                    old.delete()
                    file.renameTo(old)
                }
                if (!file.exists()) file.writeText(HEADER + "\n")
                file.appendText(text)
            }.onFailure { Log.w(TAG, "could not write ${file.path}: $it") }
        }
    }
}
