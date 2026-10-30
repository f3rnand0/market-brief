package com.marketbrief.scheduling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs [action] once per day at [triggerTime] in [zone], sleeping until the next
 * occurrence. A failed run is logged and does not stop the schedule; cancellation
 * propagates.
 */
class DailyReportScheduler(
    private val clock: Clock,
    private val zone: ZoneId,
    private val triggerTime: LocalTime,
    private val action: suspend () -> Unit,
) {
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            val now = clock.instant()
            delay(Duration.between(now, nextTriggerAt(now)).toMillis().milliseconds)
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Scheduled report generation failed", e)
            }
        }
    }

    fun nextTriggerAt(now: Instant): Instant {
        val localNow = now.atZone(zone)
        val today = localNow.toLocalDate().atTime(triggerTime).atZone(zone)
        val next: ZonedDateTime = if (today.isAfter(localNow)) today else today.plusDays(1)
        return next.toInstant()
    }

    companion object {
        val REPORT_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        val REPORT_TIME: LocalTime = LocalTime.of(9, 0)

        private val logger = LoggerFactory.getLogger(DailyReportScheduler::class.java)
    }
}
