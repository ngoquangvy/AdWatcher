package com.adwatcher.app.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.adwatcher.app.data.PopupLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    logs: List<PopupLog>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var selectedPkgs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var uninstallQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var isBulkUninstalling by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val uninstallLauncherHolder = remember { mutableStateOf<androidx.activity.result.ActivityResultLauncher<android.content.Intent>?>(null) }

    fun launchNextUninstall(launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>) {
        if (uninstallQueue.isEmpty()) {
            isBulkUninstalling = false
            return
        }
        val pkgName = uninstallQueue.first()
        val intent = android.content.Intent(
            android.content.Intent.ACTION_DELETE,
            "package:$pkgName".toUri()
        ).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(android.content.Intent.EXTRA_RETURN_RESULT, true)
        }
        val resolved = intent.resolveActivity(context.packageManager)
        if (resolved != null) {
            launcher.launch(intent)
        } else {
            val settingsIntent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:$pkgName".toUri()
            ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
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
        val pkgs = selectedPkgs.toList()
        if (pkgs.isEmpty()) return
        selectedPkgs = emptySet()
        uninstallQueue = pkgs
        isBulkUninstalling = true
        uninstallLauncherHolder.value?.let { launchNextUninstall(it) }
    }

    val grouped = remember(logs) {
        logs.groupBy { it.packageName }
            .mapValues { (_, entries) -> entries.sortedByDescending { it.timestamp } }
            .entries.sortedByDescending { (_, entries) -> entries.first().timestamp }
    }

    val totalPopupCount = logs.size
    val totalAppCount = grouped.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDarkBg)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = appText("Popup nghi ng?", "Suspicious popups"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = appText("$totalPopupCount popup t? $totalAppCount ?ng d?ng", "$totalPopupCount popups from $totalAppCount apps"),
                    fontSize = 13.sp,
                    color = if (logs.isNotEmpty()) StatusMediumRisk else TextSecondary
                )
            }

            if (logs.isNotEmpty()) {
                IconButton(
                    onClick = { showClearDialog = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CardDarkBg,
                        contentColor = StatusHighRisk
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, BorderDark, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = appText("X?a to?n b? nh?t k?", "Clear all logs")
                    )
                }
            }
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyLogsView()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(grouped, key = { it.key }) { (pkg, entries) ->
                    AppGroupCard(
                        packageName = pkg,
                        entries = entries,
                        isSelected = pkg in selectedPkgs,
                        onSelectedChange = { checked ->
                            selectedPkgs = if (checked) selectedPkgs + pkg else selectedPkgs - pkg
                        },
                        onClick = { selectedPkg = pkg }
                    )
                }
            }
        }

        if (selectedPkgs.isNotEmpty()) {
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
                        text = appText("${selectedPkgs.size} app ?? ch?n", "${selectedPkgs.size} apps selected"),
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
                    onClick = { selectedPkgs = emptySet() },
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
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text(if (isBulkUninstalling) appText("?ang g?...", "Uninstalling...") else appText("G? nhi?u", "Uninstall selected"))
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = CardDarkBg,
            title = {
                Text(appText("X?a to?n b? nh?t k??", "Clear all logs?"), color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(appText("T?t c? nh?t k? popup s? b? x?a v?nh vi?n v? kh?ng th? kh?i ph?c.", "All popup logs will be permanently deleted and cannot be restored."), color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = { onClearLogs(); showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusHighRisk)) {
                    Text(appText("B? ch?n", "Clear"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                    Text(appText("H?y", "Cancel"))
                }
            }
        )
    }

    selectedPkg?.let { pkg ->
        val entries = grouped.find { it.key == pkg }?.value
        if (entries != null) {
            AppDetailDialog(
                packageName = pkg,
                entries = entries,
                onDismiss = { selectedPkg = null },
                onUninstall = {
                    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun AppGroupCard(
    packageName: String,
    entries: List<PopupLog>,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appEntry = entries.first()
    val appIcon: Drawable? = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }
    }
    val lastTime = remember(appEntry.timestamp) {
        SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date(appEntry.timestamp))
    }
    val popupCount = entries.size
    val hasAttack = entries.any { it.isAttackState }
    val hasSuspicious = entries.any { it.hasSuspiciousText }
    val hasUrl = entries.any { it.containsUrl }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDarkBg)
            .border(1.dp, if (hasAttack) StatusHighRisk else BorderDark, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectedChange,
            modifier = Modifier.padding(end = 8.dp)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                        contentDescription = appEntry.appName,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (popupCount == 1) appEntry.appName else "${popupCount}x ${appEntry.appName}",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (hasAttack) StatusHighRisk else StatusMediumRisk)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = packageName,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = appText("L?n cu?i: $lastTime", "Last seen: $lastTime"),
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        if (popupCount > 1) {
                            Badge(text = appText("Link l?", "Odd link"), color = StatusMediumRisk)
                        }
                        if (hasAttack) Badge(text = appText("T?N C?NG", "ATTACK"), color = StatusHighRisk)
                        if (hasSuspicious) Badge(text = appText("T?N C?NG", "ATTACK"), color = StatusHighRisk)
                        if (hasUrl) Badge(text = appText("Link l?", "Odd link"), color = StatusMediumRisk)
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppDetailDialog(
    packageName: String,
    entries: List<PopupLog>,
    onDismiss: () -> Unit,
    onUninstall: () -> Unit
) {
    val context = LocalContext.current
    val appEntry = entries.first()
    val appIcon: Drawable? = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardDarkBg)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepDarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.toBitmap().asImageBitmap(),
                            contentDescription = appEntry.appName,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = appEntry.appName,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = packageName,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = appText("${entries.size} popup ???c ghi nh?n", "${entries.size} popups recorded"),
                        color = StatusMediumRisk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderDark)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = appText("L?ch s? popup", "Popup history"),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { log ->
                    DetailLogRow(log)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderDark)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(BorderDark)
                    )
                ) {
                    Text(appText("??ng", "Close"))
                }
                Button(
                    onClick = onUninstall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusHighRisk)
                ) {
                    Text(appText("G? c?i ??t", "Uninstall"))
                }
            }
        }
    }
    }
}

