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
    val hasAdSuspiciousPackage: Boolean
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
    fun scanInstalledApps(includeAllApps: Boolean = false): List<AppRiskInfo> {
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

            val isTrusted = isSystem || isTrustedPackage(packageName)
            val canUninstall = !isSystem

            // Skip trusted apps entirely unless caller explicitly wants all
            if (!includeAllApps && (isTrusted || !canUninstall)) continue

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

            // Count dangerous permissions (exclude INTERNET as it's too common)
            val dangerousPerms = listOf(hasOverlay, hasBoot, hasAccessibility, hasInstall, hasSms, hasNotificationListener, hasQueryAll)
            val totalDangerous = dangerousPerms.count { it }

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

            val isWhitelisted = isTrusted

            // ── Risk Score Calculation ──
            var score = 0
            val reasons = mutableListOf<String>()

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
                score += 25
                reasons.add("Ẩn icon: Không xuất hiện trong danh sách ứng dụng, người dùng rất khó phát hiện và gỡ cài đặt.")
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

            // Cap score at 100
            score = score.coerceAtMost(100)

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
                    hasAdSuspiciousPackage = isAdSuspiciousPackage
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
        } catch (e: Exception) {
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
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }
    }
}
