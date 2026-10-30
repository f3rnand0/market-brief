package com.marketbrief.scheduling

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class DailyReportSchedulerTest {

    @Test
    fun `fires at 09 00 Berlin across the spring DST boundary`() = runTest {
        val fired = mutableListOf<Instant>()
        val clock = virtualClock(Instant.parse("2026-03-27T12:00:00Z"), testScheduler)
        val scheduler = scheduler(clock) { fired += clock.instant() }

        val job = launch { scheduler.run() }
        advanceTimeBy(45.hours)
        job.cancelAndJoin()

        assertEquals(
            listOf(
                Instant.parse("2026-03-28T08:00:00Z"),
                Instant.parse("2026-03-29T07:00:00Z"),
            ),
            fired,
        )
    }

    @Test
    fun `fires at 09 00 Berlin across the autumn DST boundary`() = runTest {
        val fired = mutableListOf<Instant>()
        val clock = virtualClock(Instant.parse("2026-10-23T12:00:00Z"), testScheduler)
        val scheduler = scheduler(clock) { fired += clock.instant() }

        val job = launch { scheduler.run() }
        advanceTimeBy(45.hours)
        job.cancelAndJoin()

        assertEquals(
            listOf(
                Instant.parse("2026-10-24T07:00:00Z"),
                Instant.parse("2026-10-25T08:00:00Z"),
            ),
            fired,
        )
    }

    @Test
    fun `an exact trigger time schedules the next day`() {
        val scheduler = scheduler(Clock.systemUTC()) {}

        assertEquals(
            Instant.parse("2026-10-26T08:00:00Z"),
            scheduler.nextTriggerAt(Instant.parse("2026-10-25T08:00:00Z")),
        )
    }

    @Test
    fun `a failed run keeps the schedule alive`() = runTest {
        val clock = virtualClock(Instant.parse("2026-03-27T12:00:00Z"), testScheduler)
        var attempts = 0
        val scheduler = scheduler(clock) {
            attempts++
            error("provider down")
        }

        val job = launch { scheduler.run() }
        advanceTimeBy(45.hours)
        job.cancelAndJoin()

        assertEquals(2, attempts)
    }

    private fun scheduler(clock: Clock, action: suspend () -> Unit) = DailyReportScheduler(
        clock = clock,
        zone = DailyReportScheduler.REPORT_ZONE,
        triggerTime = DailyReportScheduler.REPORT_TIME,
        action = action,
    )

    private fun virtualClock(base: Instant, testScheduler: TestCoroutineScheduler): Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(base.toEpochMilli() + testScheduler.currentTime)
    }
}
