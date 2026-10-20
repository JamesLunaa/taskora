package com.example.taskora.reminders

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Some OEM skins kill or freeze background apps and drop their alarms unless the app is
 * exempt from battery optimization. On vivo OriginOS this was observed directly: alarms are
 * removed ~20s after the app leaves the screen (`dumpsys alarm` shows `Reason=frozen`).
 */
object BackgroundRestrictions {
    // Brands with aggressive background management (see dontkillmyapp.com).
    private val AGGRESSIVE_BRANDS = setOf(
        "vivo", "iqoo", "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus",
        "huawei", "honor", "samsung", "meizu", "asus",
    )

    fun isAggressiveBrand(manufacturer: String, brand: String): Boolean =
        manufacturer.lowercase() in AGGRESSIVE_BRANDS || brand.lowercase() in AGGRESSIVE_BRANDS

    /** True when reminders are at risk: an aggressive brand and no battery-optimization exemption. */
    fun mayBlockReminders(context: Context): Boolean {
        if (!isAggressiveBrand(Build.MANUFACTURER, Build.BRAND)) return false
        val power = context.getSystemService(PowerManager::class.java)
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The app's info page, where every OEM puts its per-app battery setting. The direct
     * "ignore optimizations" prompt needs a permission Play restricts, so it isn't used.
     */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
}
