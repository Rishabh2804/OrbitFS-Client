package org.orbitfs.android.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RetryTest {

    @Test
    fun `withRetry succeeds on first attempt`() = runTest {
        val wrapper = OrbitFSClientWrapper("localhost", 9999)
        var callCount = 0

        val result = wrapper.withRetry(
            initialDelay = 1.milliseconds,
            maxDelay = 10.milliseconds
        ) {
            callCount++
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `withRetry retries on failure then succeeds`() = runTest {
        val wrapper = OrbitFSClientWrapper("localhost", 9999)
        var callCount = 0

        val result = wrapper.withRetry(
            maxAttempts = 3,
            initialDelay = 1.milliseconds,
            maxDelay = 10.milliseconds
        ) {
            callCount++
            if (callCount < 2) {
                throw RuntimeException("fail")
            }
            "success"
        }

        assertEquals("success", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `withRetry exhausts attempts and throws`() = runTest {
        val wrapper = OrbitFSClientWrapper("localhost", 9999)
        var callCount = 0

        val exception = assertThrows<RuntimeException> {
            wrapper.withRetry(
                maxAttempts = 3,
                initialDelay = 1.milliseconds,
                maxDelay = 10.milliseconds
            ) {
                callCount++
                throw RuntimeException("always fails")
            }
        }

        assertEquals("always fails", exception.message)
        assertEquals(3, callCount)
    }

    @Test
    fun `withRetry retries correct number of times`() = runTest {
        val wrapper = OrbitFSClientWrapper("localhost", 9999)
        var callCount = 0

        val exception = assertThrows<RuntimeException> {
            wrapper.withRetry(
                maxAttempts = 5,
                initialDelay = 1.milliseconds,
                maxDelay = 100.milliseconds
            ) {
                callCount++
                throw RuntimeException("fail")
            }
        }

        assertEquals(5, callCount)
        assertEquals("fail", exception.message)
    }
}
