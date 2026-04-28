package com.example.expressionphotobooth.data.repository;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/**
 * Maps Firebase Authentication exceptions and error codes to
 * user-friendly Vietnamese messages.
 *
 * Why this exists: Firebase returns English error strings directly from the SDK.
 * All error surfaces in the app must be in Vietnamese for a consistent UX.
 */
public final class FirebaseAuthErrorMapper {

    private FirebaseAuthErrorMapper() {}

    /**
     * Translates a Firebase Auth exception to a Vietnamese message.
     * Falls back to {@code fallback} when the exception type is unrecognised.
     */
    @NonNull
    public static String toVietnamese(@NonNull Exception exception, @NonNull String fallback) {

        // ── Weak password ─────────────────────────────────────────────────────
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return "Mật khẩu quá yếu. Vui lòng dùng ít nhất 6 ký tự.";
        }

        // ── Email already in use ──────────────────────────────────────────────
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return "Email này đã được đăng ký bởi tài khoản khác. Vui lòng đăng nhập hoặc dùng email khác.";
        }

        // ── Wrong credential (wrong password / bad token) ─────────────────────
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            String msg = exception.getMessage() != null ? exception.getMessage() : "";
            if (msg.contains("INVALID_LOGIN_CREDENTIALS")
                    || msg.contains("password is invalid")
                    || msg.contains("wrong-password")) {
                return "Email hoặc mật khẩu không đúng. Vui lòng kiểm tra lại.";
            }
            if (msg.contains("badly formatted") || msg.contains("INVALID_EMAIL")) {
                return "Định dạng email không hợp lệ. Vui lòng kiểm tra lại.";
            }
            return "Thông tin đăng nhập không hợp lệ. Vui lòng thử lại.";
        }

        // ── User not found / disabled ─────────────────────────────────────────
        if (exception instanceof FirebaseAuthInvalidUserException) {
            String errorCode = ((FirebaseAuthInvalidUserException) exception).getErrorCode();
            switch (errorCode) {
                case "ERROR_USER_NOT_FOUND":
                    return "Tài khoản không tồn tại. Vui lòng kiểm tra email hoặc đăng ký mới.";
                case "ERROR_USER_DISABLED":
                    return "Tài khoản của bạn đã bị vô hiệu hóa. Vui lòng liên hệ hỗ trợ.";
                case "ERROR_USER_TOKEN_EXPIRED":
                    return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
                default:
                    return "Tài khoản không hợp lệ. Vui lòng thử lại.";
            }
        }

        // ── Network error ─────────────────────────────────────────────────────
        if (exception instanceof FirebaseNetworkException) {
            return "Không có kết nối mạng. Vui lòng kiểm tra WiFi hoặc dữ liệu di động.";
        }

        // ── Generic FirebaseAuthException via error code ───────────────────────
        if (exception instanceof FirebaseAuthException) {
            String errorCode = ((FirebaseAuthException) exception).getErrorCode();
            switch (errorCode) {
                case "ERROR_EMAIL_ALREADY_IN_USE":
                    return "Email này đã được đăng ký. Vui lòng dùng email khác hoặc đăng nhập.";
                case "ERROR_WEAK_PASSWORD":
                    return "Mật khẩu quá yếu. Hãy thêm chữ hoa, số và ký tự đặc biệt.";
                case "ERROR_INVALID_EMAIL":
                    return "Định dạng email không hợp lệ.";
                case "ERROR_WRONG_PASSWORD":
                    return "Mật khẩu không đúng. Vui lòng thử lại.";
                case "ERROR_USER_NOT_FOUND":
                    return "Tài khoản không tồn tại. Vui lòng đăng ký.";
                case "ERROR_USER_DISABLED":
                    return "Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ.";
                case "ERROR_TOO_MANY_REQUESTS":
                    return "Quá nhiều yêu cầu. Firebase đã tạm thời khóa thiết bị. Vui lòng thử lại sau ít phút.";
                case "ERROR_NETWORK_REQUEST_FAILED":
                    return "Lỗi kết nối mạng. Vui lòng kiểm tra internet và thử lại.";
                case "ERROR_INVALID_CREDENTIAL":
                    return "Thông tin xác thực không hợp lệ hoặc đã hết hạn.";
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return "Phương thức đăng nhập này chưa được bật. Vui lòng liên hệ hỗ trợ.";
                case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                    return "Email này đã được đăng ký bằng phương thức khác (Google hoặc Email/Password).";
                case "ERROR_REQUIRES_RECENT_LOGIN":
                    return "Bạn cần đăng nhập lại để thực hiện thao tác này.";
                case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                    return "Tài khoản này đã được liên kết với một người dùng khác.";
                default:
                    break;
            }
        }

        // ── Raw message fallback — translate known English patterns ────────────
        String raw = exception.getMessage();
        if (raw != null) {
            if (raw.contains("email address is already in use")) {
                return "Email này đã được đăng ký. Vui lòng dùng email khác hoặc đăng nhập.";
            }
            if (raw.contains("no user record") || raw.contains("no account")) {
                return "Tài khoản không tồn tại. Vui lòng kiểm tra lại email.";
            }
            if (raw.contains("password is invalid") || raw.contains("INVALID_LOGIN_CREDENTIALS")) {
                return "Email hoặc mật khẩu không đúng.";
            }
            if (raw.contains("network") || raw.contains("Network")) {
                return "Lỗi kết nối mạng. Vui lòng thử lại.";
            }
            if (raw.contains("too many requests") || raw.contains("TOO_MANY_ATTEMPTS")) {
                return "Quá nhiều lần thử. Vui lòng đợi vài phút rồi thử lại.";
            }
            if (raw.contains("badly formatted")) {
                return "Định dạng email không hợp lệ.";
            }
        }

        return fallback;
    }
}
