package ua.echecker.data

/** Notification settings. [leadMinutes] = warn this long before an outage; 0 = don't warn. */
data class AlertPrefs(
    val leadMinutes: Int = DEFAULT_LEAD,
    val backOn: Boolean = true,
    val scheduleChanges: Boolean = true,
) {
    companion object {
        const val DEFAULT_LEAD = 30
        val LEAD_CHOICES = listOf(0, 15, 30, 60)
    }
}
