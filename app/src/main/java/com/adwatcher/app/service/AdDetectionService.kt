package com.adwatcher.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.adwatcher.app.analyzer.AppAnalyzer
import com.adwatcher.app.data.AppDatabase
import com.adwatcher.app.data.PopupLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Background Accessibility Service that monitors window state changes.
 *
 * Core filtering logic:
 * - Trusted apps (system, Google, Samsung, manufacturer, big tech) are IGNORED entirely.
 * - Only popups from unknown/suspicious/sideloaded apps are logged.
 * - Rapid repeated popups from the same app are flagged as SPAM ATTACK.
 */
class AdDetectionService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var defaultLauncherPackage: String? = null
    private var lastLoggedPackage: String? = null
    private var lastLoggedTimestamp: Long = 0L
    private lateinit var appAnalyzer: AppAnalyzer

    // Tracks package popup frequency to detect spam/freeze attacks
    private val appEventTimestamps = mutableMapOf<String, MutableList<Long>>()

    override fun onCreate() {
        super.onCreate()
        appAnalyzer = AppAnalyzer(applicationContext)
        detectDefaultLauncher()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        detectDefaultLauncher()
    }

    private fun detectDefaultLauncher() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            defaultLauncherPackage = resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val currentTimestamp = System.currentTimeMillis()

        // ── Step 1: Filter system UI noise ──
        if (packageName == "com.android.systemui" || 
            packageName == "android" || 
            packageName == this.packageName || 
            packageName == defaultLauncherPackage) {
            return
        }

        // ── Step 2: Filter trusted apps and known installer sources ──
        // Avoid false positives for apps from official sources like Samsung, Google, or major vendors.
        val installSource = getInstallSource(packageName)
        if (appAnalyzer.isTrustedPackage(packageName) || appAnalyzer.isTrustedInstallSource(installSource)) {
            return
        }

        // ── Step 3: Throttle rapid duplicate events (same app within 1.5 seconds) ──
        if (packageName == lastLoggedPackage && (currentTimestamp - lastLoggedTimestamp) < 1500) {
            return
        }

        lastLoggedPackage = packageName
        lastLoggedTimestamp = currentTimestamp

        // ── Step 4: Detect popup spam attacks (≥ 3 popups in 30 seconds = attack) ──
        val history = appEventTimestamps.getOrPut(packageName) { mutableListOf() }
        history.removeAll { currentTimestamp - it > 30000 }
        history.add(currentTimestamp)
        val isSpamAttack = history.size >= 3

        // ── Step 5: Extract popup text content ──
        val popupText = event.text.joinToString(" ").take(200)
        val textLower = popupText.lowercase()
        val containsUrl = urlPattern.matcher(textLower).find() || textLower.contains("http")
        val suspiciousKeywords = listOf(
            "trúng thưởng", "quà tặng", "winner", "prize", "free", "congratulations",
            "bạn đã trúng", "nhận ngay", "khuyến mãi", "giảm giá", "sale off",
            "đăng ký", "xác nhận", "otp", "mật khẩu", "password", "verify",
            "tài khoản", "ngân hàng", "bank", "withdraw", "nạp thẻ",
            "vip", "lucky", "spin", "jackpot", "xu vàng", "hoàn trả"
        )
        val hasSuspiciousText = suspiciousKeywords.any { textLower.contains(it) }

        // ── Step 6: Log the suspicious popup event ──
        serviceScope.launch {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || 
                               (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                // Double-check: if somehow a system app slipped through, skip it
                if (isSystem) return@launch

                val appInstallSource = getInstallSource(packageName)
                val isSideloaded = appInstallSource != "com.android.vending" && 
                                   appInstallSource != "com.amazon.venezia" && 
                                   appInstallSource != "com.sec.android.app.samsungapps"

                val log = PopupLog(
                    packageName = packageName,
                    appName = appLabel,
                    timestamp = currentTimestamp,
                    isSystemApp = false,
                    eventType = if (isSpamAttack) "ATTACK" else "POPUP",
                    isSideloaded = isSideloaded,
                    isAttackState = isSpamAttack,
                    installSource = appInstallSource,
                    popupText = popupText.ifEmpty { null },
                    hasSuspiciousText = hasSuspiciousText,
                    containsUrl = containsUrl
                )

                AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
            } catch (e: PackageManager.NameNotFoundException) {
                // Unknown package = highly suspicious, always log it
                val log = PopupLog(
                    packageName = packageName,
                    appName = packageName,
                    timestamp = currentTimestamp,
                    isSystemApp = false,
                    eventType = if (isSpamAttack) "ATTACK" else "POPUP",
                    isSideloaded = true,
                    isAttackState = isSpamAttack,
                    installSource = null,
                    popupText = popupText.ifEmpty { null },
                    hasSuspiciousText = hasSuspiciousText,
                    containsUrl = containsUrl
                )
                AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private val urlPattern: Pattern = Pattern.compile(
            "https?://[\\w.-]+(:\\d+)?(/[\\w./%-]*)?",
            Pattern.CASE_INSENSITIVE
        )
    }

    private fun getInstallSource(packageName: String): String? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onInterrupt() {
        // Required by AccessibilityService interface
    }
}
