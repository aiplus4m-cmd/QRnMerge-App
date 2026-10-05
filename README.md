# QRnMerge

**Scan tài liệu · Quét QR · Gộp PDF** – gộp 2 app *MyScanner* và *PDF Merger* của NhảmStudio thành 1 app Android gọn nhẹ.

| Tab | Chức năng |
|-----|-----------|
| **Scan** (mặc định) | Scan tài liệu bằng Google ML Kit Document Scanner (tự nhận viền, **làm phẳng** phối cảnh). Bộ lọc *Màu sắc nét / Xám / Đen trắng / Gốc* khử bóng, làm trắng giấy, **làm nét chữ** (thanh trượt Độ nét, Tương phản). Lưu thành **ảnh** (`Pictures/QRnMerge`) hoặc **PDF**, hoặc gửi sang tab Gộp. Ô **QR** nhỏ ở góc phải để quét mã QR / mã vạch (camera hoặc từ ảnh) – kèm quảng cáo công cụ tạo QR miễn phí https://topvl.net/qr |
| **Gộp PDF** | Chọn nhiều ảnh và/hoặc PDF, kéo ≡ để sắp xếp, đặt tên, chọn khổ trang (A4 / theo ảnh) → gộp thành 1 PDF lưu vào **`Download/QRnMerge`**, sau đó Mở / Chia sẻ / Lưu vào vị trí khác. Nhận cả file được *Chia sẻ* từ app khác. |
| **Giới thiệu** | Logo, Dev: NhảmStudio, Web: https://topvl.net |

## Tải APK
Mỗi lần push lên `main`, GitHub Actions build + test rồi đăng APK vào mục **[Releases](../../releases)**.

## Kỹ thuật
- Kotlin + Jetpack Compose (Material 3), minSdk 24 (Android 7.0), targetSdk 35.
- Scan/QR dùng Google Play services (model tải theo nhu cầu → APK nhỏ, không cần quyền Camera).
- Gộp PDF bằng PdfBox-Android: trang PDF được **copy nguyên vẹn** (không raster hoá), ảnh được nhúng JPEG.
- Lưu file qua MediaStore (Android 10+) hoặc thư mục công khai + quyền ghi (Android 9 trở xuống).
- Bộ lọc scan viết thuần Kotlin (`ImageEnhancer`): ước lượng nền giấy (max-pool + blur) → chia nền để khử bóng → levels → unsharp mask → (Đen trắng) ngưỡng thích nghi bằng integral image.

## Kiểm thử
- `./gradlew testDebugUnitTest` – unit test bộ lọc ảnh, phân tích nội dung QR, tên file.
- `./gradlew connectedDebugAndroidTest` – test trên thiết bị: gộp PDF+ảnh theo đúng thứ tự, kiểm tra file PDF **đã lưu trên máy** (Download/QRnMerge) đọc lại được, đúng số trang/khổ trang, trùng tên, PDF có mật khẩu, file hỏng, 12 ảnh 12MP, lưu ảnh JPEG, và test UI end-to-end bấm nút Gộp.
- CI chạy trên emulator Android 9 (API 28, lưu kiểu cũ) và Android 14 (API 34, MediaStore), rồi cài APK release đã ký + mở thử từng tab.

## Ký APK
APK release được ký bằng keystore trong `app/signing/` để các bản cập nhật cài đè được. Muốn dùng keystore riêng, thêm các secret
`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` vào repo (Settings → Secrets → Actions).

© 2026 NhảmStudio – https://topvl.net
