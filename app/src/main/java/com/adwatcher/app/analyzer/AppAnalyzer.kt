package com.adwatcher.app.analyzer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build


/**
 * Represents the analysis result for a single installed app.
 * isTrusted = true means the app belongs to a known manufacturer, big tech company, or system.
 * Only apps with isTrusted = false are considered "suspicious" and shown to the user.
 */
data class AppRiskInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val reasons: List<String>,
    val hasOverlayPermission: Boolean,
    val hasBootPermission: Boolean,
    val hasNoLauncher: Boolean,
    val isFakeSystemApp: Boolean,
    val isSideloaded: Boolean,
    val installSource: String?,
    val isWhitelisted: Boolean,
    val isTrusted: Boolean,
    val canUninstall: Boolean,
    // New permission checks
    val hasAccessibilityPermission: Boolean,
    val hasInstallPermission: Boolean,
    val hasSmsPermission: Boolean,
    val hasNotificationListener: Boolean,
    val hasQueryAllPackages: Boolean,
    val totalDangerousPermissions: Int,
    val hasAdSuspiciousPackage: Boolean,
    val hasDeviceAdminPermission: Boolean,
    val isActiveAdmin: Boolean,
    val hasUsageStatsPermission: Boolean,
    val isRecentlyInstalled: Boolean
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}

