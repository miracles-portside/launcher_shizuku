package com.example.launcher

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuHelper {

    const val REQUEST_CODE = 4242

    sealed interface State {
        data object NotInstalled : State
        data object NotRunning : State
        data object PermissionRequired : State
        data object Ready : State
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun ping(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean =
        ping() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun currentState(context: Context): State = when {
        !isInstalled(context) -> State.NotInstalled
        !ping() -> State.NotRunning
        !hasPermission() -> State.PermissionRequired
        else -> State.Ready
    }

    fun requestPermission() {
        if (ping() && !Shizuku.isPreV11()) {
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> }

    fun addListeners() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
    }

    fun removeListeners() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }

    suspend fun runCommand(command: String): String = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext "ERROR: no permission"
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as ShizukuRemoteProcess

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            process.destroy()

            if (error.isNotBlank() && output.isBlank()) "ERROR: $error" else output.trim()
        } catch (e: Exception) {
            "EXCEPTION: ${e.message}"
        }
    }

    private fun format(label: String, out: String): String =
        if (out.isBlank() || out.startsWith("ERROR") || out.startsWith("EXCEPTION"))
            "$label → $out" else "$label → OK"

    suspend fun enableGestures(): List<String> = listOf(
        "force_fsg_nav_bar=1" to runCommand("settings put global force_fsg_nav_bar 1"),
        "gestural overlay" to runCommand("cmd overlay enable com.android.internal.systemui.navbar.gestural"),
        "hide gesture line" to runCommand("settings put global hide_gesture_line 1")
    ).map { (label, out) -> format(label, out) }

    suspend fun disableGestures(): List<String> = listOf(
        "force_fsg_nav_bar=0" to runCommand("settings put global force_fsg_nav_bar 0"),
        "threebutton overlay" to runCommand("cmd overlay enable com.android.internal.systemui.navbar.threebutton")
    ).map { (label, out) -> format(label, out) }

    // ---- Gesture preference ----

    fun isGesturesEnabled(context: Context): Boolean =
        context.getSharedPreferences(GestureAccessibilityService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(GestureAccessibilityService.KEY_GESTURES, false)

    fun setGesturesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(GestureAccessibilityService.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(GestureAccessibilityService.KEY_GESTURES, enabled).apply()
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = context.packageName + "/" + GestureAccessibilityService::class.java.name
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }
}
