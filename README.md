# AdWatcher

Ứng dụng Android phát hiện và cảnh báo ứng dụng đáng ngờ (adware, spyware, fake system apps) trên thiết bị của bạn.

## Tính năng

- **Quét ứng dụng**: Phân tích tất cả ứng dụng đã cài đặt, phát hiện dấu hiệu nguy hiểm
- **Chấm điểm rủi ro**: Mỗi ứng dụng được chấm điểm 0-100 dựa trên các tiêu chí:
  - Giả mạo tên hệ thống (Google, System, Update...)
  - Quyền vẽ đè màn hình (SYSTEM_ALERT_WINDOW)
  - Ẩn icon khỏi danh sách ứng dụng
  - Tự khởi chạy khi bật máy (RECEIVE_BOOT_COMPLETED)
  - Cài đặt từ nguồn không xác định (sideload)
- **Lọc thông minh**: Tự động bỏ qua ứng dụng hệ thống, ứng dụng từ Google, Samsung, nhà sản xuất và các big tech
- **Dịch vụ Accessibility**: Phát hiện popup quảng cáo và ghi log real-time
- **Giao diện tối**: Material 3 với theme tối toàn bộ
- **Ngôn ngữ**: Tiếng Việt

## Công nghệ

- Kotlin + Jetpack Compose (Material 3)
- Room Database (Popup logs)
- Android Accessibility Service
- Minimum SDK 26 (Android 8.0)