@Composable
private fun DetailLogRow(log: PopupLog) {
    val timeFormatted = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss - dd/MM", Locale.getDefault()).format(Date(log.timestamp))
    }
    val detectionLabel = remember(log.detectionMethod) {
        when (log.detectionMethod) {
            "WINDOW_STATE_CHANGED" -> "State change"
            "WINDOWS_CHANGED" -> "Windows change"
            "OVERLAY_APPLICATION" -> "Overlay app"
            "OVERLAY_LEGACY_ALERT" -> "Alert overlay"
            "OVERLAY_LEGACY_OVERLAY" -> "System overlay"
            "OVERLAY_UNKNOWN" -> "Overlay ?"
            "BROWSER_HANDOFF" -> "Browser handoff"
            else -> log.detectionMethod ?: "Auto"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DeepDarkBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeFormatted,
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Badge(text = log.eventType, color = when {
                    log.eventType == "ATTACK" || log.eventType == "HIGH_FREQ" -> StatusHighRisk
                    log.eventType == "OVERLAY_FULL" -> StatusMediumRisk
                    else -> TextSecondary
                })
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                Badge(text = detectionLabel, color = NeonPurple)
                if (log.isQuickPopup) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(text = appText("Popup nhanh", "Quick popup"), color = StatusMediumRisk)
                }
                if (log.isBrowserHandoff) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(text = appText("Qua trinh duyet", "Browser handoff"), color = StatusMediumRisk)
                }
                log.attributionConfidence?.let { confidence ->
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(text = confidence, color = if (confidence == "HIGH") StatusHighRisk else TextSecondary)
                }
                if (log.hasSuspiciousText) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(text = appText("T?N C?NG", "ATTACK"), color = StatusHighRisk)
                }
                if (log.containsUrl) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(text = appText("Link l?", "Odd link"), color = StatusMediumRisk)
                }
            }
            if (log.suspectedSourcePackage != null || log.triggerPackage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = appText(
                        "Nguon nghi ngo: ${log.suspectedSourcePackage ?: log.packageName} -> cua so: ${log.triggerPackage ?: log.packageName}",
                        "Suspected source: ${log.suspectedSourcePackage ?: log.packageName} -> window: ${log.triggerPackage ?: log.packageName}"
                    ),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!log.popupText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.popupText,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmptyLogsView() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardDarkBg)
                .border(1.dp, BorderDark, RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DeepDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = appText("?ang qu?t", "Scanning"),
                    tint = NeonPurple,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = appText("?ang theo d?i popup l?", "Monitoring unknown popups"),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appText("H? th?ng ?ang gi?m s?t n?n. Popup t? app h? th?ng, Google, Samsung, Microsoft v? c?c h?ng l?n s? ???c b? qua. Ch? popup t? app l? m?i b? ghi l?i.", "Background monitoring is active. Popups from system apps, Google, Samsung, Microsoft, and major trusted vendors are ignored. Only unknown-app popups are recorded."),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
