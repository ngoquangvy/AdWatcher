package com.adwatcher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale

@Composable
fun appText(vi: String, en: String): String {
    return if (Locale.current.language.equals("vi", ignoreCase = true)) {
        vietnameseText(en, vi)
    } else {
        en
    }
}

fun appTextForSystem(vi: String, en: String): String {
    return if (java.util.Locale.getDefault().language.equals("vi", ignoreCase = true)) {
        vietnameseText(en, vi)
    } else {
        en
    }
}

@Composable
fun appRiskReasonText(reason: String): String {
    return if (Locale.current.language.equals("vi", ignoreCase = true)) {
        reason
    } else {
        englishRiskReason(reason)
    }
}

private fun vietnameseText(en: String, fallbackVi: String): String {
    dynamicVietnameseText(en)?.let { return it }
    return viFallbacks[en] ?: fallbackVi
}

private fun dynamicVietnameseText(en: String): String? {
    Regex("^(\\d+) popups from (\\d+) apps$").matchEntire(en)?.let {
        return "${it.groupValues[1]} popup t\u1eeb ${it.groupValues[2]} \u1ee9ng d\u1ee5ng"
    }
    Regex("^(\\d+) apps selected$").matchEntire(en)?.let {
        return "${it.groupValues[1]} app \u0111\u00e3 ch\u1ecdn"
    }
    Regex("^(\\d+) suspicious apps found$").matchEntire(en)?.let {
        return "${it.groupValues[1]} app nghi ng\u1edd \u0111\u01b0\u1ee3c ph\u00e1t hi\u1ec7n"
    }
    Regex("^(\\d+) popups recorded$").matchEntire(en)?.let {
        return "${it.groupValues[1]} popup \u0111\u01b0\u1ee3c ghi nh\u1eadn"
    }
    Regex("^Risk score: (\\d+)%$").matchEntire(en)?.let {
        return "\u0110i\u1ec3m r\u1ee7i ro: ${it.groupValues[1]}%"
    }
    Regex("^Last seen: (.+)$").matchEntire(en)?.let {
        return "L\u1ea7n cu\u1ed1i: ${it.groupValues[1]}"
    }
    Regex("^Dangerous permissions \\((\\d+) found\\):$").matchEntire(en)?.let {
        return "Quy\u1ec1n nguy hi\u1ec3m (${it.groupValues[1]} ph\u00e1t hi\u1ec7n):"
    }
    Regex("^Suspected source: (.+) -> window: (.+)$").matchEntire(en)?.let {
        return "Ngu\u1ed3n nghi ng\u1edd: ${it.groupValues[1]} -> c\u1eeda s\u1ed5: ${it.groupValues[2]}"
    }
    return null
}

