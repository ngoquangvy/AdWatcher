package com.adwatcher.app.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.net.toUri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.adwatcher.app.analyzer.AppRiskInfo
import com.adwatcher.app.analyzer.RiskLevel

/**
 * Scanner screen that ONLY displays suspicious apps.
 * System apps, Google apps, manufacturer apps, and big tech apps are
 * already filtered out by AppAnalyzer — this screen only receives
 * apps that need the user's attention.
 */
@Composable
fun ScannerScreen(
    appsList: List<AppRiskInfo>,
    isScanning: Boolean,
    onRefreshScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAppForDetail by remember { mutableStateOf<AppRiskInfo?>(null) }
    var selectedPackageNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var uninstallQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var isBulkUninstalling by remember { mutableStateOf(false) }

    val highRiskCount = remember(appsList) { appsList.count { it.riskLevel == RiskLevel.HIGH } }
    val mediumRiskCount = remember(appsList) { appsList.count { it.riskLevel == RiskLevel.MEDIUM } }

    val context = LocalContext.current
    val selectedApps = remember(appsList, selectedPackageNames) {
        appsList.filter { it.packageName in selectedPackageNames }
    }

    val uninstallLauncherHolder = remember { mutableStateOf<androidx.activity.result.ActivityResultLauncher<android.content.Intent>?>(null) }

    fun launchNextUninstall(launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>) {
        if (uninstallQueue.isEmpty()) {
            isBulkUninstalling = false
            return
        }

        val packageName = uninstallQueue.first()
        val uninstallIntent = android.content.Intent(
            android.content.Intent.ACTION_DELETE,
            "package:$packageName".toUri()
        ).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(android.content.Intent.EXTRA_RETURN_RESULT, true)
        }

        val resolved = uninstallIntent.resolveActivity(context.packageManager)
        if (resolved != null) {
            launcher.launch(uninstallIntent)
        } else {
            val settingsIntent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:$packageName".toUri()
            ).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
            uninstallQueue = uninstallQueue.drop(1)
            launchNextUninstall(launcher)
        }
    }

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (uninstallQueue.size <= 1) {
            uninstallQueue = emptyList()
            isBulkUninstalling = false
        } else {
            uninstallQueue = uninstallQueue.drop(1)
            uninstallLauncherHolder.value?.let { launchNextUninstall(it) }
        }
    }
    uninstallLauncherHolder.value = uninstallLauncher

    fun startBulkUninstall() {
        val uninstallable = selectedApps.filter { it.canUninstall }.map { it.packageName }
        if (uninstallable.isEmpty()) return

        selectedPackageNames = emptySet()
        uninstallQueue = uninstallable
        isBulkUninstalling = true
        uninstallLauncherHolder.value?.let { launchNextUninstall(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDarkBg)
            .padding(horizontal = 16.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = appText("Qu?t ?ng d?ng", "App scan"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = appText("${appsList.size} app nghi ng? ???c ph?t hi?n", "${appsList.size} suspicious apps found"),
                    fontSize = 13.sp,
                    color = if (appsList.isNotEmpty()) StatusMediumRisk else StatusLowRisk
                )
            }

            Button(
                onClick = onRefreshScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(if (isScanning) appText("?ang qu?t...", "Scanning...") else appText("Qu?t l?i", "Scan again"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Risk Summary Dashboard Card
        RiskSummaryCard(highRiskCount, mediumRiskCount)

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedPackageNames.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeepDarkBg)
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appText("${selectedPackageNames.size} app ?? ch?n", "${selectedPackageNames.size} apps selected"),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = appText("H? th?ng s? y?u c?u x?c nh?n g? c?i ??t t?ng app.", "Android will ask you to confirm each uninstall."),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                TextButton(
                    onClick = { selectedPackageNames = emptySet() },
                    colors = ButtonDefaults.textButtonColors(contentColor = ElectricCyan)
                ) {
                    Text(appText("B? ch?n", "Clear"))
                }

                Button(
                    onClick = { startBulkUninstall() },
                    enabled = !isBulkUninstalling,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBulkUninstalling) StatusMediumRisk.copy(alpha = 0.5f) else StatusHighRisk,
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isBulkUninstalling) appText("?ang g?...", "Uninstalling...") else appText("G? nhi?u", "Uninstall selected"))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonPurple)
            }
        } else if (appsList.isEmpty()) {
            // Clean device state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(StatusLowRisk.copy(alpha = 0.05f))
                        .border(1.dp, StatusLowRisk.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(32.dp)
                ) {
                    Text(
                        text = appText("Thi?t b? s?ch", "Device is clean"),
                        color = StatusLowRisk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = appText("Kh?ng ph?t hi?n ?ng d?ng nghi ng? n?o. T?t c? ?ng d?ng tr?n m?y ??u thu?c h? th?ng ho?c t? ngu?n uy t?n.", "No suspicious apps found. Installed apps appear to be system apps or from trusted sources."),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(appsList, key = { it.packageName }) { appInfo ->
                    AppRiskCard(
                        appInfo = appInfo,
                        isSelected = selectedPackageNames.contains(appInfo.packageName),
                        onSelectedChange = { checked ->
                            selectedPackageNames = if (checked) {
                                selectedPackageNames + appInfo.packageName
                            } else {
                                selectedPackageNames - appInfo.packageName
                            }
                        },
                        onClick = { selectedAppForDetail = appInfo }
                    )
                }
            }
        }
    }

    // Interactive App Detail Dialog
    selectedAppForDetail?.let { app ->
        AppDetailDialog(appInfo = app, onDismiss = { selectedAppForDetail = null })
    }
}

