package com.erdbot.mediagrab.extract.youtube

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class SapisidHashTest {

    @Test
    fun matchesYouTubeWebFormat() {
        val expected = MessageDigest.getInstance("SHA-1")
            .digest("1700000000 abcSAPISID https://www.youtube.com".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("SAPISIDHASH 1700000000_$expected", NewPipeHttp.sapisidHash("abcSAPISID", 1700000000))
    }
}
