package com.sncanvas.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The redraw timing log: one CSV line per frame, written in batches, never from the drawing itself. */
class PerfLogTest {
    private val folder: File = Files.createTempDirectory("perf").toFile()
    private val file = File(folder, PerfLog.FILE_NAME)
    private var now = 10_000L
    private val log = PerfLog({ folder }, clockMs = { now }, runInBackground = { it.run() })

    private fun frames(
        count: Int,
        startNs: Long = SECOND,
    ) = repeat(count) { i -> log.frame("full", "Move", 399, startNs + i * FRAME, startNs + i * FRAME + DRAW) }

    @Test
    fun `nothing is written until a batch is full`() {
        frames(10)
        assertFalse(file.exists())
    }

    @Test
    fun `a full batch is written with the header first, one line per frame`() {
        log.mark("load", 399)
        frames(59)
        val lines = file.readLines()
        assertEquals(PerfLog.HEADER, lines[0])
        assertEquals(61, lines.size)
        assertEquals("10000,mark:load,,399,,", lines[1])
    }

    @Test
    fun `each frame says how long it took and how long since the one before`() {
        log.mark("load", 399)
        frames(59)
        val lines = file.readLines()
        // The first frame after a mark has nothing before it to measure from.
        assertTrue(lines[2].endsWith(",full,Move,399,5.50,"))
        assertTrue(lines[3].endsWith(",full,Move,399,5.50,16.00"))
    }

    @Test
    fun `a batch also goes out after a couple of seconds, and an idle pause is not a slow frame`() {
        log.frame("full", null, 399, 5 * SECOND, 5 * SECOND + MS)
        now += 3_000
        log.frame("pan", "Pan", 399, 7 * SECOND, 7 * SECOND + 2 * MS)
        val lines = file.readLines()
        assertEquals(3, lines.size)
        assertTrue(lines[1].endsWith(",full,none,399,1.00,"))
        assertTrue(lines[2].endsWith(",pan,Pan,399,2.00,"))
    }

    @Test
    fun `closing writes whatever is waiting, and a second flush writes nothing more`() {
        frames(3)
        log.flush()
        log.flush()
        assertEquals(4, file.readLines().size)
    }

    @Test
    fun `with no canvas folder yet the lines are dropped, not kept growing`() {
        val nowhere = PerfLog({ null }, runInBackground = { it.run() })
        nowhere.frame("full", null, 1, 1, 2)
        nowhere.flush()
        assertFalse(file.exists())
    }

    @Test
    fun `a log past its size limit starts over, keeping the last one beside it`() {
        file.writeText(PerfLog.HEADER + "\n" + "x".repeat(PerfLog.MAX_BYTES.toInt() + 1))
        PerfLog.appendLines(file, "1,full,none,1,1.00,\n")
        assertEquals(listOf(PerfLog.HEADER, "1,full,none,1,1.00,"), file.readLines())
        assertTrue(File(folder, PerfLog.OLD_FILE_NAME).length() > PerfLog.MAX_BYTES)
    }

    private companion object {
        const val MS = 1_000_000L
        const val SECOND = 1_000 * MS
        const val FRAME = 16 * MS
        const val DRAW = 5_500_000L
    }
}