private val viFallbacks = mapOf(
    "Logs" to "Nh\u1eadt k\u00fd",
    "Scan" to "Qu\u00e9t app",
    "Settings" to "C\u1ea5u h\u00ecnh",
    "Permission warning" to "C\u1ea3nh b\u00e1o quy\u1ec1n",
    "Protection required" to "C\u1ea7n b\u1eadt b\u1ea3o v\u1ec7",
    "Enable Accessibility to detect and close unknown popups." to "B\u1eadt Tr\u1ee3 n\u0103ng \u0111\u1ec3 ph\u00e1t hi\u1ec7n v\u00e0 \u0111\u00f3ng popup l\u1ea1.",
    "ON" to "B\u1eacT",
    "OFF" to "CH\u01afA B\u1eacT",
    "Guide" to "H\u01b0\u1edbng d\u1eabn",
    "System settings" to "C\u1ea5u h\u00ecnh h\u1ec7 th\u1ed1ng",
    "Accessibility permission" to "Quy\u1ec1n Tr\u1ee3 n\u0103ng",
    "This permission lets AdWatcher monitor unknown popups and help close screen-blocking ads." to "Quy\u1ec1n n\u00e0y cho ph\u00e9p AdWatcher gi\u00e1m s\u00e1t popup l\u1ea1 v\u00e0 gi\u00fap \u0111\u00f3ng qu\u1ea3ng c\u00e1o che m\u00e0n h\u00ecnh.",
    "Open settings" to "M\u1edf c\u00e0i \u0111\u1eb7t",
    "Enable protection steps:" to "C\u00e1c b\u01b0\u1edbc b\u1eadt b\u1ea3o v\u1ec7:",
    "Tap the \"Open settings\" button above." to "Nh\u1ea5n n\u00fat \"M\u1edf c\u00e0i \u0111\u1eb7t\" \u1edf tr\u00ean.",
    "Find Downloaded apps or Installed services." to "T\u00ecm \"\u1ee8ng d\u1ee5ng \u0111\u00e3 t\u1ea3i xu\u1ed1ng\" ho\u1eb7c \"D\u1ecbch v\u1ee5 \u0111\u00e3 c\u00e0i \u0111\u1eb7t\".",
    "Select AdWatcher from the list." to "Ch\u1ecdn AdWatcher trong danh s\u00e1ch.",
    "Turn the service on and choose Allow." to "B\u1eadt d\u1ecbch v\u1ee5 v\u00e0 ch\u1ecdn Cho ph\u00e9p.",
    "Protection is active" to "B\u1ea3o v\u1ec7 \u0111ang b\u1eadt",
    "AdWatcher is monitoring unknown popups and will try to close screen-blocking ads during an attack." to "AdWatcher \u0111ang theo d\u00f5i popup l\u1ea1 v\u00e0 s\u1ebd c\u1ed1 g\u1eafng \u0111\u00f3ng qu\u1ea3ng c\u00e1o che m\u00e0n h\u00ecnh khi ph\u00e1t hi\u1ec7n t\u1ea5n c\u00f4ng.",
    "Suspicious popups" to "Popup nghi ng\u1edd",
    "Clear all logs" to "X\u00f3a to\u00e0n b\u1ed9 nh\u1eadt k\u00fd",
    "Android will ask you to confirm each uninstall." to "H\u1ec7 th\u1ed1ng s\u1ebd y\u00eau c\u1ea7u x\u00e1c nh\u1eadn g\u1ee1 c\u00e0i \u0111\u1eb7t t\u1eebng app.",
    "Clear" to "B\u1ecf ch\u1ecdn",
    "Uninstalling..." to "\u0110ang g\u1ee1...",
    "Uninstall selected" to "G\u1ee1 c\u00e0i \u0111\u1eb7t",
    "Clear all logs?" to "X\u00f3a to\u00e0n b\u1ed9 nh\u1eadt k\u00fd?",
    "All popup logs will be permanently deleted and cannot be restored." to "T\u1ea5t c\u1ea3 nh\u1eadt k\u00fd popup s\u1ebd b\u1ecb x\u00f3a v\u0129nh vi\u1ec5n v\u00e0 kh\u00f4ng th\u1ec3 kh\u00f4i ph\u1ee5c.",
    "Cancel" to "H\u1ee7y",
    "Last seen" to "L\u1ea7n cu\u1ed1i",
    "ATTACK" to "T\u1ea4N C\u00d4NG",
    "Odd text" to "Text l\u1ea1",
    "Odd link" to "Link l\u1ea1",
    "Popup history" to "L\u1ecbch s\u1eed popup",
    "Close" to "\u0110\u00f3ng",
    "Uninstall" to "G\u1ee1 c\u00e0i \u0111\u1eb7t",
    "Scanning" to "\u0110ang qu\u00e9t",
    "Monitoring unknown popups" to "\u0110ang theo d\u00f5i popup l\u1ea1",
    "Background monitoring is active. Popups from system apps, Google, Samsung, Microsoft, and major trusted vendors are ignored. Only unknown-app popups are recorded." to "H\u1ec7 th\u1ed1ng \u0111ang gi\u00e1m s\u00e1t n\u1ec1n. Popup t\u1eeb app h\u1ec7 th\u1ed1ng, Google, Samsung, Microsoft v\u00e0 c\u00e1c h\u00e3ng l\u1edbn s\u1ebd \u0111\u01b0\u1ee3c b\u1ecf qua. Ch\u1ec9 popup t\u1eeb app l\u1ea1 m\u1edbi b\u1ecb ghi l\u1ea1i.",
    "App scan" to "Qu\u00e9t \u1ee9ng d\u1ee5ng",
    "Scanning..." to "\u0110ang qu\u00e9t...",
    "Scan again" to "Qu\u00e9t l\u1ea1i",
    "Device is clean" to "Thi\u1ebft b\u1ecb s\u1ea1ch",
    "No suspicious apps found. Installed apps appear to be system apps or from trusted sources." to "Kh\u00f4ng ph\u00e1t hi\u1ec7n \u1ee9ng d\u1ee5ng nghi ng\u1edd n\u00e0o. T\u1ea5t c\u1ea3 \u1ee9ng d\u1ee5ng tr\u00ean m\u00e1y \u0111\u1ec1u thu\u1ed9c h\u1ec7 th\u1ed1ng ho\u1eb7c t\u1eeb ngu\u1ed3n uy t\u00edn.",
    "Safety level" to "M\u1ee9c \u0111\u1ed9 an to\u00e0n",
    "Danger" to "Nguy hi\u1ec3m",
    "Needs review" to "C\u1ea7n ki\u1ec3m tra",
    "Safe" to "An to\u00e0n",
    "Alerts" to "C\u1ea3nh b\u00e1o",
    "Suspicious" to "Nghi ng\u1edd",
    "Warning" to "C\u1ea3nh b\u00e1o",
    "Package name:" to "T\u00ean package:",
    "Unknown (sideloaded)" to "Kh\u00f4ng x\u00e1c \u0111\u1ecbnh (APK ngo\u00e0i)",
    "Install source:" to "Ngu\u1ed3n c\u00e0i \u0111\u1eb7t:",
    "Trusted source" to "Ngu\u1ed3n tin c\u1eady",
    "Unknown source" to "Ngu\u1ed3n l\u1ea1",
    "Quick popup" to "Popup nhanh",
    "Browser handoff" to "Qua tr\u00ecnh duy\u1ec7t",
    "Fake system" to "Gi\u1ea3 h\u1ec7 th\u1ed1ng",
    "No icon" to "Kh\u00f4ng icon",
    "Overlay" to "V\u1ebd \u0111\u00e8",
    "Accessibility" to "Tr\u1ee3 n\u0103ng",
    "APK install" to "C\u00e0i APK",
    "SMS" to "SMS",
    "Notifications" to "Th\u00f4ng b\u00e1o",
    "Odd package" to "Package l\u1ea1",
    "Active admin" to "Admin b\u1eadt",
    "Device admin" to "Admin",
    "Usage access" to "Theo d\u00f5i app",
    "Recent" to "M\u1edbi c\u00e0i",
    "Suspicious signs found:" to "D\u1ea5u hi\u1ec7u nghi ng\u1edd ph\u00e1t hi\u1ec7n:",
    "Overlay (SYSTEM_ALERT_WINDOW)" to "V\u1ebd \u0111\u00e8 (SYSTEM_ALERT_WINDOW)",
    "Accessibility (BIND_ACCESSIBILITY_SERVICE)" to "Tr\u1ee3 n\u0103ng (BIND_ACCESSIBILITY_SERVICE)",
    "APK install (REQUEST_INSTALL_PACKAGES)" to "C\u00e0i \u0111\u1eb7t APK (REQUEST_INSTALL_PACKAGES)",
    "Auto-start (RECEIVE_BOOT_COMPLETED)" to "T\u1ef1 kh\u1edfi ch\u1ea1y (RECEIVE_BOOT_COMPLETED)",
    "Read SMS (READ_SMS)" to "\u0110\u1ecdc SMS (READ_SMS)",
    "Notification access (BIND_NOTIFICATION_LISTENER)" to "\u0110\u1ecdc th\u00f4ng b\u00e1o (BIND_NOTIFICATION_LISTENER)",
    "App list access (QUERY_ALL_PACKAGES)" to "Xem danh s\u00e1ch app (QUERY_ALL_PACKAGES)",
    "Device admin (BIND_DEVICE_ADMIN)" to "Qu\u1ea3n tr\u1ecb thi\u1ebft b\u1ecb (BIND_DEVICE_ADMIN)",
    "Usage access (PACKAGE_USAGE_STATS)" to "Truy c\u1eadp d\u1eef li\u1ec7u s\u1eed d\u1ee5ng (PACKAGE_USAGE_STATS)",
    "This app is not from the system or manufacturer, but no specific suspicious behavior was found." to "\u1ee8ng d\u1ee5ng n\u00e0y kh\u00f4ng thu\u1ed9c h\u1ec7 th\u1ed1ng hay nh\u00e0 s\u1ea3n xu\u1ea5t nh\u01b0ng ch\u01b0a ph\u00e1t hi\u1ec7n h\u00e0nh vi \u0111\u00e1ng ng\u1edd c\u1ee5 th\u1ec3.",
    "Cannot uninstall" to "Kh\u00f4ng th\u1ec3 g\u1ee1"
)

