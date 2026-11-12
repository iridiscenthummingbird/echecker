package ua.echecker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.echecker.sync.SyncWorker
import java.io.IOException
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant

class SyncIssueTest {
    @Test fun `classifies errors`() {
        assertEquals(SyncIssue.Kind.FORMAT, SyncIssue.from(SourceFormatException("x")).kind)
        assertEquals(SyncIssue(SyncIssue.Kind.SERVER, 503), SyncIssue.from(HttpStatusException(503, "u")))
        assertEquals(SyncIssue.Kind.NETWORK, SyncIssue.from(UnknownHostException("t.me")).kind)
        assertEquals(SyncIssue.Kind.NETWORK, SyncIssue.from(IOException("reset")).kind)
        assertEquals(SyncIssue.Kind.UNKNOWN, SyncIssue.from(IllegalStateException()).kind)
    }

    @Test fun `messages`() {
        assertEquals("Не вдалося оновити: джерело не відповідає (HTTP 503)", SyncIssue(SyncIssue.Kind.SERVER, 503).message())
        assertEquals("Немає інтернету — оновимо, щойно з'явиться", SyncIssue(SyncIssue.Kind.OFFLINE).message())
        val backup = SyncIssue(SyncIssue.Kind.FORMAT, backupSource = ChannelFetcher.OBLENERGO_CHANNEL)
        assertFalse(backup.isError)
        assertTrue(backup.message(), backup.message().contains("Дані з резервного: Telegram «Запоріжжяобленерго»"))
    }

    @Test fun `incremental sync pages back to an hour before the last full read, at most 30 days`() {
        val now = Instant.parse("2026-11-12T12:00:00Z")
        assertEquals(now.minus(Duration.ofDays(30)), SyncWorker.sinceFor(now, null))
        assertEquals(Instant.parse("2026-11-12T08:00:00Z"), SyncWorker.sinceFor(now, Instant.parse("2026-11-12T09:00:00Z")))
        assertEquals(now.minus(Duration.ofDays(30)), SyncWorker.sinceFor(now, now.minus(Duration.ofDays(90))))
    }
}
