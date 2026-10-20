package com.erdbot.mediagrab.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erdbot.mediagrab.App
import com.erdbot.mediagrab.download.DownloadRecord
import com.erdbot.mediagrab.download.DownloadService
import com.erdbot.mediagrab.extract.ExtractionException
import com.erdbot.mediagrab.extract.Links
import com.erdbot.mediagrab.extract.LoginRequiredException
import com.erdbot.mediagrab.extract.MediaPost
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.extract.Variant
import com.erdbot.mediagrab.ui.theme.ThemeId
import com.erdbot.mediagrab.update.AppUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val input: String = "",
    val loading: Boolean = false,
    val post: MediaPost? = null,
    /** Chosen variant per media item index. */
    val selected: Map<Int, Variant> = emptyMap(),
    val error: String? = null,
    /** Which site's login would fix [error], if any. */
    val loginSite: String? = null,
    val instagramLoggedIn: Boolean = false,
    val facebookLoggedIn: Boolean = false,
    val xLoggedIn: Boolean = false,
    val youtubeLoggedIn: Boolean = false,
    val message: String? = null,
    /** A supported link found on the clipboard, offered as "Copied a link — use it?". */
    val clipSuggestion: String? = null,
    /** A newer release on GitHub, offered until installed or dismissed. */
    val update: AppUpdate? = null,
    /** Download progress of [update] (0..1, -1 unknown), null when not downloading. */
    val updateProgress: Float? = null,
    /** Result of a manual "Check for updates" in Settings. */
    val updateStatus: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val app = app as App
    private var job: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val downloads: StateFlow<List<DownloadRecord>> = this.app.downloads.records
    val theme: StateFlow<ThemeId> = this.app.settings.theme
    val suggestClipboard: StateFlow<Boolean> = this.app.settings.suggestClipboard
    val checkUpdates: StateFlow<Boolean> = this.app.settings.checkUpdates

    /** Links already offered or used, so the same copied link isn't suggested twice. */
    private val offeredLinks = mutableSetOf<String>()

    init {
        refreshLogins()
        if (this.app.settings.checkUpdates.value) checkForUpdate(manual = false)
    }

    fun setCheckUpdates(on: Boolean) = app.settings.setCheckUpdates(on)

    /** Asks GitHub for a newer release; a manual check also reports "up to date" and errors. */
    fun checkForUpdate(manual: Boolean = true) {
        if (manual) _state.update { it.copy(updateStatus = "Checking…") }
        viewModelScope.launch {
            try {
                val update = app.updater.check()
                _state.update {
                    it.copy(
                        update = update ?: it.update,
                        updateStatus = if (!manual) it.updateStatus else if (update != null) "Version ${update.version} is available" else "You're on the latest version",
                    )
                }
            } catch (e: ExtractionException) {
                if (manual) _state.update { it.copy(updateStatus = e.message) }
            } catch (e: org.json.JSONException) {
                if (manual) _state.update { it.copy(updateStatus = "GitHub sent an unexpected answer.") }
            }
        }
    }

    fun dismissUpdate() = _state.update { it.copy(update = null) }

    /** Downloads the update and opens Android's install screen. */
    fun installUpdate() {
        val update = _state.value.update ?: return
        if (_state.value.updateProgress != null) return
        if (!app.updater.canInstall()) {
            app.updater.openInstallPermissionSettings()
            _state.update { it.copy(message = "Allow MediaGrab to install apps, then tap Update again") }
            return
        }
        _state.update { it.copy(updateProgress = 0f) }
        viewModelScope.launch {
            try {
                val apk = app.updater.download(update) { p -> _state.update { it.copy(updateProgress = p) } }
                app.updater.install(apk)
                _state.update { it.copy(updateProgress = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // PackageInstaller reports bad APKs and session errors as IOException/SecurityException.
                _state.update { it.copy(updateProgress = null, message = e.message ?: "The update failed.") }
            }
        }
    }

    fun setTheme(id: ThemeId) = app.settings.setTheme(id)

    fun setSuggestClipboard(on: Boolean) {
        app.settings.setSuggestClipboard(on)
        if (!on) _state.update { it.copy(clipSuggestion = null) }
    }

    /** Called when the app comes to the front with the clipboard's text. */
    fun onClipboard(text: String?) {
        if (!app.settings.suggestClipboard.value) return
        val url = text?.let(Links::firstUrl) ?: return
        if (url in offeredLinks || url == Links.firstUrl(_state.value.input) || app.extractors.find(url) == null) return
        offeredLinks += url
        _state.update { it.copy(clipSuggestion = url) }
    }

    fun useClipSuggestion() {
        val url = _state.value.clipSuggestion ?: return
        _state.update { it.copy(clipSuggestion = null, input = url) }
        fetch()
    }

    fun dismissClipSuggestion() = _state.update { it.copy(clipSuggestion = null) }

    fun onInput(text: String) = _state.update { it.copy(input = text, error = null) }

    /** Called for text shared from another app: fill the box and fetch right away. */
    fun onShared(text: String) {
        onInput(Links.firstUrl(text) ?: text)
        fetch()
    }

    fun fetch() {
        val url = Links.firstUrl(_state.value.input)
        if (url == null) {
            _state.update { it.copy(error = "Paste an X, Instagram, Facebook, YouTube or TikTok link.") }
            return
        }
        job?.cancel()
        offeredLinks += url
        _state.update { it.copy(loading = true, error = null, post = null, loginSite = null, message = null, clipSuggestion = null) }
        job = viewModelScope.launch {
            try {
                val post = app.extractors.extract(url)
                _state.update { s ->
                    s.copy(
                        loading = false,
                        post = post,
                        selected = post.items.indices.associateWith { post.items[it].best },
                        error = if (post.items.isEmpty()) "This post has no photos or videos." else null,
                    )
                }
            } catch (e: ExtractionException) {
                val site = when (app.extractors.find(url)?.platform) {
                    Platform.INSTAGRAM -> LoginActivity.SITE_INSTAGRAM
                    Platform.FACEBOOK -> LoginActivity.SITE_FACEBOOK
                    Platform.TWITTER -> LoginActivity.SITE_X
                    Platform.YOUTUBE -> LoginActivity.SITE_YOUTUBE
                    else -> null
                }
                _state.update {
                    it.copy(loading = false, error = e.message, loginSite = site.takeIf { e is LoginRequiredException })
                }
            }
        }
    }

    fun select(index: Int, variant: Variant) = _state.update { it.copy(selected = it.selected + (index to variant)) }

    fun download(index: Int) {
        val s = _state.value
        val post = s.post ?: return
        DownloadService.enqueue(app, post, index, s.selected[index] ?: post.items[index].best)
        _state.update { it.copy(message = "Downloading — see the Media tab") }
    }

    fun downloadAll() {
        val post = _state.value.post ?: return
        post.items.indices.forEach { download(it) }
        _state.update { it.copy(message = "Downloading ${post.items.size} files — see the Media tab") }
    }

    fun retry(record: DownloadRecord) = DownloadService.retry(app, record.id)

    /** Deletes the file (we created it, so no permission is needed) and its history entry. */
    fun delete(record: DownloadRecord) {
        record.contentUri?.let { runCatching { app.contentResolver.delete(Uri.parse(it), null, null) } }
        app.downloads.remove(record.id)
    }

    /** Drops history entries whose file the user deleted elsewhere (e.g. in the Files app). */
    fun pruneMissing() {
        downloads.value.filter { it.contentUri != null }.forEach { record ->
            val exists = runCatching {
                app.contentResolver.query(Uri.parse(record.contentUri), arrayOf("_id"), null, null, null)
                    ?.use { it.moveToFirst() } ?: false
            }.getOrDefault(false)
            if (!exists) app.downloads.remove(record.id)
        }
    }

    fun refreshLogins() = _state.update {
        it.copy(
            instagramLoggedIn = WebSessions.instagram.isLoggedIn(),
            facebookLoggedIn = WebSessions.facebook.isLoggedIn(),
            xLoggedIn = WebSessions.x.isLoggedIn(),
            youtubeLoggedIn = WebSessions.youtube.isLoggedIn(),
        )
    }

    fun logout(site: String) {
        when (site) {
            LoginActivity.SITE_FACEBOOK -> WebSessions.facebook.logout()
            LoginActivity.SITE_X -> WebSessions.x.logout()
            LoginActivity.SITE_YOUTUBE -> WebSessions.youtube.logout()
            else -> WebSessions.instagram.logout()
        }
        refreshLogins()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
