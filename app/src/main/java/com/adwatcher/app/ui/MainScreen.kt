package com.adwatcher.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adwatcher.app.analyzer.AppRiskInfo
import com.adwatcher.app.data.PopupLog

sealed class ScreenTab {
    object Logs : ScreenTab()
    object Scanner : ScreenTab()
    object Guide : ScreenTab()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    logs: List<PopupLog>,
    onClearLogs: () -> Unit,
    appsList: List<AppRiskInfo>,
    isScanning: Boolean,
    onRefreshScan: () -> Unit,
    isAccessibilityEnabled: Boolean,
    onRequestAccessibilityPermission: () -> Unit
) {
    var activeTab by remember { mutableStateOf<ScreenTab>(ScreenTab.Logs) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "ADWATCHER SECURE",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DeepDarkBg
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CardDarkBg,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                NavigationBarItem(
                    selected = activeTab == ScreenTab.Logs,
                    onClick = { activeTab = ScreenTab.Logs },
                    icon = { @Suppress("DEPRECATION") Icon(Icons.Filled.List, contentDescription = "Logs") },
                    label = { Text("Lịch sử", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = ElectricCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = NeonPurple.copy(alpha = 0.2f)
                    )
                )

                NavigationBarItem(
                    selected = activeTab == ScreenTab.Scanner,
                    onClick = { activeTab = ScreenTab.Scanner },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Scanner") },
                    label = { Text("Quét App", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = ElectricCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = NeonPurple.copy(alpha = 0.2f)
                    )
                )

                NavigationBarItem(
                    selected = activeTab == ScreenTab.Guide,
                    onClick = { activeTab = ScreenTab.Guide },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Permission Guide") },
                    label = { Text("Cấu hình", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = ElectricCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = NeonPurple.copy(alpha = 0.2f)
                    )
                )
            }
        },
        containerColor = DeepDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Smoothly animated warning banner for Accessibility Service
            AnimatedVisibility(
                visible = !isAccessibilityEnabled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                PermissionWarningBanner(
                    onClickActivate = onRequestAccessibilityPermission,
                    onClickGuideTab = { activeTab = ScreenTab.Guide }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeTab) {
                    ScreenTab.Logs -> LogsScreen(
                        logs = logs,
                        onClearLogs = onClearLogs
                    )
                    ScreenTab.Scanner -> ScannerScreen(
                        appsList = appsList,
                        isScanning = isScanning,
                        onRefreshScan = onRefreshScan
                    )
                    ScreenTab.Guide -> ConfigurationScreen(
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        onRequestAccessibility = onRequestAccessibilityPermission
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionWarningBanner(
    onClickActivate: () -> Unit,
    onClickGuideTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StatusHighRisk.copy(alpha = 0.12f))
            .border(1.dp, StatusHighRisk.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Cảnh báo quyền",
            tint = StatusHighRisk,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Cần Kích Hoạt Dịch Vụ",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Bật Hỗ trợ tiếp cận để phát hiện quảng cáo ẩn danh.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Button(
                onClick = onClickActivate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusHighRisk,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("BẬT", fontSize = 12.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(onClick = onClickGuideTab) {
                Text("Xem hướng dẫn", color = ElectricCyan, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ConfigurationScreen(
    isAccessibilityEnabled: Boolean,
    onRequestAccessibility: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDarkBg)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Cấu Hình Hệ Thống",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Permission Card State
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    1.dp, 
                    if (isAccessibilityEnabled) StatusLowRisk.copy(alpha = 0.3f) else BorderDark, 
                    RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = CardDarkBg)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Quyền Hỗ Trợ Tiếp Cận",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isAccessibilityEnabled) StatusLowRisk.copy(alpha = 0.15f) 
                                else StatusHighRisk.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isAccessibilityEnabled) "ĐÃ BẬT" else "CHƯA BẬT",
                            color = if (isAccessibilityEnabled) StatusLowRisk else StatusHighRisk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Quyền này cho phép AdWatcher giám sát sự xuất hiện của các cửa sổ pop-up lạ trong nền và chỉ ra thủ phạm.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                if (!isAccessibilityEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRequestAccessibility,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Đi tới cài đặt", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Step by step guidelines if not enabled
        if (!isAccessibilityEnabled) {
            Text(
                text = "Hướng dẫn từng bước kích hoạt:",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val steps = listOf(
                "Nhấp nút \"Đi tới cài đặt\" bên trên.",
                "Tìm mục \"Ứng dụng đã tải xuống\" (Downloaded Apps) hoặc \"Dịch vụ đã cài đặt\" (Installed Services).",
                "Chọn \"AdWatcher\" từ danh sách.",
                "Bật công tắc dịch vụ và chọn \"Cho phép\" (Allow) để hoàn tất."
            )

            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDarkBg)
                        .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(NeonPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = step,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // Running State
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StatusLowRisk.copy(alpha = 0.05f))
                    .border(1.dp, StatusLowRisk.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "Hệ thống bảo mật đang kích hoạt",
                    color = StatusLowRisk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Điện thoại của bạn đang được AdWatcher bảo vệ an toàn 24/7 khỏi các loại mã độc quảng cáo tự động hiện.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