@Composable
fun RiskSummaryCard(highCount: Int, mediumCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardDarkBg)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appText("M?c ?? an to?n", "Safety level"),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    highCount > 0 -> appText("Nguy hi?m", "Danger")
                    mediumCount > 0 -> appText("C?n ki?m tra", "Needs review")
                    else -> appText("An to?n", "Safe")
                },
                color = when {
                    highCount > 0 -> StatusHighRisk
                    mediumCount > 0 -> StatusMediumRisk
                    else -> StatusLowRisk
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RiskStatBadge(appText("C?nh b?o", "Alerts"), highCount, StatusHighRisk)
            RiskStatBadge(appText("Nghi ng?", "Suspicious"), mediumCount, StatusMediumRisk)
        }
    }
}

@Composable
fun RiskStatBadge(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DeepDarkBg)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = count.toString(),
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
fun AppRiskCard(
    appInfo: AppRiskInfo,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appIcon: Drawable? = remember(appInfo.packageName) {
        try {
            context.packageManager.getApplicationIcon(appInfo.packageName)
        } catch (_: Exception) {
            null
        }
    }

    val indicatorColor = when (appInfo.riskLevel) {
        RiskLevel.HIGH -> StatusHighRisk
        RiskLevel.MEDIUM -> StatusMediumRisk
        RiskLevel.LOW -> StatusLowRisk
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDarkBg)
            .border(
                1.dp, 
                if (appInfo.riskLevel == RiskLevel.HIGH) StatusHighRisk.copy(alpha = 0.3f) else BorderDark, 
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = if (appInfo.canUninstall) onSelectedChange else null,
            enabled = appInfo.canUninstall,
            modifier = Modifier.padding(end = 8.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeepDarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.toBitmap().asImageBitmap(),
                            contentDescription = appInfo.appName,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "App Icon",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = appInfo.packageName,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (appInfo.isFakeSystemApp) {
                            StatusBadge(appText("C?nh b?o", "Warning"), StatusHighRisk)
                        }
                        if (appInfo.isSideloaded) {
                            StatusBadge(appText("Nghi ng?", "Suspicious"), StatusMediumRisk)
                        }
                        if (appInfo.hasNoLauncher) {
                            StatusBadge(appText("C?nh b?o", "Warning"), StatusHighRisk)
                        }
                        if (appInfo.hasOverlayPermission) {
                            StatusBadge(appText("Nghi ng?", "Suspicious"), StatusMediumRisk)
                        }
                        if (appInfo.hasAccessibilityPermission) {
                            StatusBadge(appText("C?nh b?o", "Warning"), StatusHighRisk)
                        }
                        if (appInfo.hasInstallPermission) {
                            StatusBadge(appText("C?nh b?o", "Warning"), StatusHighRisk)
                        }
                        if (appInfo.hasSmsPermission) {
                            StatusBadge(appText("Nghi ng?", "Suspicious"), StatusMediumRisk)
                        }
                        if (appInfo.hasNotificationListener) {
                            StatusBadge(appText("Nghi ng?", "Suspicious"), StatusMediumRisk)
                        }
                        if (appInfo.hasAdSuspiciousPackage) {
                            StatusBadge(appText("Nghi ng?", "Suspicious"), StatusMediumRisk)
                        }
                        if (appInfo.isActiveAdmin) {
                            StatusBadge(appText("C?nh b?o", "Warning"), StatusHighRisk)
                        } else if (appInfo.hasDeviceAdminPermission) {
                            StatusBadge(appText("Nghi ng?", "Suspicious"), StatusMediumRisk)
                        }
                        if (appInfo.hasUsageStatsPermission) {
                            StatusBadge(appText("Nghi ng?", "Suspicious"), StatusMediumRisk)
                        }
                        if (appInfo.isRecentlyInstalled) {
                            StatusBadge(appText("Nghi ng?", "Suspicious"), StatusMediumRisk)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(indicatorColor.copy(alpha = 0.15f))
                .border(1.dp, indicatorColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = appText("?i?m r?i ro: ${appInfo.riskScore}%", "Risk score: ${appInfo.riskScore}%"),
                color = indicatorColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppDetailDialog(appInfo: AppRiskInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val appIcon: Drawable? = remember(appInfo.packageName) {
        try {
            context.packageManager.getApplicationIcon(appInfo.packageName)
        } catch (_: Exception) {
            null
        }
    }
    val canUninstall = !appInfo.isSystemApp

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDarkBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DeepDarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.toBitmap().asImageBitmap(),
                            contentDescription = appInfo.appName,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = appInfo.appName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = appText("?i?m r?i ro: ${appInfo.riskScore}%", "Risk score: ${appInfo.riskScore}%"),
                        fontSize = 12.sp,
                        color = when (appInfo.riskLevel) {
                            RiskLevel.HIGH -> StatusHighRisk
                            RiskLevel.MEDIUM -> StatusMediumRisk
                            RiskLevel.LOW -> StatusLowRisk
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(bottom = 12.dp))

                // Package name
                Text(appText("T?n package:", "Package name:"), fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Text(
                    text = appInfo.packageName,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Install source info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeepDarkBg)
                        .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val installSourceLabel = when (appInfo.installSource) {
                        "com.android.vending" -> "Google Play Store"
                        "com.sec.android.app.samsungapps" -> "Samsung Galaxy Store"
                        "com.amazon.venezia" -> "Amazon Appstore"
                        "com.samsung.android.app.omcagent" -> "Samsung Installer"
                        null -> appText("Kh?ng x?c ??nh (APK ngo?i)", "Unknown (sideloaded)")
                        else -> appInfo.installSource
                    }
                    val installSourceColor = when (appInfo.installSource) {
                        "com.android.vending",
                        "com.sec.android.app.samsungapps",
                        "com.amazon.venezia",
                        "com.samsung.android.app.omcagent" -> StatusLowRisk
                        else -> StatusMediumRisk
                    }
                    val isTrustedInstallSource = when (appInfo.installSource) {
                        "com.android.vending",
                        "com.sec.android.app.samsungapps",
                        "com.amazon.venezia",
                        "com.samsung.android.app.omcagent" -> true
                        else -> false
                    }
                    val sourceBadgeColor = if (isTrustedInstallSource) StatusLowRisk else StatusHighRisk

                    Column(modifier = Modifier.weight(1f)) {
                        Text(appText("Ngu?n c?i ??t:", "Install source:"), fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = installSourceLabel,
                            fontSize = 13.sp,
                            color = installSourceColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(sourceBadgeColor.copy(alpha = 0.15f))
                            .border(1.dp, sourceBadgeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isTrustedInstallSource) {
                                appText("Ngu?n tin c?y", "Trusted source")
                            } else {
                                appText("Ngu?n l?", "Unknown source")
                            },
                            color = sourceBadgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ── Permission Detail Section ──
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = appText(
                        "Quyền nguy hiểm (${
                            listOf(
                                appInfo.hasOverlayPermission,
                                appInfo.hasAccessibilityPermission,
                                appInfo.hasInstallPermission,
                                appInfo.hasBootPermission,
                                appInfo.hasSmsPermission,
                                appInfo.hasNotificationListener,
                                appInfo.hasQueryAllPackages,
                                appInfo.hasDeviceAdminPermission,
                                appInfo.hasUsageStatsPermission
                            ).count { it }
                        } phát hiện):",
                        "Dangerous permissions (${
                            listOf(
                                appInfo.hasOverlayPermission,
                                appInfo.hasAccessibilityPermission,
                                appInfo.hasInstallPermission,
                                appInfo.hasBootPermission,
                                appInfo.hasSmsPermission,
                                appInfo.hasNotificationListener,
                                appInfo.hasQueryAllPackages,
                                appInfo.hasDeviceAdminPermission,
                                appInfo.hasUsageStatsPermission
                            ).count { it }
                        } found):"
                    ),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val permItems = mutableListOf<Pair<String, Boolean>>()
                permItems.add(appText("V? ?? (SYSTEM_ALERT_WINDOW)", "Overlay (SYSTEM_ALERT_WINDOW)") to appInfo.hasOverlayPermission)
                permItems.add(appText("Tr? n?ng (BIND_ACCESSIBILITY_SERVICE)", "Accessibility (BIND_ACCESSIBILITY_SERVICE)") to appInfo.hasAccessibilityPermission)
                permItems.add(appText("C?i ??t APK (REQUEST_INSTALL_PACKAGES)", "APK install (REQUEST_INSTALL_PACKAGES)") to appInfo.hasInstallPermission)
                permItems.add(appText("T? kh?i ch?y (RECEIVE_BOOT_COMPLETED)", "Auto-start (RECEIVE_BOOT_COMPLETED)") to appInfo.hasBootPermission)
                permItems.add(appText("??c SMS (READ_SMS)", "Read SMS (READ_SMS)") to appInfo.hasSmsPermission)
                permItems.add(appText("??c th?ng b?o (BIND_NOTIFICATION_LISTENER)", "Notification access (BIND_NOTIFICATION_LISTENER)") to appInfo.hasNotificationListener)
                permItems.add(appText("Xem danh s?ch app (QUERY_ALL_PACKAGES)", "App list access (QUERY_ALL_PACKAGES)") to appInfo.hasQueryAllPackages)
                permItems.add(appText("Qu?n tr? thi?t b? (BIND_DEVICE_ADMIN)", "Device admin (BIND_DEVICE_ADMIN)") to appInfo.hasDeviceAdminPermission)
                permItems.add(appText("Truy c?p d? li?u s? d?ng (PACKAGE_USAGE_STATS)", "Usage access (PACKAGE_USAGE_STATS)") to appInfo.hasUsageStatsPermission)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    permItems.forEach { (name, enabled) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (enabled) StatusHighRisk.copy(alpha = 0.08f) else DeepDarkBg)
                                .border(
                                    1.dp,
                                    if (enabled) StatusHighRisk.copy(alpha = 0.2f) else BorderDark,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (enabled) StatusHighRisk else TextSecondary.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = name,
                                color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (appInfo.reasons.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusMediumRisk.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StatusMediumRisk)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = appText("?ng d?ng n?y kh?ng thu?c h? th?ng hay nh? s?n xu?t nh?ng ch?a ph?t hi?n h?nh vi ??ng ng? c? th?.", "This app is not from the system or manufacturer, but no specific suspicious behavior was found."),
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text(
                        text = appText("D?u hi?u nghi ng? ph?t hi?n:", "Suspicious signs found:"),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        appInfo.reasons.forEach { reason ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DeepDarkBg)
                                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = appText("C?nh b?o", "Warning"),
                                    tint = StatusHighRisk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = appRiskReasonText(reason),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = ElectricCyan)
            ) {
                Text(appText("??ng", "Close"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    if (canUninstall) {
                        try {
                            val uninstallIntent = android.content.Intent(
                                android.content.Intent.ACTION_DELETE,
                                "package:${appInfo.packageName}".toUri()
                            ).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra(android.content.Intent.EXTRA_RETURN_RESULT, true)
                            }

                            val resolved = uninstallIntent.resolveActivity(context.packageManager)
                            if (resolved != null) {
                                context.startActivity(uninstallIntent)
                            } else {
                                val settingsIntent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    "package:${appInfo.packageName}".toUri()
                                ).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(settingsIntent)
                            }
                            onDismiss()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                enabled = canUninstall,
                colors = ButtonDefaults.buttonColors(containerColor = StatusHighRisk),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (canUninstall) appText("G? c?i ??t", "Uninstall") else appText("Kh?ng th? g?", "Cannot uninstall"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
