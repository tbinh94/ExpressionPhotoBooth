# 🧪 Test Cases — Authentication (Phase 1 + 2 + 3)

> Chạy từng test case theo thứ tự. Build app ở **Debug** mode trước khi test.

---

## 🔴 PHASE 1 — Security

---

### TC-01 · Rate Limiting (Brute-force protection)

**Mục tiêu:** Sau 5 lần đăng nhập sai liên tiếp, app khóa trong 30 giây.

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Mở app → vào màn Login | Hiển thị form đăng nhập bình thường |
| 2 | Nhập email hợp lệ, password **sai** → nhấn Sign In | Dialog "Sign In Failed" |
| 3 | Lặp lại bước 2 thêm 4 lần (tổng 5 lần) | Lần thứ 5 vẫn báo lỗi bình thường |
| 4 | Thử lần thứ 6 | ❌ Phải hiện dialog **"Too Many Attempts"** với countdown giây |
| 5 | Đợi 30 giây → thử lại | ✅ Được phép thử lại bình thường |

**Pass criteria:** Lần thứ 6 bị chặn, không gửi request lên Firebase.

---

### TC-02 · Forgot Password — Anti User-Enumeration

**Mục tiêu:** Thông báo reset password không tiết lộ email có tồn tại hay không.

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Ở màn Login → nhấn "Forgot password?" | Không làm gì (cần nhập email trước) → hiện dialog yêu cầu nhập email |
| 2 | Nhập email **đã đăng ký** → nhấn Forgot | Dialog: "If this email is registered, a reset link has been sent. Please check your inbox." |
| 3 | Nhập email **chưa đăng ký** (vd: `fake_xyz@abc.com`) → nhấn Forgot | ✅ **Cùng một thông báo** như trên — không báo "Email chưa đăng ký" |
| 4 | Nhập email **định dạng sai** (vd: `abc`) | Dialog "Invalid email format" — báo lỗi format trước khi gửi |

**Pass criteria:** Email đã đăng ký và chưa đăng ký trả về **cùng 1 message**.

---

### TC-03 · Google Client ID từ resources

> ⚠️ Test này cần decompile APK hoặc kiểm tra code.

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Mở file `LoginActivity.java` | Dòng `requestIdToken(...)` dùng `getString(R.string.default_web_client_id)` |
| 2 | Tìm kiếm `"423474"` trong toàn bộ project | ❌ Không còn xuất hiện hardcode Client ID |
| 3 | Nhấn "Continue with Google" | ✅ Google picker hiện lên bình thường |

---

## 🟠 PHASE 2 — Performance

---

### TC-04 · Role Cache — Cold Start nhanh hơn

**Mục tiêu:** Lần 2 mở app không cần đợi Firestore.

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Đăng nhập Email/Password thành công | Vào HomeActivity bình thường |
| 2 | Tắt app hoàn toàn (swipe away) | — |
| 3 | Bật lại app | ✅ App chuyển thẳng vào HomeActivity **nhanh hơn** (không bị delay Firestore) |
| 4 | Tắt WiFi + Mobile Data → bật app lại | ✅ Vẫn điều hướng được đúng màn hình (dùng cache) |
| 5 | Sign Out → Sign In lại | Cache bị xóa → fetch Firestore lại 1 lần |

---

### TC-05 · Không gọi Firestore 2 lần khi đăng nhập Email

> Dùng Android Studio **Logcat** hoặc Firebase Console để đếm reads.

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Xóa app data (Settings → App → Clear Data) | Cache trống |
| 2 | Đăng nhập bằng Email/Password | Chỉ **1 lần read** Firestore (fetch role) rồi cache |
| 3 | Logcat filter: `FirebaseFirestore` | Không thấy 2 lần read liên tiếp ngay sau đăng nhập |

---

### TC-06 · Form State sau khi xoay màn hình

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Mở Login → nhập email `test@abc.com` | Email hiện trong field |
| 2 | Nhấn "Sign Up" → chuyển sang Register mode | Form đổi sang Register |
| 3 | Xoay màn hình (portrait → landscape) | ✅ Vẫn ở Register mode, email draft còn nguyên |
| 4 | Xoay lại (landscape → portrait) | ✅ State không bị reset |

---

## 🟡 PHASE 3 — UX

---

