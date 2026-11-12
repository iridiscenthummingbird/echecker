package ua.echecker.update

/** Dotted numeric versions ("1.0", "v1.10.2"); anything else is treated as unknown. */
object AppVersion {
    fun parse(raw: String): List<Int>? {
        val s = raw.trim().removePrefix("v").removePrefix("V")
        if (s.isEmpty()) return null
        return s.split('.').map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
    }

    /** True only when both parse and [candidate] is strictly higher; missing parts count as 0. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
