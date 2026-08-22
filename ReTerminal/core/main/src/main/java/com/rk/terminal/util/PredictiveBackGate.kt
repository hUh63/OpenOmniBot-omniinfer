package com.rk.terminal.util

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Runtime gate for the predictive back gesture.
 *
 * The manifest flag android:enableOnBackInvokedCallback="true" is static and cannot be
 * toggled at runtime. Per the official docs, the system does not play predictive back
 * animations while an enabled OnBackPressedCallback exists, so the "off" state is
 * implemented by registering a consuming callback that falls back to the legacy
 * behavior (plain finish, no animation).
 * Docs: https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
 */
object PredictiveBackGate {
    private const val PREFS_NAME = "FlutterSharedPreferences"
    private const val KEY_PREDICTIVE_BACK = "flutter.predictive_back_enabled"

    /** Toggle state, default on. Changed from the Flutter settings page, persisted via shared_preferences. */
    fun isPredictiveBackEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREDICTIVE_BACK, true)

    /**
     * Installs the legacy-mode callback for an Activity without its own back handling:
     * when the toggle is ON the callback stays disabled (system plays predictive
     * animations and performs the default finish); when OFF the callback is enabled
     * and calls finish() directly (legacy behavior, no animation).
     * Re-synced on every ON_RESUME to pick up changes made in the Flutter settings page.
     */
    fun install(activity: ComponentActivity) {
        val callback = object : OnBackPressedCallback(!isPredictiveBackEnabled(activity)) {
            override fun handleOnBackPressed() {
                activity.finish()
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, callback)
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                callback.isEnabled = !isPredictiveBackEnabled(activity)
            }
        })
    }
}
