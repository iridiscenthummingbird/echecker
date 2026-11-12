package ua.echecker.domain

import java.time.LocalDate

object ScheduleParser {
    private val DATE = Regex("""Графік\s+відключен\S*\s+на\s+(\d{2})\.(\d{2})\.(\d{4})""")
    private val QUEUE = Regex("""Черга\s+(\d)\.(\d)""")
    private val INTERVAL = Regex("""[зЗ]\s+(\d{1,2}):(\d{2})\s+до\s+(\d{1,2}):(\d{2})""")

    fun parse(text: String): Schedule? {
        val dm = DATE.find(text) ?: return null
        val (d, m, y) = dm.destructured
        val date = runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull() ?: return null

        val raw = linkedMapOf<Queue, MutableList<Interval>>()
        var current: Queue? = null
        for (line in text.lines()) {
            val qm = QUEUE.find(line)
            if (qm != null) {
                current = Queue.parse("${qm.groupValues[1]}.${qm.groupValues[2]}")
                continue
            }
            val queue = current ?: continue
            for (im in INTERVAL.findAll(line)) {
                val (sh, sm, eh, em) = im.destructured
                val start = sh.toInt() * 60 + sm.toInt()
                val end = eh.toInt() * 60 + em.toInt()
                if (start in 0..1440 && end in 0..1440 && start < end) {
                    raw.getOrPut(queue) { mutableListOf() } += Interval(start, end)
                }
            }
        }
        return Schedule(date, raw.mapValues { Interval.merge(it.value) })
    }
}
