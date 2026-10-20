package com.erdbot.mediagrab.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Receives the installer's status: shows its confirmation screen, or says why it failed. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The app restarts into the new version.
            else -> {
                val reason = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> return // The user tapped Cancel.
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "The update is signed with a different key. Uninstall MediaGrab and install it from GitHub."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage for the update."
                    else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update couldn't be installed."
                }
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            }
        }
    }
}
