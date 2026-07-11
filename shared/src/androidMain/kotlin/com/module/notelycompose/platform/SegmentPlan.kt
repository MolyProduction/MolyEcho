package com.module.notelycompose.platform

/** Halboffener Byte-Bereich [start, end) einer Datei, der von einer Verbindung geladen wird. */
internal data class Segment(val index: Int, val start: Long, val end: Long) {
    val size: Long get() = end - start
}

/**
 * Pure Planungs- und Persistenzlogik für den parallelen segmentierten Download
 * (unit-getestet; die eigentliche Netz-/Datei-Arbeit macht [Downloader]).
 *
 * Die `.smeta`-Sidecar-Datei (`v1;total;n;done0,done1,…`) hält den Fortschritt je
 * Segment für Range-Resume. [parseMeta] validiert gegen den aktuellen [plan] —
 * jede Inkonsistenz (Version, Segmentanzahl, unplausible Zähler) ergibt null,
 * der Aufrufer startet dann frisch statt eine korrupte Datei fortzusetzen.
 */
internal object SegmentPlan {

    const val MAX_SEGMENTS = 4
    const val MIN_SEGMENT_BYTES = 32L * 1024L * 1024L

    /** Teilt [total] Bytes in 1..[MAX_SEGMENTS] lückenlose Segmente von je ≥ [MIN_SEGMENT_BYTES]. */
    fun plan(total: Long): List<Segment> {
        if (total <= 0) return emptyList()
        val count = (total / MIN_SEGMENT_BYTES).coerceIn(1L, MAX_SEGMENTS.toLong()).toInt()
        val base = total / count
        return List(count) { i ->
            Segment(
                index = i,
                start = i * base,
                end = if (i == count - 1) total else (i + 1) * base // Rest ans letzte Segment
            )
        }
    }

    fun encodeMeta(total: Long, done: LongArray): String =
        "v1;$total;${done.size};${done.joinToString(",")}"

    class ParsedMeta(val total: Long, val done: LongArray)

    /** null bei defekter oder zum aktuellen Plan inkonsistenter Meta. */
    fun parseMeta(text: String): ParsedMeta? {
        val parts = text.trim().split(";")
        if (parts.size != 4 || parts[0] != "v1") return null
        val total = parts[1].toLongOrNull() ?: return null
        val count = parts[2].toIntOrNull() ?: return null
        val done = parts[3].split(",").map { it.toLongOrNull() ?: return null }
        if (done.size != count) return null
        val segments = plan(total)
        if (segments.size != count) return null
        if (done.withIndex().any { (i, d) -> d < 0 || d > segments[i].size }) return null
        return ParsedMeta(total, done.toLongArray())
    }
}
