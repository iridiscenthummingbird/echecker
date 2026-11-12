package ua.echecker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ScheduleParser {
    private val NUMERIC_DATE = Regex("""Графік\s+відключен\S*\s+на\s+(\d{2})\.(\d{2})\.(\d{4})""")
    private val MONTHS = listOf(
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"
    )
    private val NAMED_DATE = Regex(
        """(\d{1,2})\s+(${MONTHS.joinToString("|")})""",
        RegexOption.IGNORE_CASE
    )
    private val QUEUE = Regex("""Черга\s+(\d)\.(\d)""")
    private val BLOCK_INTERVAL = Regex("""^\s*[зЗ]\s+(\d{1,2}):(\d{2})\s+до\s+(\d{1,2}):(\d{2})\s*$""")
    private val COMPACT_LINE = Regex("""^\s*(\d)\.(\d)\s*:\s*(.+)$""")
    private val COMPACT_INTERVAL = Regex("""(\d{1,2}):(\d{2})\s*[–—-]\s*(\d{1,2}):(\d{2})""")

    /** [referenceDate] = the post's publish date in Kyiv; used to infer the year when the post omits it. */
    fun parse(text: String, referenceDate: LocalDate): Schedule? {
        val date = findDate(text, referenceDate) ?: return null

        val raw = linkedMapOf<Queue, MutableList<Interval>>()
        var current: Queue? = null
        for (line in text.lines()) {
            val qm = QUEUE.find(line)
            if (qm != null) {
                current = Queue.parse("${qm.groupValues[1]}.${qm.groupValues[2]}")
                continue
            }
            val bm = BLOCK_INTERVAL.find(line)
            if (bm != null) {
                val queue = current ?: continue
                addInterval(raw, queue, bm.groupValues)
                continue
            }
            val cm = COMPACT_LINE.find(line)
            if (cm != null) {
                current = null
                val queue = Queue.parse("${cm.groupValues[1]}.${cm.groupValues[2]}") ?: continue
                for (im in COMPACT_INTERVAL.findAll(cm.groupValues[3])) addInterval(raw, queue, im.groupValues)
            }
        }
        if (raw.isEmpty()) return null
        return Schedule(date, raw.mapValues { Interval.merge(it.value) })
    }

    private fun addInterval(raw: MutableMap<Queue, MutableList<Interval>>, queue: Queue, g: List<String>) {
        val sh = g[1].toInt()
        val sm = g[2].toInt()
        val eh = g[3].toInt()
        val em = g[4].toInt()
        if (sm >= 60 || em >= 60 || sh > 23) return
        if (eh > 24 || (eh == 24 && em != 0)) return
        val start = sh * 60 + sm
        val end = eh * 60 + em
        if (start >= end) return
        raw.getOrPut(queue) { mutableListOf() } += Interval(start, end)
    }

    private fun findDate(text: String, ref: LocalDate): LocalDate? {
        val numeric = NUMERIC_DATE.find(text)
        if (numeric != null) {
            val (d, m, y) = numeric.destructured
            return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }
        val named = NAMED_DATE.find(text) ?: return null
        val day = named.groupValues[1].toInt()
        val month = MONTHS.indexOf(named.groupValues[2].lowercase()) + 1
        val date = runCatching { LocalDate.of(ref.year, month, day) }.getOrNull() ?: return null
        val diff = ChronoUnit.DAYS.between(ref, date)
        return when {
            diff < -180 -> date.plusYears(1)
            diff > 180 -> date.minusYears(1)
            else -> date
        }
    }
}
