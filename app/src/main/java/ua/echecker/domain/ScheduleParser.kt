package ua.echecker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One schedule post. A full update replaces the whole day; queues it does not list have no outages.
 * A [partial] update replaces only the queues in [Schedule.queues] — an empty list there means that queue's
 * outages were cancelled — and leaves every other queue as previously published.
 */
data class ScheduleUpdate(val schedule: Schedule, val partial: Boolean)

object ScheduleParser {
    private val NUMERIC_DATE = Regex("""Графік\s+відключен\S*\s+на\s+(\d{2})\.(\d{2})\.(\d{4})""")
    private val ANY_NUMERIC_DATE = Regex("""(?<![\d.])(\d{2})\.(\d{2})\.(\d{4})(?![\d.]\d)""")
    private val MONTHS = listOf(
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"
    )
    private val NAMED_DATE = Regex(
        """(?<!\d)(\d{1,2})\s+(${MONTHS.joinToString("|")})""",
        RegexOption.IGNORE_CASE
    )
    // WS also matches NBSP (U+00A0); DASH also accepts − (U+2212) and ‒ (U+2012).
    private const val WS = """[\s\u00A0]"""
    private const val DASH = """[–—\-\u2212\u2012]"""
    private val QUEUE = Regex("""Черга$WS+(\d)\.(\d)""")
    private val BLOCK_INTERVAL = Regex("""^$WS*[зЗ]$WS+(\d{1,2}):(\d{2})$WS+до$WS+(\d{1,2}):(\d{2})$WS*$""")
    // Not anchored to line start: zoe_alarm sometimes glues "1.1: …" to the header with spaces instead of a newline.
    private val COMPACT_LINE = Regex("""(?:^|$WS)(\d)\.(\d)$WS*:$WS*(.+)$""")
    private val COMPACT_INTERVAL = Regex("""(\d{1,2}):(\d{2})$WS*$DASH$WS*(\d{1,2}):(\d{2})""")

