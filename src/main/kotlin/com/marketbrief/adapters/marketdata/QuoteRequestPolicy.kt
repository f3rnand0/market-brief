package com.marketbrief.adapters.marketdata

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal object QuoteRequestPolicy {
    private val REQUEST_TIMEOUT = 15.seconds

    suspend fun bodyOrNull(
        source: String,
        target: Any,
        log: Logger,
        timeout: Duration = REQUEST_TIMEOUT,
        request: suspend () -> HttpResponse,
    ): String? = try {
        withTimeout(timeout) {
            val response = request()
            if (!response.status.isSuccess()) {
                log.warn("{} returned {} for {}", source, response.status, target)
                return@withTimeout null
            }
            response.bodyAsText()
        }
    } catch (e: TimeoutCancellationException) {
        if (!currentCoroutineContext().isActive) throw e
        log.warn("{} request timed out for {}", source, target)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("{} request failed for {}", source, target, e)
        null
    }
}
