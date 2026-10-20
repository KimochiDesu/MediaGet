package com.erdbot.mediagrab.extract

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** A user's login must never follow a redirect to another site. */
class LoginScopeGuardTest {

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    @Test
    fun loginHeadersAreDroppedWhenARedirectLeavesTheSite() {
        // "localhost" and "127.0.0.1" are the same server but different hosts, like site → elsewhere.
        val elsewhere = "http://127.0.0.1:${server.port}/landing"
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", elsewhere))
        server.enqueue(MockResponse().setBody("ok"))

        val request = Request.Builder()
            .url("http://localhost:${server.port}/start")
            .withLogin("localhost", mapOf("Cookie" to "sessionid=SECRET", "X-CSRFToken" to "TOKEN"))
            .build()
        Http().client.newCall(request).execute().use { assertEquals(200, it.code) }

        val onSite = server.takeRequest()
        assertEquals("sessionid=SECRET", onSite.getHeader("Cookie"))
        assertEquals("TOKEN", onSite.getHeader("X-CSRFToken"))

        val offSite = server.takeRequest()
        assertEquals("/landing", offSite.path)
        assertNull(offSite.getHeader("Cookie"))
        assertNull(offSite.getHeader("X-CSRFToken"))
    }

    @Test
    fun loginHeadersStayWithinTheSiteAcrossRedirects() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/next"))
        server.enqueue(MockResponse().setBody("ok"))

        val request = Request.Builder()
            .url("http://localhost:${server.port}/start")
            .withLogin("localhost", mapOf("Cookie" to "sessionid=SECRET"))
            .build()
        Http().client.newCall(request).execute().close()

        server.takeRequest()
        assertEquals("sessionid=SECRET", server.takeRequest().getHeader("Cookie"))
    }
}