    // \w is ASCII-only on the JVM, so Cyrillic word tails use \p{L}.
    // "ГПВ" / "графік(и) (погодинних) відключень" — not ГОП, планові чи аварійні роботи.
    private const val GPV = """(?:ГПВ|погодинн\p{L}*\s+відключен\p{L}*|графік\p{L}*\s+(?:погодинн\p{L}*\s+)?відключен\p{L}*)"""
    // Impersonal forms only: "ми скасовуємо роботи" must not count.
    private const val CANCEL = """(?:скасован\p{L}*|скасовуються|скасування|відмінен\p{L}*|відміняються|""" +
        """не\s+(?:будуть\s+)?застосов\p{L}*|не\s+діятимуть|не\s+діють|не\s+прогнозу\p{L}*|не\s+плану\p{L}*|не\s+заплановано)"""
    private val GPV_CANCELLED = Regex(
        """$GPV[\s\S]{0,60}?$CANCEL|$CANCEL[\s\S]{0,60}?$GPV""",
        RegexOption.IGNORE_CASE,
    )
    private const val QUEUE_LIST = """[1-6]\.[12](?:\s*(?:,|та|і|й)\s*[1-6]\.[12])*"""
    private val QUEUE_ID = Regex("""[1-6]\.[12]""")
    private val FOR_QUEUES = Regex("""(?:для|по)\s+(?:під)?черг\p{L}*\s+($QUEUE_LIST)""", RegexOption.IGNORE_CASE)
    private val PARTIAL_MARKERS = listOf(
        // "для решти / інших черг (графік) без змін", "без змін для інших черг"
        """(?:решт|інш)\p{L}*\s+(?:під)?черг\p{L}*[^\n]{0,60}?без\s+змін""",
        """без\s+змін[^\n]{0,40}?(?:решт|інш)\p{L}*\s+(?:під)?черг""",
        // "лише/тільки для черги 3.2", "зміни/оновлено … для черг 3.2 та 6.2"
        """(?:лише|тільки)\s+(?:для\s+)?(?:під)?черг\p{L}*\s+[1-6]\.[12]""",
        """(?:змін\p{L}*|оновлен\p{L}*|скориговано|коригуван\p{L}*)[^\n]{0,80}?для\s+(?:під)?черг\p{L}*\s+[1-6]\.[12]""",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /**
     * The full schedule from a post, or null if the post is not one (including partial updates).
     * A cancelled GPV day yields a schedule without outages.
     */
    fun parse(text: String, referenceDate: LocalDate): Schedule? =
        parseUpdate(text, referenceDate)?.takeUnless { it.partial }?.schedule

    /** [referenceDate] = the post's publish date in Kyiv; used to infer the year when the post omits it. */
    fun parseUpdate(text: String, referenceDate: LocalDate): ScheduleUpdate? {
        val date = findDate(text, referenceDate) ?: return null

        val raw = linkedMapOf<Queue, MutableList<Interval>>()
        // Queues the post names, with or without intervals ("3.2: скасовано"); matters for partial updates.
        val mentioned = linkedSetOf<Queue>()
        var current: Queue? = null
        for (line in text.lines()) {
            val qm = QUEUE.find(line)
            if (qm != null) {
                val queue = Queue.parse("${qm.groupValues[1]}.${qm.groupValues[2]}")
                if (queue != null) mentioned += queue
                // "Черга 1.1: 00:00 - 05:00. 09:00 - 14:00" — intervals on the same line; otherwise a block header.
                val inline = COMPACT_INTERVAL.findAll(line.substring(qm.range.last + 1)).toList()
                if (inline.isEmpty()) {
                    current = queue
                } else {
                    current = null
                    if (queue != null) for (im in inline) addInterval(raw, queue, im.groupValues)
                }
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
                mentioned += queue
                for (im in COMPACT_INTERVAL.findAll(cm.groupValues[3])) addInterval(raw, queue, im.groupValues)
            }
        }
        val parsed = raw.mapValues { Interval.merge(it.value) }
        val partial = PARTIAL_MARKERS.any { it.containsMatchIn(text) }

        if (parsed.isNotEmpty()) {
            if (!partial) return ScheduleUpdate(Schedule(date, parsed), partial = false)
            val listed = (mentioned + parsed.keys).associateWith { parsed[it].orEmpty() }
            return ScheduleUpdate(Schedule(date, listed), partial = true)
        }
        if (!GPV_CANCELLED.containsMatchIn(text)) return null
        // No intervals + "ГПВ скасовано": either for the listed queues only, or for the whole day.
        val cancelled = mentioned + FOR_QUEUES.findAll(text).flatMap { m ->
            QUEUE_ID.findAll(m.groupValues[1]).mapNotNull { Queue.parse(it.value) }
        }
        return when {
            !partial -> ScheduleUpdate(Schedule(date, emptyMap()), partial = false)
            // "Partial" but no queue we can name: wiping the whole day would be worse than ignoring the post.
            cancelled.isEmpty() -> null
            else -> ScheduleUpdate(Schedule(date, cancelled.associateWith { emptyList() }), partial = true)
        }
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

    /**
     * First date in the text within [ref − 1 day, ref + 2 days]: numeric "Графік відключення на" headers first,
     * then named-month dates, then any other dd.mm.yyyy.
     */
    private fun findDate(text: String, ref: LocalDate): LocalDate? {
        fun numericDates(regex: Regex) = regex.findAll(text).map { m ->
            val (d, mo, y) = m.destructured
            runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
        }
        val numeric = numericDates(NUMERIC_DATE)
        val named = NAMED_DATE.findAll(text).map { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS.indexOf(m.groupValues[2].lowercase()) + 1
            runCatching { LocalDate.of(ref.year, month, day) }.getOrNull()?.let { date ->
                val diff = ChronoUnit.DAYS.between(ref, date)
                when {
                    diff < -180 -> date.plusYears(1)
                    diff > 180 -> date.minusYears(1)
                    else -> date
                }
            }
        }
        return (numeric + named + numericDates(ANY_NUMERIC_DATE)).filterNotNull().firstOrNull { it.isPlausibleFor(ref) }
    }

    private fun LocalDate.isPlausibleFor(ref: LocalDate) = !isBefore(ref.minusDays(1)) && !isAfter(ref.plusDays(2))
}
