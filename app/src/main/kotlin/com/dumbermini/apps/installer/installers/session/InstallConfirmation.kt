package com.dumbermini.apps.installer.installers.session

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Retains Android's confirmation request until it is answered or the session ends. */
internal object InstallConfirmation {
    private const val PREFERENCES = "pending_install_confirmations"
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()

    data class Request(val sessionId: Int, val intent: Intent)

    fun save(context: Context, sessionId: Int, intent: Intent) {
        if (sessionId < 0) return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(sessionId.toString(), intent.toUri(Intent.URI_INTENT_SCHEME)).apply()
        refresh()
    }

    fun remove(context: Context, sessionId: Int) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.contains(sessionId.toString())) {
            preferences.edit().remove(sessionId.toString()).apply()
            refresh()
        }
    }

    fun refresh() {
        revision.update { it + 1 }
    }

    fun next(context: Context): Request? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val installer = context.packageManager.packageInstaller
        for ((key, value) in preferences.all) {
            val id = key.toIntOrNull() ?: continue
            val session = installer.getSessionInfo(id)
            if (session == null) {
                remove(context, id)
                continue
            }
            // The pending-action broadcast can arrive just before Android marks it inactive.
            if (session.isActive) continue
            val intent = runCatching {
                Intent.parseUri(value as String, Intent.URI_INTENT_SCHEME)
            }.getOrNull()
            if (intent == null) {
                remove(context, id)
                continue
            }
            // Keep the system confirmation in More Apps' task, above its activity.
            intent.flags = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
            return Request(id, intent)
        }
        return null
    }
}
