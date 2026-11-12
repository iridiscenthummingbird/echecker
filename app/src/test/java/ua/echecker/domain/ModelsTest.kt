package ua.echecker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    @Test fun `parses valid queue`() {
        assertEquals(Queue(2, 1), Queue.parse("2.1"))
        assertEquals("2.1", Queue(2, 1).toString())
    }

    @Test fun `rejects invalid queue`() {
        assertNull(Queue.parse("7.1"))
        assertNull(Queue.parse("1.3"))
        assertNull(Queue.parse("abc"))
    }

    @Test fun `ALL has 12 sorted queues`() {
        assertEquals(12, Queue.ALL.size)
        assertEquals(Queue(1, 1), Queue.ALL.first())
        assertEquals(Queue(6, 2), Queue.ALL.last())
    }

    @Test fun `formats interval including 24_00`() {
        assertEquals("19:00–24:00", Interval(1140, 1440).format())
        assertEquals("05:30–07:00", Interval(330, 420).format())
    }

    @Test fun `merges overlapping and adjacent intervals`() {
        val merged = Interval.merge(
            listOf(Interval(1140, 1440), Interval(930, 1170), Interval(0, 60), Interval(60, 90))
        )
        assertEquals(listOf(Interval(0, 90), Interval(930, 1440)), merged)
    }
}
