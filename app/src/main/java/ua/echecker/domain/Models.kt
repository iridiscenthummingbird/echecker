package ua.echecker.domain

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class Queue(val group: Int, val sub: Int) : Comparable<Queue> {
    init {
        require(group in 1..6 && sub in 1..2) { "Invalid queue $group.$sub" }
    }

    override fun toString() = "$group.$sub"

    override fun compareTo(other: Queue) = compareValuesBy(this, other, Queue::group, Queue::sub)

    companion object {
        val ALL: List<Queue> = (1..6).flatMap { g -> (1..2).map { s -> Queue(g, s) } }

        fun parse(s: String): Queue? {
            val parts = s.trim().split(".")
            if (parts.size != 2) return null
            val g = parts[0].toIntOrNull() ?: return null
            val sub = parts[1].toIntOrNull() ?: return null
            return if (g in 1..6 && sub in 1..2) Queue(g, sub) else null
        }
    }
}

/** Minutes of day, 0..1440 (1440 = 24:00). */
data class Interval(val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0..1440 && endMinute in 0..1440 && startMinute < endMinute) {
            "Invalid interval $startMinute..$endMinute"
        }
    }

    fun format() = "${formatMinute(startMinute)}–${formatMinute(endMinute)}"

    companion object {
        fun formatMinute(m: Int): String = String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)

        fun merge(list: List<Interval>): List<Interval> {
            val result = mutableListOf<Interval>()
            for (i in list.sortedBy { it.startMinute }) {
                val last = result.lastOrNull()
                if (last != null && i.startMinute <= last.endMinute) {
                    result[result.lastIndex] = Interval(last.startMinute, maxOf(last.endMinute, i.endMinute))
                } else {
                    result += i
                }
            }
            return result
        }
    }
}

data class Schedule(val date: LocalDate, val queues: Map<Queue, List<Interval>>) {
    fun intervalsFor(q: Queue): List<Interval> = queues[q].orEmpty()
}

object KyivTime {
    val ZONE: ZoneId = runCatching { ZoneId.of("Europe/Kyiv") }.getOrElse { ZoneId.of("Europe/Kiev") }
}