class AppAnalyzer(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    // ─────────────────────────────────────────────────────────────────────
    // TRUSTED PACKAGE PREFIXES
    // Any package starting with these prefixes is considered trusted
    // (system, manufacturer, or big tech company). These apps will be
    // filtered OUT of the suspicious list entirely.
    // ─────────────────────────────────────────────────────────────────────
    private val trustedPrefixes = listOf(
        // Core system and vendor packages
        "android",
        "com.android.",
        "com.google.",
        "com.samsung.",
        "com.sec.",
        "com.xiaomi.",
        "com.miui.",
        "com.huawei.",
        "com.honor.",
        "com.oppo.",
        "com.coloros.",
        "com.vivo.",
        "com.bbk.",
        "com.oneplus.",
        "net.oneplus.",
        "com.lenovo.",
        "com.motorola.",
        "com.lge.",
        "com.sony.",
        "com.sonymobile.",
        "com.asus.",
        "com.hmd.",
        "com.qualcomm.",
        "com.qti.",
        "com.mediatek.",

        // Major platform services
        "com.microsoft.",
        "com.facebook.",
        "com.instagram.android",
        "com.whatsapp",
        "com.amazon.",
        "com.netflix.",
        "com.spotify.",
        "com.snapchat.",
        "com.pinterest.",
        "com.linkedin.",
        "com.adobe.",
        "com.dropbox.",
        "com.paypal.",
        "com.zhiliaoapp.",
        "com.ss.android.",
        "com.apple.",

        // Trusted Vietnamese roots
        "com.viettel.",
        "vn.com.viettel.",
        "com.vng.",
        "vn.momo.",
        "com.vnpay.",
        "vn.tiki.",
        "com.shopee.",
        "com.lazada.",
        "com.grab.",
        "com.vnpt.",
        "com.vietnamobile.",
    )

    // Exact package names that are trusted even if their prefix doesn't match above
    private val trustedExactPackages = setOf(
        "com.adwatcher.app"  // Our own app
    )

    // Installer packages that are considered trusted sources or official stores.
    private val trustedInstallerPackages = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.amazon.venezia",
        "com.samsung.android.app.omcagent",
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "com.miui.global.packageinstaller",
        "com.oppo.market",
        "com.vivo.appstore",
        "com.oneplus.apkmanager"
    )

    private val trustedInstallerPrefixes = listOf(
        "com.android.",
        "com.google.",
        "com.samsung.",
        "com.sec.",
        "com.xiaomi.",
        "com.miui.",
        "com.huawei.",
        "com.oppo.",
        "com.oneplus.",
        "com.vivo.",
        "com.amazon.",
        "com.qualcomm.",
        "com.qti.",
        "com.mediatek.",
        "com.hmd.",
        "com.bbk."
    )

    /**
     * Checks if a package name belongs to a trusted source (system, manufacturer, big tech company, or trusted vendor).
     * This is the core filter that decides what the user sees vs what gets hidden.
     */
    fun isTrustedPackage(packageName: String): Boolean {
        if (trustedExactPackages.contains(packageName)) return true
        return trustedPrefixes.any { prefix -> packageName.startsWith(prefix) }
    }

    fun isTrustedInstallSource(installerPackageName: String?): Boolean {
        if (installerPackageName == null) return false
        if (trustedInstallerPackages.contains(installerPackageName)) return true
        return trustedInstallerPrefixes.any { prefix -> installerPackageName.startsWith(prefix) }
    }

    /**
     * Scans all installed applications and returns ONLY suspicious (non-trusted) apps
     * sorted by risk score. Trusted apps are excluded from the results entirely
     * to avoid cluttering the user's view.
     *
     * @param includeAllApps If true, returns all apps including trusted ones (for debugging).
     */
    fun scanInstalledApps(includeAllApps: Boolean = false, popupCounts: Map<String, Int> = emptyMap()): List<AppRiskInfo> {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
        val activeAdmins = devicePolicyManager?.activeAdmins?.map { it.packageName } ?: emptyList()

        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        val results = mutableListOf<AppRiskInfo>()

        for (appInfo in installedApps) {
            // Always exclude our own app
            if (appInfo.packageName == context.packageName) continue

            val appLabel = appInfo.loadLabel(packageManager).toString()
            val packageName = appInfo.packageName
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || 
                           (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val isTrustedPrefix = isTrustedPackage(packageName)
            val isTrusted = isSystem || isTrustedPrefix
            val canUninstall = !isSystem

            // Skip ONLY if it's a System app (cannot be uninstalled) AND we don't want all apps.
            // This allows us to scan trusted Play Store apps but assign them lower risk scores.
            if (!includeAllApps && !canUninstall) continue

            // ── Permission Analysis ──
            val permissions = getAppPermissions(packageName)
            val hasOverlay = permissions.contains("android.permission.SYSTEM_ALERT_WINDOW")
            val hasBoot = permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED")
            val hasAccessibility = permissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
            val hasInstall = permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES")
            val hasSms = permissions.contains("android.permission.READ_SMS") ||
                         permissions.contains("android.permission.RECEIVE_SMS")
            val hasNotificationListener = permissions.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
            val hasQueryAll = permissions.contains("android.permission.QUERY_ALL_PACKAGES")
            val hasInternet = permissions.contains("android.permission.INTERNET")
            val hasDeviceAdmin = permissions.contains("android.permission.BIND_DEVICE_ADMIN")
            val hasUsageStats = permissions.contains("android.permission.PACKAGE_USAGE_STATS")

            val isActiveAdmin = activeAdmins.contains(packageName)

            // Count dangerous permissions (exclude INTERNET as it's too common)
            val dangerousPerms = listOf(hasOverlay, hasBoot, hasAccessibility, hasInstall, hasSms, hasNotificationListener, hasQueryAll, hasDeviceAdmin, hasUsageStats)
            val totalDangerous = dangerousPerms.count { it }

            // Total permission count (including normal ones) — high count = aggressive
            val totalPermissionCount = permissions.size

            // ── Install Time ──
            val installTime = getInstallTime(packageName)
            val isRecentlyInstalled = installTime > 0 && (System.currentTimeMillis() - installTime) < 48 * 60 * 60 * 1000L

            // ── Launcher Visibility ──
            val hasNoLauncher = packageManager.getLaunchIntentForPackage(packageName) == null

            // ── Fake System Name Detection ──
            val labelLower = appLabel.lowercase()
            val pkgLower = packageName.lowercase()
            val suspiciousNames = listOf(
                "system", "settings", "carrier", "service",
                "samsung", "update", "security", "cleaner", "booster", "optimizer",
                "battery", "wifi", "vpn free", "phone manager"
            )
            val suspiciousBrandLabels = listOf(
                "google", "gmail", "drive", "docs", "chrome", "maps", "photos",
                "youtube", "play store", "google pay", "device care", "phone manager",
                "security center", "android system", "system ui"
            )
            val suspiciousBrandPackagePatterns = listOf(
                "google", "gmail", "drive", "docs", "chrome", "maps", "photos",
                "youtube", "playstore", "googlepay", "wallet"
            )
            val isFakeSystem = !isTrusted && suspiciousNames.any { labelLower.contains(it) }
            val isSuspiciousBrandLabel = !isTrusted && suspiciousBrandLabels.any { labelLower.contains(it) }
            val isSuspiciousBrandPackage = !isTrusted && suspiciousBrandPackagePatterns.any { pattern ->
                pkgLower.contains(pattern) && !pkgLower.startsWith("com.google.") && !pkgLower.startsWith("com.android.")
            }

            // ── Ad-Suspicious Package Name Detection ──
            // Detect packages that look like ad SDKs or adware (random names with "ad" pattern)
            val adSuspiciousPatterns = listOf(
                "ad", "ads", "advert", "monetiz", "sdk",
                "tracking", "analytics", "push", "campaign"
            )
            val isAdSuspiciousPackage = !isTrusted && !isSystem && 
                adSuspiciousPatterns.any { pattern ->
                    pkgLower.contains(pattern) && !pkgLower.startsWith("com.google.android.gms")
                }

            // ── Install Source Detection ──
            val installSource = getInstallSource(packageName)
            val isSideloaded = !isSystem && !isTrustedInstallSource(installSource)
            val isBrandMimicWithBadSource = (isSuspiciousBrandLabel || isSuspiciousBrandPackage) && !isTrustedInstallSource(installSource)

            val isWhitelisted = isTrustedPrefix

            // ── Soft Whitelist for Overlay ──
            val softWhitelist = listOf("com.facebook.orca", "com.zing.zalo", "com.viber.voip", "com.skype.raider")
            val isSoftWhitelisted = softWhitelist.contains(packageName)

            // ── Risk Score Calculation ──
            var score = 0
            val reasons = mutableListOf<String>()

            // Reduce score for official play store apps, but don't ignore them
            if (!isSideloaded && installSource == "com.android.vending") {
                score -= 10
            }
            if (isWhitelisted) {
                score -= 20
            }

            if (isFakeSystem) {
                score += 45
                reasons.add("Giả mạo hệ thống: Tên app chứa từ khóa nhạy cảm ('System', 'Settings', 'Security'...) nhưng không phải app chính hãng.")
            }
            if (isBrandMimicWithBadSource) {
                score += 50
                reasons.add("Tên app giống thương hiệu lớn/Google nhưng nguồn cài đặt không chính hãng. Đây là dấu hiệu của app lừa đảo/quảng cáo.")
            } else if (isSuspiciousBrandLabel || isSuspiciousBrandPackage) {
                score += 25
                reasons.add("Tên app giả mạo thương hiệu lớn/Google nhưng package không rõ nguồn chính hãng.")
            }

            // ── Individual Permission Scores ──
            if (hasAccessibility) {
                score += 35
                reasons.add("Quyền trợ năng: Có thể đọc mọi nội dung trên màn hình, tự động thao tác hoặc thu thập dữ liệu nhạy cảm.")
            }
            if (hasOverlay) {
                score += 30
                reasons.add("Quyền vẽ đè màn hình: Có khả năng tự động hiện popup quảng cáo full-screen đè lên ứng dụng khác.")
            }
            if (hasInstall) {
                score += 25
                reasons.add("Quyền cài đặt ứng dụng: Có thể tự động tải và cài đặt APK khác mà người dùng không biết.")
            }
            if (hasNoLauncher) {
                score += 40
                reasons.add("Không có icon: Cố tình ẩn mình khỏi màn hình chính, đặc điểm chung của Adware.")
            }
            if (isSideloaded) {
                score += 20
                reasons.add("Nguồn không xác định: Được cài bằng file APK bên ngoài, không qua kiểm duyệt của Google Play.")
            }
            if (hasSms) {
                score += 18
                reasons.add("Quyền đọc/tin nhắn: Có thể chặn hoặc đánh cắp mã OTP và tin nhắn xác thực ngân hàng.")
            }
            if (hasBoot) {
                score += 15
                reasons.add("Tự khởi chạy: Kích hoạt ngay khi bật máy, có thể chạy dịch vụ quảng cáo ngầm liên tục.")
            }
            if (hasNotificationListener) {
                score += 15
                reasons.add("Quyền đọc thông báo: Có thể đọc và tương tác với thông báo từ các ứng dụng khác (kể cả OTP).")
            }
            if (hasQueryAll) {
                score += 10
                reasons.add("Quyền xem danh sách app: Có thể phát hiện bạn đang cài app ngân hàng nào để nhắm mục tiêu lừa đảo.")
            }
            if (isAdSuspiciousPackage) {
                score += 15
                reasons.add("Tên package đáng ngờ: Chứa từ khóa liên quan đến quảng cáo/SDK không rõ nguồn gốc.")
            }

            // New scores
            if (hasUsageStats) {
                score += 20
                reasons.add("Quyền truy cập dữ liệu sử dụng: Có thể theo dõi bạn đang mở app nào để hiển thị quảng cáo mục tiêu.")
            }
            if (hasDeviceAdmin) {
                score += 20
                reasons.add("Quyền Quản trị thiết bị: Rất khó gỡ cài đặt nếu được kích hoạt.")
            }
            if (isActiveAdmin) {
                score += 30
                reasons.add("Đang là Quản trị thiết bị: Ứng dụng này đang nắm quyền cao nhất, bạn cần hủy cấp quyền trước khi gỡ.")
            }
            if (isRecentlyInstalled) {
                score += 15
                reasons.add("Mới cài đặt: Ứng dụng vừa được cài trong vòng 48h, có khả năng là nguyên nhân gây ra popup gần đây.")
            }

            // ── Behavioral Heuristics (no-permission adware detection) ──
            // Apps can show ads with ONLY internet — no dangerous permissions needed.
            // These heuristics detect indicators of adware behavior even without dangerous perms.
            if (totalPermissionCount > 15) {
                score += 8
                reasons.add("Số lượng quyền cao: Yêu cầu $totalPermissionCount quyền khác nhau, có dấu hiệu thu thập dữ liệu quá mức.")
            }
            if (hasInternet && hasBoot && !isTrustedPrefix) {
                score += 8
                reasons.add("Internet + Tự khởi chạy: Có thể kết nối mạng ngay sau khi máy khởi động để tải quảng cáo về.")
            }
            if (hasInternet && hasUsageStats) {
                score += 10
                reasons.add("Internet + Theo dõi app: Có thể theo dõi bạn đang dùng app gì để hiển thị quảng cáo nhắm mục tiêu.")
            }
            if (hasInternet && isRecentlyInstalled) {
                score += 8
                reasons.add("Internet + Mới cài: Vừa cài xong đã có khả năng kết nối mạng — cần chú ý nếu gần đây có popup lạ.")
            }
            if (hasNoLauncher && hasInternet) {
                score += 10
                reasons.add("Không icon + Internet: Có thể chạy ngầm và tải nội dung quảng cáo mà người dùng không biết.")
            }

            // ── Popup Frequency Scoring ──
            // Apps with detected popup logs get extra score based on count
            val popupCount = popupCounts[packageName] ?: 0
            if (popupCount > 0) {
                val popupScore = minOf(popupCount * 3, 25)
                score += popupScore
                reasons.add("Phát hiện $popupCount popup quảng cáo: Popup xuất hiện $popupCount lần từ ứng dụng này, có biểu hiện của Adware hiển thị quảng cáo.")
            }

            // Soft whitelist reduction
            if (isSoftWhitelisted && hasOverlay) {
                score -= 30
                reasons.add("Ứng dụng phổ biến (Zalo/Messenger): Thường xuyên dùng quyền vẽ đè hợp lệ.")
            }

            // ── Permission Synergy Scoring (dangerous combinations) ──
            if (hasOverlay && hasAccessibility) {
                score += 25
                reasons.add("Kết hợp Vẽ đè + Trợ năng: Có thể tự động nhấn vào quảng cáo mà không cần người dùng tương tác.")
            }
            if (hasOverlay && hasInstall) {
                score += 20
                reasons.add("Kết hợp Vẽ đè + Cài đặt: Có thể tự động cài ứng dụng qua popup giả mạo.")
            }
            if (hasSms && hasInternet) {
                score += 18
                reasons.add("Kết hợp SMS + Internet: Có thể gửi mã OTP đánh cắp ra ngoài qua mạng.")
            }
            if (hasAccessibility && hasBoot) {
                score += 12
                reasons.add("Kết hợp Trợ năng + Tự khởi chạy: Có thể gián điệp liên tục ngay sau mỗi lần khởi động máy.")
            }
            if (hasInstall && hasBoot) {
                score += 10
                reasons.add("Kết hợp Cài đặt + Tự khởi chạy: Có thể âm thầm cài ứng dụng mới ngay sau khi máy khởi động.")
            }
            if (totalDangerous >= 4) {
                score += 15
                reasons.add("Tích lũy nhiều quyền nguy hiểm: Ứng dụng yêu cầu $totalDangerous quyền nhạy cảm khác nhau.")
            }

            // Cap score between 0 and 100
            score = score.coerceIn(0, 100)

            val riskLevel = when {
                score >= 60 -> RiskLevel.HIGH
                score >= 30 -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

            results.add(
                AppRiskInfo(
                    packageName = packageName,
                    appName = appLabel,
                    isSystemApp = isSystem,
                    riskScore = score,
                    riskLevel = riskLevel,
                    reasons = reasons,
                    hasOverlayPermission = hasOverlay,
                    hasBootPermission = hasBoot,
                    hasNoLauncher = hasNoLauncher,
                    isFakeSystemApp = isFakeSystem,
                    isSideloaded = isSideloaded,
                    installSource = installSource,
                    isWhitelisted = isWhitelisted,
                    isTrusted = isTrusted,
                    canUninstall = canUninstall,
                    hasAccessibilityPermission = hasAccessibility,
                    hasInstallPermission = hasInstall,
                    hasSmsPermission = hasSms,
                    hasNotificationListener = hasNotificationListener,
                    hasQueryAllPackages = hasQueryAll,
                    totalDangerousPermissions = totalDangerous,
                    hasAdSuspiciousPackage = isAdSuspiciousPackage,
                    hasDeviceAdminPermission = hasDeviceAdmin,
                    isActiveAdmin = isActiveAdmin,
                    hasUsageStatsPermission = hasUsageStats,
                    isRecentlyInstalled = isRecentlyInstalled
                )
            )
        }

        // Sort by risk score (highest first)
        return results.sortedWith(
            compareByDescending<AppRiskInfo> { it.riskScore }
                .thenBy { it.appName }
        )
    }

    private fun getInstallSource(packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getAppPermissions(packageName: String): List<String> {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            packageInfo.requestedPermissions?.toList() ?: emptyList()
        } catch (_: PackageManager.NameNotFoundException) {
            emptyList()
        }
    }

    private fun getInstallTime(packageName: String): Long {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.firstInstallTime
        } catch (_: Exception) {
            0L
        }
    }
}