### TC-07 · Confirm Password Field

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Mở Login → nhấn "Sign Up" | Field **"Confirm Password"** xuất hiện bên dưới Password |
| 2 | Nhập password: `Test@123` — Confirm: `Test@456` | Nhấn Confirm Register → Dialog "Passwords do not match" |
| 3 | Nhập password: `Test@123` — Confirm: `Test@123` | ✅ Không hiện lỗi mismatch, tiếp tục đăng ký |
| 4 | Nhấn "Back to Login" | Confirm Password field biến mất, được reset |
| 5 | Quay lại Register mode | Confirm Password field trống lại |

---

### TC-08 · Password Strength Indicator

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Nhấn "Sign Up" để vào Register mode | Strength bar ẩn ban đầu |
| 2 | Gõ `abc` vào Password field | 🔴 1 segment đỏ — label **"Weak"** |
| 3 | Gõ thêm thành `abcdefgh` (≥8 ký tự) | 🟠 2 segments cam — label **"Fair"** |
| 4 | Gõ thêm chữ hoa: `Abcdefgh` | 🟡 3 segments vàng — label **"Good"** |
| 5 | Gõ thêm số: `Abcdefg1` | — |
| 6 | Gõ thêm ký tự đặc biệt: `Abcdefg1@` | 🟢 4 segments xanh — label **"Strong 🔒"** |
| 7 | Xóa hết password | Strength bar ẩn lại |
| 8 | Chuyển về Login mode (Back to Login) | Strength bar ẩn, không hiển thị ở login mode |

**Pass criteria:** Animation 4 segment transition smooth 220ms.

---

### TC-09 · Email Verification — Sau khi đăng ký

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Đăng ký tài khoản mới với email thật | App gửi email xác nhận |
| 2 | Sau khi nhấn Confirm Register | ✅ Dialog **"Verify Your Email"**: "A verification link has been sent to your-email@..." |
| 3 | App tự chuyển về Login mode (không vào HomeActivity) | ✅ Form quay về Sign In |
| 4 | Kiểm tra hộp thư email | ✅ Có email từ Firebase với link xác nhận |

---

### TC-10 · Email Verification — Chặn đăng nhập chưa xác nhận

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Đăng ký tài khoản mới (TC-09) — **không click link xác nhận** | — |
| 2 | Thử đăng nhập ngay với email + password vừa đăng ký | ❌ Dialog **"Email Not Verified"** xuất hiện |
| 3 | Dialog có nút **"Resend Email"** và **"Got it"** | ✅ Kiểm tra 2 nút tồn tại |
| 4 | Nhấn "Got it" | Dialog đóng, ở lại Login |
| 5 | Nhấn "Resend Email" | ✅ Dialog mới: "Verification email resent. Please check your inbox." |
| 6 | Click link trong email → thử đăng nhập lại | ✅ Đăng nhập thành công, vào HomeActivity |

---

### TC-11 · Google Sign-In không bị ảnh hưởng bởi Email Verification

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Nhấn "Continue with Google" | Google picker hiện |
| 2 | Chọn tài khoản Google | ✅ Đăng nhập thành công, không yêu cầu xác nhận email |

> **Lý do:** Google đã xác thực email phía provider, không cần Firebase verify lại.

---

### TC-12 · Guest không bị chặn bởi Email Verification

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Nhấn "Sign in as Guest" | ✅ Vào HomeActivity bình thường, không có bước verify |

---

## 🔁 Regression Tests — Các chức năng cũ không bị vỡ

| TC | Kiểm tra | Pass criteria |
|---|---|---|
| R-01 | Đăng nhập Email/Password tài khoản đã xác nhận | ✅ Vào HomeActivity |
| R-02 | Đăng nhập Admin account | ✅ Vào AdminDashboardActivity |
| R-03 | Sign Out → quay về Login | ✅ Form trống, cache xóa |
| R-04 | Forgot Password với email hợp lệ | ✅ Nhận email reset |
| R-05 | Chia sẻ ảnh với Guest → nhận sticker reward | ✅ Popup tặng sticker hiện |
| R-06 | Sticker mới hiện ngay khi quay lại EditPhoto | ✅ onResume load lại sticker |

---

> [!TIP]
> Dùng **Firebase Auth Console** (Authentication > Users) để kiểm tra trực tiếp trạng thái `emailVerified` của từng tài khoản sau khi test.

> [!IMPORTANT]
> Test TC-09 và TC-10 cần dùng **email thật** để nhận link xác nhận. Dùng Gmail alias trick: `yourname+test1@gmail.com`, `yourname+test2@gmail.com`…
