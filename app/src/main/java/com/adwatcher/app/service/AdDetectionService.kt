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
import com.adwatcher.app.ProtectionSettings
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
    private val overlayEventTimestamps = mutableMapOf<String, MutableList<Long>>()
    private var lastGlobalDismiss: Long = 0L
    private var adWatcherGuardPackage: String? = null
    private var adWatcherGuardStartedAt: Long = 0L
    private var adWatcherGuardRestoreCount: Int = 0

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
        val previousForegroundShort = usageStatsHelper.getForegroundAppPastSeconds(2)
        val latestForeground = usageStatsHelper.getLatestForegroundApp(5)
        val recentForegroundShort = usageStatsHelper.getRecentForegroundApps(10)
        val preFilterPopupText = event.text.joinToString(" ").take(200)
        val preFilterTextLower = preFilterPopupText.lowercase()
        val preFilterContainsUrl = urlPattern.matcher(preFilterTextLower).find() || preFilterTextLower.contains("http")
        val preFilterHasSuspiciousText = SUSPICIOUS_KEYWORDS.any { preFilterTextLower.contains(it) }
        val isAdWatcherInterrupted = previousForegroundShort == this.packageName ||
                latestForeground == this.packageName
        val isForegroundInterrupt = previousForegroundShort != null &&
                previousForegroundShort != packageName &&
                previousForegroundShort != defaultLauncherPackage &&
                previousForegroundShort != "android" &&
                previousForegroundShort != "com.android.systemui"

        val browserSourceCandidate = findBrowserHandoffSource(
            currentPackage = packageName,
            previousForeground = previousForegroundShort,
            recentForegroundApps = recentForegroundShort
        )

        if (appAnalyzer.isTrustedPackage(packageName)) {
            if (browserSourceCandidate != null) {
                logBrowserHandoff(
                    sourcePackage = browserSourceCandidate,
                    browserPackage = packageName,
                    timestamp = currentTimestamp,
                    popupText = preFilterPopupText,
                    containsUrl = preFilterContainsUrl,
                    hasSuspiciousText = preFilterHasSuspiciousText,
                    recentForegroundApps = recentForegroundShort
                )
            }
            return
        }

        val installSource = getInstallSource(packageName)
        val isFromStore = isStoreInstallSource(installSource)
        if (previousForegroundShort == packageName) {
            return
        }

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
        val hasLauncher = packageManager.getLaunchIntentForPackage(packageName) != null
        val isHiddenAppInterrupt = isForegroundInterrupt && !hasLauncher
        val shouldLogWindowPopup = isAdWatcherInterrupted ||
                hasSuspiciousText ||
                containsUrl ||
                isSpamAttack ||
                isHighFrequency ||
                isHiddenAppInterrupt

        if (!shouldLogWindowPopup) {
            return
        }

        if (isAdWatcherInterrupted && ProtectionSettings.isStrongProtectionEnabled(applicationContext)) {
            restoreAdWatcherForeground(
                now = currentTimestamp,
                reasonPackage = packageName,
                strongSignal = isSpamAttack || isHighFrequency || hasSuspiciousText || containsUrl || isHiddenAppInterrupt
            )
        }

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
                val isSideloaded = !isStoreInstallSource(appInstallSource)

                val prevFg = usageStatsHelper.getForegroundAppPastSeconds(5)
                val recentApps = usageStatsHelper.getRecentForegroundApps(30).joinToString(", ")
                val quickPopup = prevFg != null && prevFg != packageName

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
                    popupText = null,
                    hasSuspiciousText = hasSuspiciousText,
                    containsUrl = containsUrl,
                    previousForegroundPackage = prevFg,
                    recentForegroundApps = recentApps.ifEmpty { null },
                    detectionMethod = detectionMethod,
                    suspectedSourcePackage = if (quickPopup) prevFg else null,
                    triggerPackage = packageName,
                    attributionConfidence = when {
                        isSpamAttack || isHighFrequency || hasSuspiciousText -> "HIGH"
                        quickPopup -> "MEDIUM"
                        else -> "LOW"
                    },
                    isQuickPopup = quickPopup,
                    isBrowserHandoff = false
                )

                AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
            } catch (_: PackageManager.NameNotFoundException) {
                val prevFg = usageStatsHelper.getForegroundAppPastSeconds(5)
                val recentApps = usageStatsHelper.getRecentForegroundApps(30).joinToString(", ")
                val quickPopup = prevFg != null && prevFg != packageName

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
                    popupText = null,
                    hasSuspiciousText = hasSuspiciousText,
                    containsUrl = containsUrl,
                    previousForegroundPackage = prevFg,
                    recentForegroundApps = recentApps.ifEmpty { null },
                    detectionMethod = detectionMethod,
                    suspectedSourcePackage = if (quickPopup) prevFg else null,
                    triggerPackage = packageName,
                    attributionConfidence = when {
                        isSpamAttack || isHighFrequency || hasSuspiciousText -> "HIGH"
                        quickPopup -> "MEDIUM"
                        else -> "LOW"
                    },
                    isQuickPopup = quickPopup,
                    isBrowserHandoff = false
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

    private fun isStoreInstallSource(installSource: String?): Boolean {
        return installSource == "com.android.vending" ||
                installSource == "com.amazon.venezia" ||
                installSource == "com.sec.android.app.samsungapps" ||
                installSource == "com.samsung.android.app.omcagent"
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        return packageName == "com.android.chrome" ||
                packageName == "com.sec.android.app.sbrowser" ||
                packageName == "org.mozilla.firefox" ||
                packageName == "com.microsoft.emmx" ||
                packageName == "com.opera.browser" ||
                packageName == "com.brave.browser"
    }

    private fun findBrowserHandoffSource(
        currentPackage: String,
        previousForeground: String?,
        recentForegroundApps: List<String>
    ): String? {
        if (!isBrowserPackage(currentPackage)) return null
        val candidates = mutableListOf<String>()
        previousForeground?.let { candidates.add(it) }
        candidates.addAll(recentForegroundApps.asReversed())
        return candidates.firstOrNull { candidate ->
            candidate != currentPackage &&
                    candidate != packageName &&
                    candidate != defaultLauncherPackage &&
                    !appAnalyzer.isTrustedPackage(candidate)
        }
    }

    private fun logBrowserHandoff(
        sourcePackage: String,
        browserPackage: String,
        timestamp: Long,
        popupText: String,
        containsUrl: Boolean,
        hasSuspiciousText: Boolean,
        recentForegroundApps: List<String>
    ) {
        serviceScope.launch {
            try {
                val appInfo = packageManager.getApplicationInfo(sourcePackage, 0)
                val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                val installSource = getInstallSource(sourcePackage)
                val log = PopupLog(
                    packageName = sourcePackage,
                    appName = appLabel,
                    timestamp = timestamp,
                    isSystemApp = false,
                    eventType = "BROWSER_HANDOFF",
                    isSideloaded = !isStoreInstallSource(installSource),
                    isAttackState = hasSuspiciousText || containsUrl,
                    installSource = installSource,
                    popupText = null,
                    hasSuspiciousText = hasSuspiciousText,
                    containsUrl = containsUrl,
                    previousForegroundPackage = sourcePackage,
                    recentForegroundApps = recentForegroundApps.joinToString(", ").ifEmpty { null },
                    detectionMethod = "BROWSER_HANDOFF",
                    suspectedSourcePackage = sourcePackage,
                    triggerPackage = browserPackage,
                    attributionConfidence = if (hasSuspiciousText || containsUrl) "HIGH" else "MEDIUM",
                    isQuickPopup = true,
                    isBrowserHandoff = true
                )
                AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
            } catch (_: Exception) {
                // Ignore attribution failures; normal popup detection still runs.
            }
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
        // Throttle: don't check more than once per cooldown
        if (now - lastOverlayCheck < OVERLAY_CHECK_COOLDOWN_MS) return
        lastOverlayCheck = now

        try {
            @Suppress("UNCHECKED_CAST")
            val allWindows = (windows as? List<*>) ?: return
            val latestForeground = usageStatsHelper.getLatestForegroundApp(5)
            val isAdWatcherForeground = latestForeground == applicationContext.packageName
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
                if (pkg == latestForeground) continue

                // Determine overlay detection method by window type
                val overlayMethod = when (windowType) {
                    OVERLAY_TYPE_MODERN -> "OVERLAY_APPLICATION"
                    OVERLAY_TYPE_LEGACY_ALERT -> "OVERLAY_LEGACY_ALERT"
                    OVERLAY_TYPE_LEGACY_OVERLAY -> "OVERLAY_LEGACY_OVERLAY"
                    else -> "OVERLAY_UNKNOWN"
                }

                // Check logging cooldown without skipping active protection.
                val sinceLastLog = now - (lastOverlayLogged[pkg] ?: 0L)
                val shouldLogOverlay = sinceLastLog >= OVERLAY_LOG_COOLDOWN_MS
                if (shouldLogOverlay) lastOverlayLogged[pkg] = now

                // Get window bounds to determine if it's full-screen
                val bounds = Rect()
                winInfo.getBoundsInScreen(bounds)
                val displayMetrics = resources.displayMetrics
                val isFullScreen = bounds.width() >= displayMetrics.widthPixels * 0.8 &&
                                   bounds.height() >= displayMetrics.heightPixels * 0.8

                val overlayHistory = overlayEventTimestamps.getOrPut(pkg) { mutableListOf() }
                overlayHistory.removeAll { now - it > OVERLAY_ATTACK_WINDOW_MS }
                overlayHistory.add(now)
                val isOverlayAttack = overlayHistory.size >= OVERLAY_ATTACK_THRESHOLD

                // Extract title if available (API 33+)
                val windowTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    winInfo.title?.toString() ?: ""
                } else {
                    ""
                }
                val textLower = windowTitle.lowercase()
                val containsUrl = urlPattern.matcher(textLower).find() || textLower.contains("http")
                val hasSuspiciousText = SUSPICIOUS_KEYWORDS.any { textLower.contains(it) }
                val isStrongOverlay = isFullScreen || isOverlayAttack || hasSuspiciousText || containsUrl

                // ── Log the overlay event ──
                if (isStrongOverlay && (shouldLogOverlay || isOverlayAttack)) {
                    serviceScope.launch {
                    try {
                        val appInfo = packageManager.getApplicationInfo(pkg, 0)
                        val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        if (isSystem) return@launch

                        val previousForeground = usageStatsHelper.getForegroundAppPastSeconds(5)
                        val recentApps = usageStatsHelper.getRecentForegroundApps(30).joinToString(", ")
                        val installSource = getInstallSource(pkg)
                        val quickPopup = previousForeground != null && previousForeground != pkg

                        val log = PopupLog(
                            packageName = pkg,
                            appName = appLabel,
                            timestamp = now,
                            isSystemApp = false,
                            eventType = when {
                                isOverlayAttack -> "ATTACK"
                                isFullScreen -> "OVERLAY_FULL"
                                else -> "OVERLAY"
                            },
                            isSideloaded = !isStoreInstallSource(installSource),
                            isAttackState = isOverlayAttack,
                            installSource = installSource,
                            popupText = null,
                            hasSuspiciousText = hasSuspiciousText,
                            containsUrl = containsUrl,
                            previousForegroundPackage = previousForeground,
                            recentForegroundApps = recentApps.ifEmpty { null },
                            detectionMethod = overlayMethod,
                            suspectedSourcePackage = if (quickPopup) previousForeground else null,
                            triggerPackage = pkg,
                            attributionConfidence = when {
                                isOverlayAttack || hasSuspiciousText -> "HIGH"
                                quickPopup -> "MEDIUM"
                                else -> "LOW"
                            },
                            isQuickPopup = quickPopup,
                            isBrowserHandoff = false
                        )
                        AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
                    } catch (_: PackageManager.NameNotFoundException) {
                        val previousForeground = usageStatsHelper.getForegroundAppPastSeconds(5)
                        val recentApps = usageStatsHelper.getRecentForegroundApps(30).joinToString(", ")
                        val quickPopup = previousForeground != null && previousForeground != pkg

                        val log = PopupLog(
                            packageName = pkg,
                            appName = pkg,
                            timestamp = now,
                            isSystemApp = false,
                            eventType = if (isOverlayAttack) "ATTACK" else if (isFullScreen) "OVERLAY_FULL" else "OVERLAY",
                            isSideloaded = true,
                            isAttackState = isOverlayAttack,
                            installSource = null,
                            popupText = null,
                            hasSuspiciousText = hasSuspiciousText,
                            containsUrl = containsUrl,
                            previousForegroundPackage = previousForeground,
                            recentForegroundApps = recentApps.ifEmpty { null },
                            detectionMethod = overlayMethod,
                            suspectedSourcePackage = if (quickPopup) previousForeground else null,
                            triggerPackage = pkg,
                            attributionConfidence = when {
                                isOverlayAttack || hasSuspiciousText -> "HIGH"
                                quickPopup -> "MEDIUM"
                                else -> "LOW"
                            },
                            isQuickPopup = quickPopup,
                            isBrowserHandoff = false
                        )
                        AppDatabase.getDatabase(applicationContext).popupLogDao().insertLog(log)
                    }
                    }
                }

                // ── Auto-dismiss disruptive overlays from suspicious apps ──
                val sinceLastDismiss = now - (lastOverlayDismissed[pkg] ?: 0L)
                val dismissCooldown = if (isOverlayAttack) {
                    OVERLAY_ATTACK_DISMISS_COOLDOWN_MS
                } else {
                    OVERLAY_DISMISS_COOLDOWN_MS
                }
                if (isAdWatcherForeground &&
                    ProtectionSettings.isStrongProtectionEnabled(applicationContext) &&
                    isStrongOverlay &&
                    sinceLastDismiss >= dismissCooldown
                ) {
                    lastOverlayDismissed[pkg] = now
                    restoreAdWatcherForeground(
                        now = now,
                        reasonPackage = pkg,
                        strongSignal = isOverlayAttack || hasSuspiciousText || containsUrl
                    )
                }
            }
        } catch (_: Exception) {
            // Silently fail - some devices restrict getWindows()
        }
    }

    private fun restoreAdWatcherForeground(now: Long, reasonPackage: String, strongSignal: Boolean) {
        if (now - lastGlobalDismiss < GLOBAL_DISMISS_COOLDOWN_MS) return

        val isSameGuardWindow = adWatcherGuardPackage == reasonPackage &&
                now - adWatcherGuardStartedAt <= ADWATCHER_GUARD_WINDOW_MS
        if (!isSameGuardWindow) {
            adWatcherGuardPackage = reasonPackage
            adWatcherGuardStartedAt = now
            adWatcherGuardRestoreCount = 0
        }

        val maxRestores = if (strongSignal) {
            ADWATCHER_GUARD_MAX_STRONG_RESTORES
        } else {
            ADWATCHER_GUARD_MAX_LIGHT_RESTORES
        }
        if (adWatcherGuardRestoreCount >= maxRestores) return

        lastGlobalDismiss = now
        adWatcherGuardRestoreCount += 1

        try {
            val intent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            if (intent != null) {
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                startActivity(intent)
            }
        } catch (_: Exception) {
            // Avoid Back/Home here; protection should not manipulate other apps.
        }
    }

    @Suppress("unused")
    private fun dismissOverlayGestureFallback() {
        // Intentionally disabled: protection restores AdWatcher instead of
        // sending gestures, Back, or Home into another app.
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
        private const val OVERLAY_DISMISS_COOLDOWN_MS = 8000L
        private const val OVERLAY_ATTACK_DISMISS_COOLDOWN_MS = 1500L
        private const val OVERLAY_ATTACK_WINDOW_MS = 15000L
        private const val OVERLAY_ATTACK_THRESHOLD = 2
        private const val GLOBAL_DISMISS_COOLDOWN_MS = 900L
        private const val ADWATCHER_GUARD_WINDOW_MS = 6000L
        private const val ADWATCHER_GUARD_MAX_LIGHT_RESTORES = 2
        private const val ADWATCHER_GUARD_MAX_STRONG_RESTORES = 3

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
