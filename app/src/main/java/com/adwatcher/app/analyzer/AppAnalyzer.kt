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
    val isTrusted: Boolean  // True = system/manufacturer/big-corp app, hidden from user by default
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
        // Android OS & Core
        "android",
        "com.android.",
        "com.google.android.",
        "com.google.",

        // Samsung
        "com.samsung.",
        "com.sec.",
        "com.sec.android.",

        // Xiaomi / Redmi / POCO
        "com.xiaomi.",
        "com.miui.",
        "com.mi.",

        // Huawei / Honor
        "com.huawei.",
        "com.honor.",

        // OPPO
        "com.oppo.",
        "com.coloros.",
        "com.heytap.",

        // Vivo
        "com.vivo.",
        "com.bbk.",

        // Realme
        "com.realme.",

        // OnePlus
        "com.oneplus.",
        "net.oneplus.",

        // Motorola / Lenovo
        "com.motorola.",
        "com.lenovo.",

        // LG
        "com.lge.",

        // Sony
        "com.sonymobile.",
        "com.sony.",

        // Asus
        "com.asus.",

        // Nokia / HMD
        "com.hmd.",

        // Qualcomm (chipset services)
        "com.qualcomm.",
        "com.qti.",

        // MediaTek (chipset services)
        "com.mediatek.",

        // Microsoft
        "com.microsoft.",

        // Meta (Facebook)
        "com.facebook.",
        "com.instagram.android",

        // Messaging & Social (major apps)
        "com.whatsapp",
        "org.telegram.messenger",
        "jp.naver.line.android",
        "com.viber.voip",
        "com.skype.",

        // Big Tech Apps
        "com.amazon.",
        "com.netflix.",
        "com.spotify.",
        "com.twitter.",
        "com.tencent.",
        "com.snapchat.",
        "com.pinterest.",
        "com.linkedin.",
        "com.adobe.",
        "com.dropbox.",
        "com.paypal.",

        // Vietnamese popular & trusted apps
        "com.vng.",          // VNG (Zalo, ZaloPay)
        "com.vnpay.",        // VNPay
        "com.VCB.",          // Vietcombank
        "com.mbmobile.",     // MB Bank
        "vn.com.techcombank.",  // Techcombank
        "com.ftd.jvb",       // BIDV
        "com.agribank.",     // Agribank
        "vn.momo.",          // MoMo
        "com.shopee.",       // Shopee
        "com.lazada.",       // Lazada
        "vn.tiki.",          // Tiki
        "com.grab.",         // Grab
        "com.vnpt.",         // VNPT

        // TikTok / ByteDance
        "com.ss.android.",
        "com.zhiliaoapp.",

        // Apple Music on Android
        "com.apple.",
    )

    // Exact package names that are trusted even if their prefix doesn't match above
    private val trustedExactPackages = setOf(
        "com.adwatcher.app"  // Our own app
    )

    /**
     * Checks if a package name belongs to a trusted source (system, manufacturer, big corp).
     * This is the core filter that decides what the user sees vs what gets hidden.
     */
    fun isTrustedPackage(packageName: String): Boolean {
        if (trustedExactPackages.contains(packageName)) return true
        return trustedPrefixes.any { prefix -> packageName.startsWith(prefix) }
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

            // Skip trusted apps entirely unless caller explicitly wants all
            if (!includeAllApps && isTrusted) continue

            // ── Permission Analysis ──
            val permissions = getAppPermissions(packageName)
            val hasOverlay = permissions.contains("android.permission.SYSTEM_ALERT_WINDOW")
            val hasBoot = permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED")

            // ── Launcher Visibility ──
            val hasNoLauncher = packageManager.getLaunchIntentForPackage(packageName) == null

            // ── Fake System Name Detection ──
            // Detects apps that disguise themselves with names like "Google Services",
            // "System Update", "Android Settings", etc. but are NOT genuine system apps.
            val labelLower = appLabel.lowercase()
            val pkgLower = packageName.lowercase()
            val suspiciousNames = listOf(
                "google", "system", "android", "settings", "carrier", "service",
                "samsung", "update", "security", "cleaner", "booster", "optimizer",
                "battery", "wifi", "vpn free", "phone manager"
            )
            val isFakeSystem = !isTrusted && suspiciousNames.any { labelLower.contains(it) }

            // ── Install Source Detection ──
            val installSource = getInstallSource(packageName)
            val isSideloaded = !isSystem && installSource != "com.android.vending" && 
                               installSource != "com.amazon.venezia" && 
                               installSource != "com.sec.android.app.samsungapps"

            val isWhitelisted = isTrusted

            // ── Risk Score Calculation ──
            var score = 0
            val reasons = mutableListOf<String>()

            if (isFakeSystem) {
                score += 45
                reasons.add("Giả mạo hệ thống: Tên app chứa từ khóa nhạy cảm ('Google', 'System', 'Update'...) nhưng không phải app chính hãng.")
            }
            if (hasOverlay) {
                score += 30
                reasons.add("Quyền vẽ đè màn hình: Có khả năng tự động hiện popup quảng cáo full-screen đè lên ứng dụng khác.")
            }
            if (hasNoLauncher) {
                score += 25
                reasons.add("Ẩn icon: Không xuất hiện trong danh sách ứng dụng, người dùng rất khó phát hiện và gỡ cài đặt.")
            }
            if (hasBoot) {
                score += 15
                reasons.add("Tự khởi chạy: Kích hoạt ngay khi bật máy, có thể chạy dịch vụ quảng cáo ngầm liên tục.")
            }
            if (isSideloaded) {
                score += 20
                reasons.add("Nguồn không xác định: Được cài bằng file APK bên ngoài, không qua kiểm duyệt của Google Play.")
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
                    isTrusted = isTrusted
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
