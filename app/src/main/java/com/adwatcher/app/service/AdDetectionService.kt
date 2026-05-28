package com.adwatcher.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.adwatcher.app.analyzer.AppAnalyzer
import com.adwatcher.app.analyzer.UsageStatsHelper
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
    private lateinit var usageStatsHelper: UsageStatsHelper

    // Tracks package popup frequency to detect spam/freeze attacks
    private val appEventTimestamps = mutableMapOf<String, MutableList<Long>>()

    // Overlay detection state
    private var lastOverlayCheck: Long = 0L
    private val lastOverlayLogged = mutableMapOf<String, Long>()
    private val lastOverlayDismissed = mutableMapOf<String, Long>()

    // Long-term frequency tracking (hourly rolling window)
    private val hourlyPopupCounts = mutableMapOf<String, MutableList<Long>>()
    // Track popup redirection chains: which app was foreground → which app popped up
    private val popupAttributionHistory = mutableListOf<PopupAttribution>()

    override fun onCreate() {
        super.onCreate()
        appAnalyzer = AppAnalyzer(applicationContext)
        usageStatsHelper = UsageStatsHelper(applicationContext)
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
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val currentTimestamp = System.currentTimeMillis()

        // ── Always check for overlay windows (SYSTEM_ALERT_WINDOW popups) ──
        detectOverlayWindows(currentTimestamp)

        // ── If packageName is null, the event may be from an overlay ──
        val packageName = event.packageName?.toString() ?: return
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // TYPE_WINDOWS_CHANGED fires for many system-level changes;
            // only process it if we actually have a meaningful package.
            if (packageName == "android" || packageName == "com.android.systemui") return
        }

        // ── Step 1: Filter system UI noise ──
        if (packageName == "com.android.systemui" || 
            packageName == "android" || 
            packageName == this.packageName || 
            packageName == defaultLauncherPackage) {
            return
        }

        // ── Step 2: Filter only trusted system/manufacturer/big-tech packages ──
        // NOTE: Play Store apps are NOT filtered here — even clean-looking apps
        // from Play Store can show ad popups via WebView/Intent. We track their
        // frequency separately to distinguish normal popups from adware.
        if (appAnalyzer.isTrustedPackage(packageName)) {
            return
        }

        val installSource = getInstallSource(packageName)
        val isFromStore = installSource == "com.android.vending" || 
                          installSource == "com.amazon.venezia" || 
                          installSource == "com.sec.android.app.samsungapps"

        // ── Step 3: Throttle rapid duplicate events (same app within 1.5 seconds) ──
        if (packageName == lastLoggedPackage && (currentTimestamp - lastLoggedTimestamp) < 1500) {
            return
        }

        lastLoggedPackage = packageName
        lastLoggedTimestamp = currentTimestamp

        // ── Step 4: Detect popup spam attacks ──
        // Short-term: ≥ 3 popups in 30 seconds = immediate spam attack
        val shortHistory = appEventTimestamps.getOrPut(packageName) { mutableListOf() }
        shortHistory.removeAll { currentTimestamp - it > 30000 }
        shortHistory.add(currentTimestamp)
        val isSpamAttack = shortHistory.size >= 3

        // Long-term: ≥ 5 popups in 1 hour = high-frequency adware,
        // even if the app came from Play Store
        val hourly = hourlyPopupCounts.getOrPut(packageName) { mutableListOf() }
        hourly.removeAll { currentTimestamp - it > 3600000 }
        hourly.add(currentTimestamp)
        val isHighFrequency = hourly.size >= 5

        // ── Popup attribution: record what was happening before this popup ──
        val previousForeground = usageStatsHelper.getForegroundAppPastSeconds(3)
        if (previousForeground != null && previousForeground != packageName) {
            popupAttributionHistory.add(PopupAttribution(
                fromPackage = previousForeground,
                toPackage = packageName,
                timestamp = currentTimestamp
            ))
            // Keep only last 100 entries
            if (popupAttributionHistory.size > 100) {
                popupAttributionHistory.removeAt(0)
            }
        }

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

        // ── Determine detection method ──
        val detectionMethod = when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
            else -> null
        }

        // ── Step 6: Log the suspicious popup event ──
        serviceScope.launch {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || 
                               (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                if (isSystem) return@launch

                val appInstallSource = getInstallSource(packageName)
                val isSideloaded = appInstallSource != "com.android.vending" && 
                                   appInstallSource != "com.amazon.venezia" && 
                                   appInstallSource != "com.sec.android.app.samsungapps"

                val prevFg = usageStatsHelper.getForegroundAppPastSeconds(5)
                val recentApps = usageStatsHelper.getRecentForegroundApps(30).joinToString(", ")

                // Derive event type from behavioral signals
                val derivedEventType = when {
                    isSpamAttack -> "ATTACK"
                    isHighFrequency -> "HIGH_FREQ"
                    isFromStore -> "POPUP_STORE"
                    else -> "POPUP"
                }

                val log = PopupLog(
                    packageName = packageName,
                    appName = appLabel,
                    timestamp = currentTimestamp,
                    isSystemApp = false,
                    eventType = derivedEventType,
                    isSideloaded = isSideloaded,
                    isAttackState = isSpamAttack || isHighFrequency,
                    installSource = appInstallSource,
                    popupText = popupText.ifEmpty { null },
                    hasSuspiciousText = hasSuspiciousText,
                    containsUrl = containsUrl,
                    previousForegroundPackage = prevFg,
                    recentForegroundApps = recentApps.ifEmpty { null },
                    detectionMethod = detectionMethod
                )

                AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
            } catch (_: PackageManager.NameNotFoundException) {
                val prevFg = usageStatsHelper.getForegroundAppPastSeconds(5)
                val recentApps = usageStatsHelper.getRecentForegroundApps(30).joinToString(", ")

                val derivedEventType = when {
                    isSpamAttack -> "ATTACK"
                    isHighFrequency -> "HIGH_FREQ"
                    else -> "POPUP"
                }

                val log = PopupLog(
                    packageName = packageName,
                    appName = packageName,
                    timestamp = currentTimestamp,
                    isSystemApp = false,
                    eventType = derivedEventType,
                    isSideloaded = true,
                    isAttackState = isSpamAttack || isHighFrequency,
                    installSource = null,
                    popupText = popupText.ifEmpty { null },
                    hasSuspiciousText = hasSuspiciousText,
                    containsUrl = containsUrl,
                    previousForegroundPackage = prevFg,
                    recentForegroundApps = recentApps.ifEmpty { null },
                    detectionMethod = detectionMethod
                )
                AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

    // ════════════════════════════════════════════════════════════════
    // OVERLAY WINDOW DETECTION
    // Catches SYSTEM_ALERT_WINDOW / TYPE_APPLICATION_OVERLAY popups
    // that are invisible to TYPE_WINDOW_STATE_CHANGED.
    // ════════════════════════════════════════════════════════════════

    /**
     * Checks all currently displayed windows for overlay types
     * belonging to untrusted/suspicious apps.
     *
     * Uses [AccessibilityService.getWindows] (API 21+) to enumerate
     * all visible windows and identify overlay windows. Falls back
     * gracefully on older API levels.
     */
    private fun detectOverlayWindows(now: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        // Throttle: don't check more than once per cooldown
        if (now - lastOverlayCheck < OVERLAY_CHECK_COOLDOWN_MS) return
        lastOverlayCheck = now

        try {
            @Suppress("UNCHECKED_CAST")
            val allWindows = (windows as? List<*>) ?: return
            for (winObj in allWindows) {
                val winInfo = winObj as AccessibilityWindowInfo
                val windowType = winInfo.type
                // Only care about overlay window types
                if (windowType != OVERLAY_TYPE_MODERN &&
                    windowType != OVERLAY_TYPE_LEGACY_ALERT &&
                    windowType != OVERLAY_TYPE_LEGACY_OVERLAY) continue

                val root = winInfo.root
                val pkg = root?.packageName?.toString() ?: continue
                // Skip our own app, system apps, and trusted packages
                val myPackage = applicationContext.packageName
                if (pkg == myPackage || appAnalyzer.isTrustedPackage(pkg)) continue

                // Determine overlay detection method by window type
                val overlayMethod = when (windowType) {
                    OVERLAY_TYPE_MODERN -> "OVERLAY_APPLICATION"
                    OVERLAY_TYPE_LEGACY_ALERT -> "OVERLAY_LEGACY_ALERT"
                    OVERLAY_TYPE_LEGACY_OVERLAY -> "OVERLAY_LEGACY_OVERLAY"
                    else -> "OVERLAY_UNKNOWN"
                }

                // Check logging cooldown for this specific package
                val sinceLastLog = now - (lastOverlayLogged[pkg] ?: 0L)
                if (sinceLastLog < OVERLAY_LOG_COOLDOWN_MS) continue
                lastOverlayLogged[pkg] = now

                // Get window bounds to determine if it's full-screen
                val bounds = Rect()
                winInfo.getBoundsInScreen(bounds)
                val displayMetrics = resources.displayMetrics
                val isFullScreen = bounds.width() >= displayMetrics.widthPixels * 0.8 &&
                                   bounds.height() >= displayMetrics.heightPixels * 0.8

                // Extract title if available (API 33+)
                val windowTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    winInfo.title?.toString() ?: ""
                } else {
                    ""
                }
                val textLower = windowTitle.lowercase()
                val containsUrl = urlPattern.matcher(textLower).find() || textLower.contains("http")
                val hasSuspiciousText = SUSPICIOUS_KEYWORDS.any { textLower.contains(it) }

                // ── Log the overlay event ──
                serviceScope.launch {
                    try {
                        val appInfo = packageManager.getApplicationInfo(pkg, 0)
                        val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        if (isSystem) return@launch

                        val previousForeground = usageStatsHelper.getForegroundAppPastSeconds(5)
                        val recentApps = usageStatsHelper.getRecentForegroundApps(30).joinToString(", ")

                        val log = PopupLog(
                            packageName = pkg,
                            appName = appLabel,
                            timestamp = now,
                            isSystemApp = false,
                            eventType = if (isFullScreen) "OVERLAY_FULL" else "OVERLAY",
                            isSideloaded = true,
                            isAttackState = false,
                            installSource = getInstallSource(pkg),
                            popupText = windowTitle.ifEmpty { null },
                            hasSuspiciousText = hasSuspiciousText,
                            containsUrl = containsUrl,
                            previousForegroundPackage = previousForeground,
                            recentForegroundApps = recentApps.ifEmpty { null },
                            detectionMethod = overlayMethod
                        )
                        AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
                    } catch (_: PackageManager.NameNotFoundException) {
                        val previousForeground = usageStatsHelper.getForegroundAppPastSeconds(5)
                        val recentApps = usageStatsHelper.getRecentForegroundApps(30).joinToString(", ")

                        val log = PopupLog(
                            packageName = pkg,
                            appName = pkg,
                            timestamp = now,
                            isSystemApp = false,
                            eventType = if (isFullScreen) "OVERLAY_FULL" else "OVERLAY",
                            isSideloaded = true,
                            isAttackState = false,
                            installSource = null,
                            popupText = windowTitle.ifEmpty { null },
                            hasSuspiciousText = hasSuspiciousText,
                            containsUrl = containsUrl,
                            previousForegroundPackage = previousForeground,
                            recentForegroundApps = recentApps.ifEmpty { null },
                            detectionMethod = overlayMethod
                        )
                        AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
                    }
                }

                // ── Auto-dismiss if it's a full-screen overlay from suspicious app ──
                val sinceLastDismiss = now - (lastOverlayDismissed[pkg] ?: 0L)
                if (isFullScreen && sinceLastDismiss >= OVERLAY_DISMISS_COOLDOWN_MS) {
                    lastOverlayDismissed[pkg] = now
                    dismissOverlay()
                }
            }
        } catch (_: Exception) {
            // Silently fail - some devices restrict getWindows()
        }
    }

    /**
     * Dismisses an overlay window using the best available method
     * for the current Android version:
     * - API 24+: Gesture simulation (swipe down then up = back)
     * - API 16+: Global action HOME (more reliable than BACK for overlays)
     */
    private fun dismissOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // ── API 24+: Use gesture simulation ──
            // Swipe from bottom-left toward center — mimics the "back gesture"
            // Many overlays are dismissed this way on modern Android
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels

            val path = Path().apply {
                moveTo(width * 0.05f, height * 0.5f)   // start from left edge center
                lineTo(width * 0.15f, height * 0.5f)    // swipe right slightly
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 150L))
                .build()

            dispatchGesture(gesture, null, null)
        } else {
            // ── API 16-23: Use global action ──
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /**
     * Records which app was foreground when another app popped up —
     * helps detect adware that waits for specific apps to open.
     */
    private data class PopupAttribution(
        val fromPackage: String,
        val toPackage: String,
        val timestamp: Long
    )

    override fun onInterrupt() {
        // Required by AccessibilityService interface
    }

    companion object {
        private val urlPattern: Pattern = Pattern.compile(
            "https?://[\\w.-]+(:\\d+)?(/[\\w./%-]*)?",
            Pattern.CASE_INSENSITIVE
        )

        // Overlay window types detected via getWindows()
        private const val OVERLAY_TYPE_MODERN = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY      // API 26+
        private const val OVERLAY_TYPE_LEGACY_ALERT = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT       // Deprecated, <= API 25
        private const val OVERLAY_TYPE_LEGACY_OVERLAY = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY   // Deprecated, <= API 25

        // Cooldown constants (milliseconds)
        private const val OVERLAY_CHECK_COOLDOWN_MS = 3000L      // Don't enumerate windows more than once per 3s
        private const val OVERLAY_LOG_COOLDOWN_MS = 10000L       // Don't log the same overlay package more than once per 10s
        private const val OVERLAY_DISMISS_COOLDOWN_MS = 30000L   // Don't dismiss the same package more than once per 30s

        // Suspicious text keywords (same as popup detection but extended)
        private val SUSPICIOUS_KEYWORDS = listOf(
            "trúng thưởng", "quà tặng", "winner", "prize", "free", "congratulations",
            "bạn đã trúng", "nhận ngay", "khuyến mãi", "giảm giá", "sale off",
            "đăng ký", "xác nhận", "otp", "mật khẩu", "password", "verify",
            "tài khoản", "ngân hàng", "bank", "withdraw", "nạp thẻ",
            "vip", "lucky", "spin", "jackpot", "xu vàng", "hoàn trả",
            "1%", "delegate", "overlay", "window", "full screen"
        )
    }
}
