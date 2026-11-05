package com.module.notelycompose.dictation

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationSessionTest {

    private fun DictationSession.backgroundSignal(): Unit? = runBlocking {
        withTimeoutOrNull(150) { backgroundRequests.firstOrNull() }
    }

    @Test
    fun `Zyklus request begin finish`() {
        val session = DictationSession()
        assertFalse(session.pendingStart.value)
        assertFalse(session.active.value)

        assertTrue(session.request())
        assertTrue(session.pendingStart.value)
        assertFalse(session.active.value)

        session.begin(DictationOptions())
        assertFalse(session.pendingStart.value)
        assertTrue(session.active.value)

        session.finish()
        assertFalse(session.pendingStart.value)
        assertFalse(session.active.value)
    }

    @Test
    fun `request waehrend aktivem Diktat wird ignoriert`() {
        val session = DictationSession()
        session.request()
        session.begin(DictationOptions())

        assertFalse(session.request())
        assertFalse(session.pendingStart.value)
        assertTrue(session.active.value)
    }

    @Test
    fun `zweites request waehrend wartendem Start wird ignoriert`() {
        val session = DictationSession()
        assertTrue(session.request())
        assertFalse(session.request())
    }

    @Test
    fun `begin ohne request startet nichts`() {
        val session = DictationSession()
        session.begin(DictationOptions())
        assertFalse(session.active.value)
    }

    @Test
    fun `clearIfNotStarted verwirft nur den wartenden Start`() {
        val session = DictationSession()
        session.request()
        session.clearIfNotStarted()
        assertFalse(session.pendingStart.value)
        assertFalse(session.active.value)

        session.request()
        session.begin(DictationOptions())
        session.clearIfNotStarted()
        assertTrue("laufendes Diktat darf bleiben", session.active.value)
    }

    @Test
    fun `finish laesst einen neuen wartenden Start unberuehrt`() {
        // Stale finish() eines alten ViewModels darf ein neues Diktat nicht abschiessen.
        val session = DictationSession()
        session.request()
        session.finish()
        assertTrue(session.pendingStart.value)
    }

    @Test
    fun `Optionen werden bei begin eingefroren und nach finish zurueckgesetzt`() {
        val session = DictationSession()
        val custom = DictationOptions(autoTranscribe = true, autoCopy = false, notifyReady = false)
        session.request()
        session.begin(custom)
        assertEquals(custom, session.options.value)

        session.finish()
        assertEquals(DictationOptions(), session.options.value)
    }

    @Test
    fun `requestBackground sendet nur bei aktivem Diktat mit Option A`() {
        val session = DictationSession()
        session.requestBackground()
        assertNull("inaktiv: kein Signal", session.backgroundSignal())

        session.request()
        session.begin(DictationOptions(autoTranscribe = false))
        session.requestBackground()
        assertNull("Option A aus: kein Signal", session.backgroundSignal())
        session.finish()

        session.request()
        session.begin(DictationOptions(autoTranscribe = true))
        session.requestBackground()
        assertNotNull("aktiv + Option A: Signal", session.backgroundSignal())
    }

    @Test
    fun `Hintergrund-Signal wird nur einmal zugestellt`() {
        val session = DictationSession()
        session.request()
        session.begin(DictationOptions())
        session.requestBackground()
        assertNotNull(session.backgroundSignal())
        assertNull(session.backgroundSignal())
    }

    @Test
    fun `finish verwirft ein unzugestelltes Hintergrund-Signal`() {
        // Sonst wuerde eine spaeter gestartete Activity ploetzlich in den Hintergrund springen.
        val session = DictationSession()
        session.request()
        session.begin(DictationOptions())
        session.requestBackground()
        session.finish()
        assertNull(session.backgroundSignal())
    }
}
