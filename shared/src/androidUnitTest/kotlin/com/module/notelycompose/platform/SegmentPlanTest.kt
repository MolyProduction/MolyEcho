package com.module.notelycompose.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlanTest {

    private val mb = 1024L * 1024L

    /** Segmente müssen die Datei lückenlos, überlappungsfrei und vollständig abdecken. */
    private fun assertCovers(total: Long, segments: List<Segment>) {
        assertEquals(0L, segments.first().start)
        assertEquals(total, segments.last().end)
        segments.zipWithNext().forEach { (a, b) ->
            assertEquals("Segment ${a.index} endet nicht am Anfang von ${b.index}", a.end, b.start)
        }
        assertEquals(total, segments.sumOf { it.size })
    }

    @Test
    fun `grosse Datei wird in maximal 4 Segmente geteilt`() {
        val total = 990 * mb // ONNX-Encoder-Größenordnung
        val segments = SegmentPlan.plan(total)
        assertEquals(4, segments.size)
        assertCovers(total, segments)
        assertTrue(segments.all { it.size >= SegmentPlan.MIN_SEGMENT_BYTES })
    }

    @Test
    fun `kleine Datei bleibt ein einzelnes Segment`() {
        val total = 10 * mb // tokens.txt-Größenordnung
        val segments = SegmentPlan.plan(total)
        assertEquals(1, segments.size)
        assertCovers(total, segments)
    }

    @Test
    fun `Datei knapp unter der Schwelle bleibt ein Segment`() {
        val segments = SegmentPlan.plan(2 * SegmentPlan.MIN_SEGMENT_BYTES - 1)
        assertEquals(1, segments.size)
    }

    @Test
    fun `Datei ab doppelter Mindestgroesse wird geteilt`() {
        val total = 2 * SegmentPlan.MIN_SEGMENT_BYTES
        val segments = SegmentPlan.plan(total)
        assertEquals(2, segments.size)
        assertCovers(total, segments)
    }

    @Test
    fun `ungerade Gesamtgroesse geht vollstaendig auf`() {
        val total = 1_234_567_891L
        val segments = SegmentPlan.plan(total)
        assertCovers(total, segments)
    }

    @Test
    fun `unbekannte oder leere Groesse ergibt keinen Plan`() {
        assertTrue(SegmentPlan.plan(0).isEmpty())
        assertTrue(SegmentPlan.plan(-1).isEmpty())
    }

    @Test
    fun `Meta-Roundtrip erhaelt total und Fortschritt`() {
        val total = 990 * mb
        val done = longArrayOf(5 * mb, 0, 123, 247 * mb)
        val parsed = SegmentPlan.parseMeta(SegmentPlan.encodeMeta(total, done))
        assertNotNull(parsed)
        assertEquals(total, parsed!!.total)
        assertEquals(done.toList(), parsed.done.toList())
    }

    @Test
    fun `defekte Meta wird verworfen`() {
        assertNull(SegmentPlan.parseMeta(""))
        assertNull(SegmentPlan.parseMeta("Kauderwelsch"))
        assertNull(SegmentPlan.parseMeta("v2;100;1;0")) // unbekannte Version
        assertNull(SegmentPlan.parseMeta("v1;abc;4;0,0,0,0")) // total keine Zahl
        assertNull(SegmentPlan.parseMeta("v1;100;4;0,0,0")) // Anzahl passt nicht
    }

    @Test
    fun `Meta mit anderer Segmentanzahl als der aktuelle Plan wird verworfen`() {
        // 990 MB planen wir heute mit 4 Segmenten — eine 2-Segment-Meta ist inkonsistent.
        assertNull(SegmentPlan.parseMeta("v1;${990 * mb};2;0,0"))
    }

    @Test
    fun `Meta mit unplausiblem Fortschritt wird verworfen`() {
        val total = 990 * mb
        val tooMuch = total // mehr als Segment 0 groß ist
        assertNull(SegmentPlan.parseMeta("v1;$total;4;$tooMuch,0,0,0"))
        assertNull(SegmentPlan.parseMeta("v1;$total;4;-1,0,0,0"))
    }

    @Test
    fun `Meta mit komplett fertigen Segmenten ist gueltig`() {
        val total = 4 * SegmentPlan.MIN_SEGMENT_BYTES
        val segments = SegmentPlan.plan(total)
        val done = segments.map { it.size }.toLongArray()
        assertNotNull(SegmentPlan.parseMeta(SegmentPlan.encodeMeta(total, done)))
    }
}
