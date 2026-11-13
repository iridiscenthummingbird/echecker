package ua.echecker.data

import ua.echecker.R
import ua.echecker.i18n.Strings
import java.io.IOException

/** Why the last sync did not fully succeed; shown next to the Refresh button. */
data class SyncIssue(
    val kind: Kind,
    /** HTTP status for [Kind.SERVER]. */
    val httpCode: Int? = null,
    /** Set when the primary source failed but this backup source supplied data. */
    val backupSource: String? = null,
) {
    enum class Kind { OFFLINE, NETWORK, SERVER, FORMAT, UNKNOWN }

    fun message(s: Strings): String {
        val reason = when (kind) {
            Kind.OFFLINE -> s.get(R.string.reason_offline)
            Kind.NETWORK -> s.get(R.string.reason_network)
            Kind.SERVER -> httpCode?.let { s.get(R.string.reason_server_code, it) } ?: s.get(R.string.reason_server)
            Kind.FORMAT -> s.get(R.string.reason_format)
            Kind.UNKNOWN -> s.get(R.string.reason_unknown)
        }
        return when {
            backupSource != null -> s.get(R.string.issue_backup, reason, sourceLabel(backupSource, s))
            kind == Kind.OFFLINE -> s.get(R.string.issue_offline)
            else -> s.get(R.string.issue_failed, reason)
        }
    }

    /** A backup note is informational; everything else is an error. */
    val isError: Boolean get() = backupSource == null

    companion object {
        fun from(e: Throwable): SyncIssue = when (e) {
            is SourceFormatException -> SyncIssue(Kind.FORMAT)
            is HttpStatusException -> SyncIssue(Kind.SERVER, httpCode = e.code)
            is IOException -> SyncIssue(Kind.NETWORK)
            else -> SyncIssue(Kind.UNKNOWN)
        }

        fun sourceLabel(id: String, s: Strings): String = when (id) {
            ChannelFetcher.OBLENERGO_CHANNEL -> s.get(R.string.source_oblenergo)
            else -> id
        }
    }
}
