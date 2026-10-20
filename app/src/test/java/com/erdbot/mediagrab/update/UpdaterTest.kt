package com.erdbot.mediagrab.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun versionsCompareNumerically() {
        assertTrue(Updater.isNewer("1.1.0", "1.0.0"))
        assertTrue(Updater.isNewer("1.10.0", "1.9.2"))
        assertTrue(Updater.isNewer("2", "1.9.9"))
        assertTrue(Updater.isNewer("1.0.1", "1.0"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("1.0", "1.0.0"))
        assertFalse(Updater.isNewer("0.9.9", "1.0.0"))
        assertFalse(Updater.isNewer("1.0.0-beta", "1.0.0"))
    }

    /** Shape of api.github.com/repos/{owner}/{repo}/releases/latest. */
    private fun release(tag: String, vararg assets: String, prerelease: Boolean = false) = JSONObject(
        """{"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"Notes",
           "assets":[${assets.joinToString(",") { """{"name":"$it","size":123,"browser_download_url":"https://github.com/x/releases/download/$tag/$it"}""" }}]}""",
    )

    @Test
    fun newerReleaseWithApkIsOffered() {
        val update = Updater.parseRelease(release("v1.1.0", "notes.txt", "MediaGrab-v1.1.0.apk"), "1.0.0")!!
        assertEquals("1.1.0", update.version)
        assertEquals("https://github.com/x/releases/download/v1.1.0/MediaGrab-v1.1.0.apk", update.apkUrl)
        assertEquals("Notes", update.notes)
    }

    @Test
    fun sameOrOlderOrApklessOrPrereleaseIsIgnored() {
        assertNull(Updater.parseRelease(release("v1.0.0", "MediaGrab-v1.0.0.apk"), "1.0.0"))
        assertNull(Updater.parseRelease(release("v0.9.0", "MediaGrab-v0.9.0.apk"), "1.0.0"))
        assertNull(Updater.parseRelease(release("v1.1.0", "source.zip"), "1.0.0"))
        assertNull(Updater.parseRelease(release("v1.1.0", "MediaGrab.apk", prerelease = true), "1.0.0"))
    }
}
