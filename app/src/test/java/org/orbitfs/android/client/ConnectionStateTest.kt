package org.orbitfs.android.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.orbitfs.android.data.ConnectionConfig

class ConnectionStateTest {

    @Test
    fun `Disconnected is a valid state`() {
        val config = ConnectionConfig()
        val state = ConnectionState.Disconnected(config)
        assertTrue(state is ConnectionState.Disconnected)
    }

    @Test
    fun `Connecting holds config`() {
        val config = ConnectionConfig(host = "10.0.0.1", port = 8080)
        val state = ConnectionState.Connecting(config)
        assertEquals("10.0.0.1", state.config.host)
        assertEquals(8080, state.config.port)
    }

    @Test
    fun `Connected holds config`() {
        val config = ConnectionConfig(host = "10.0.0.2", port = 9999)
        val state = ConnectionState.Connected(config)
        assertEquals("10.0.0.2", state.config.host)
        assertEquals(9999, state.config.port)
    }

    @Test
    fun `Error holds config, message, and attempt info`() {
        val config = ConnectionConfig(host = "10.0.0.3", port = 1234)
        val state = ConnectionState.Error(
            config = config,
            message = "Server unreachable",
            attempt = 2,
            maxAttempts = 5
        )
        assertEquals("10.0.0.3", state.config.host)
        assertEquals("Server unreachable", state.message)
        assertEquals(2, state.attempt)
        assertEquals(5, state.maxAttempts)
    }

    @Test
    fun `GaveUp holds config and message`() {
        val config = ConnectionConfig(host = "10.0.0.4", port = 5678)
        val state = ConnectionState.GaveUp(config, "Connection failed after 5 attempts")
        assertEquals("10.0.0.4", state.config.host)
        assertEquals("Connection failed after 5 attempts", state.message)
    }

    @Test
    fun `ConnectionState sealed class has all expected variants`() {
        val states = listOf(
            ConnectionState.Disconnected(ConnectionConfig()),
            ConnectionState.Connecting(ConnectionConfig()),
            ConnectionState.Connected(ConnectionConfig()),
            ConnectionState.Error(ConnectionConfig(), "err", 1, 5),
            ConnectionState.GaveUp(ConnectionConfig(), "err")
        )
        assertEquals(5, states.size)
    }
}