private fun englishRiskReason(reason: String): String {
    return when {
        reason.contains("Samsung Smart Tutor") -> "Samsung Smart Tutor: official Samsung-distributed remote support app. Powerful permissions are normal for support features, but only use it when you need support."
        reason.contains("Giả mạo hệ thống") -> "Fake system app: the app name contains sensitive words such as System, Settings, or Security, but it is not an official app."
        reason.contains("Tên app giống thương hiệu lớn") -> "Brand mimic: the app looks like a major brand or Google app, but its install source is not official."
        reason.contains("Quyền trợ năng") -> "Accessibility permission: can read screen content, perform actions, or collect sensitive data."
        reason.contains("Quyền vẽ đè") -> "Overlay permission: can display full-screen popups over other apps."
        reason.contains("Quyền cài đặt ứng dụng") -> "Install permission: can download and install other APK files."
        reason.contains("Không có icon") -> "Hidden icon: the app hides from the launcher, a common adware sign."
        reason.contains("Nguồn không xác định") -> "Unknown source: installed from an APK outside trusted app stores."
        reason.contains("App tài chính/ngân hàng từ nguồn không rõ") -> "Financial/banking-like app from an unknown source: this is risky unless installed from a trusted app store."
        reason.contains("Quyền đọc/tin nhắn") -> "SMS permission: may read OTP or banking verification messages."
        reason.contains("Tự khởi chạy") -> "Auto-start: can run after reboot and keep ad services alive."
        reason.contains("Quyền đọc thông báo") -> "Notification access: may read or interact with notifications, including OTP messages."
        reason.contains("Quyền xem danh sách app") -> "App list access: can see installed banking apps and target scams."
        reason.contains("Tên package đáng ngờ") -> "Suspicious package name: contains adware, ad SDK, tracking, or campaign-related terms."
        reason.contains("Quyền truy cập dữ liệu sử dụng") -> "Usage access: can monitor which apps you open and target ads."
        reason.contains("Quyền Quản trị thiết bị") -> "Device admin permission: can make the app difficult to uninstall."
        reason.contains("Đang là Quản trị thiết bị") -> "Active device admin: disable device admin before uninstalling."
        reason.contains("Mới cài đặt") -> "Recently installed: this app was installed in the last 48 hours and may be related to recent popups."
        reason.contains("Số lượng quyền cao") -> "High permission count: requests many permissions and may collect excessive data."
        reason.contains("Internet + Tự khởi chạy") -> "Internet + auto-start: can download ads after the phone starts."
        reason.contains("Internet + Theo dõi app") -> "Internet + app tracking: can monitor app usage for targeted ads."
        reason.contains("Internet + Mới cài") -> "Internet + recently installed: watch it if popups started recently."
        reason.contains("Không icon + Internet") -> "Hidden icon + internet: can run in the background and load ads."
        reason.contains("Phát hiện") && reason.contains("popup") -> "Popup activity detected: this app has been recorded showing popups."
        reason.contains("Popup lặp lại nhiều lần") -> "Repeated popups: the install source may be trusted, but continuous popup behavior is a real risk signal."
        reason.contains("Kết hợp Vẽ đè + Trợ năng") -> "Overlay + Accessibility: can interact with ads without user input."
        reason.contains("Kết hợp Vẽ đè + Cài đặt") -> "Overlay + install permission: can use fake popups to install apps."
        reason.contains("Kết hợp SMS + Internet") -> "SMS + Internet: may send stolen OTP data over the network."
        reason.contains("Kết hợp Trợ năng + Tự khởi chạy") -> "Accessibility + auto-start: can keep monitoring after each reboot."
        reason.contains("Kết hợp Cài đặt + Tự khởi chạy") -> "Install + auto-start: can install apps after reboot."
        reason.contains("Tích lũy nhiều quyền nguy hiểm") -> "Multiple dangerous permissions: this app requests several sensitive permissions."
        else -> reason
    }
}
