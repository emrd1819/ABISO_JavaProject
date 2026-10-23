import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Forgot-password logic: create and email a 6-digit code, then check the code
 * and save the new password.
 *
 * Safety rules: the code is stored hashed (like a password), expires after 10
 * minutes, works once, and is locked after 5 wrong tries.
 */
public final class PasswordReset {

    public static final int OTP_MINUTES = 10;
    public static final int MAX_ATTEMPTS = 5;
    public static final int RESEND_SECONDS = 60;

    private static final SecureRandom RNG = new SecureRandom();

    private PasswordReset() {
    }

    /**
     * Sends a code to the email. If no resident has that email, it silently
     * does nothing, so nobody can use this form to find out who is registered.
     */
    public static void requestOtp(String email) throws SQLException, jakarta.mail.MessagingException {

        try (Connection conn = Db.get()) {

            int userId = -1;
            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT user_id FROM users WHERE email = ? AND role = 'RESIDENT'")) {
                s.setString(1, email);
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getInt(1);
                    }
                }
            }
            if (userId < 0) {
                return;
            }

            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT COUNT(*) FROM password_resets "
                    + "WHERE user_id = ? AND created_at > NOW() - INTERVAL ? SECOND")) {
                s.setInt(1, userId);
                s.setInt(2, RESEND_SECONDS);
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new IllegalStateException(
                                "Please wait a minute before asking for another code.");
                    }
                }
            }

            // old codes stop working
            try (PreparedStatement s = conn.prepareStatement(
                    "UPDATE password_resets SET used = 1 WHERE user_id = ? AND used = 0")) {
                s.setInt(1, userId);
                s.executeUpdate();
            }

            String otp = String.format("%06d", RNG.nextInt(1_000_000));
            String hash = BCrypt.hashpw(otp, BCrypt.gensalt(10));

            int resetId;
            try (PreparedStatement s = conn.prepareStatement(
                    "INSERT INTO password_resets (user_id, otp_hash, expires_at) "
                    + "VALUES (?, ?, NOW() + INTERVAL ? MINUTE)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                s.setInt(1, userId);
                s.setString(2, hash);
                s.setInt(3, OTP_MINUTES);
                s.executeUpdate();
                try (ResultSet keys = s.getGeneratedKeys()) {
                    keys.next();
                    resetId = keys.getInt(1);
                }
            }

            try {
                EmailService.send(email, "Your Abiso password reset code",
                        "Your Abiso verification code is: " + otp + "\n\n"
                        + "It expires in " + OTP_MINUTES + " minutes.\n"
                        + "If you did not ask for this, you can ignore this email.");
            } catch (jakarta.mail.MessagingException e) {
                try (PreparedStatement s = conn.prepareStatement(
                        "UPDATE password_resets SET used = 1 WHERE reset_id = ?")) {
                    s.setInt(1, resetId);
                    s.executeUpdate();
                }
                throw e;
            }
        }
    }

    /**
     * Checks the code and sets the new password. Throws
     * IllegalArgumentException with a friendly message when something is wrong.
     */
    public static void resetPassword(String email, String otp, String newPassword) throws SQLException {

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        try (Connection conn = Db.get()) {

            int resetId = -1, userId = -1, attempts = 0;
            String hash = null;

            try (PreparedStatement s = conn.prepareStatement(
                    "SELECT r.reset_id, r.user_id, r.otp_hash, r.attempts "
                    + "FROM password_resets r JOIN users u ON u.user_id = r.user_id "
                    + "WHERE u.email = ? AND u.role = 'RESIDENT' "
                    + "AND r.used = 0 AND r.expires_at > NOW() "
                    + "ORDER BY r.reset_id DESC LIMIT 1")) {
                s.setString(1, email);
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next()) {
                        resetId = rs.getInt(1);
                        userId = rs.getInt(2);
                        hash = rs.getString(3);
                        attempts = rs.getInt(4);
                    }
                }
            }

            if (hash == null) {
                throw new IllegalArgumentException(
                        "That code is invalid or has expired. Please request a new one.");
            }
            if (attempts >= MAX_ATTEMPTS) {
                markUsed(conn, resetId);
                throw new IllegalArgumentException(
                        "Too many wrong tries. Please request a new code.");
            }

            boolean ok;
            try {
                ok = BCrypt.checkpw(otp, hash);
            } catch (IllegalArgumentException e) {
                ok = false;
            }

            if (!ok) {
                try (PreparedStatement s = conn.prepareStatement(
                        "UPDATE password_resets SET attempts = attempts + 1 WHERE reset_id = ?")) {
                    s.setInt(1, resetId);
                    s.executeUpdate();
                }
                throw new IllegalArgumentException("Incorrect code. Please try again.");
            }

            conn.setAutoCommit(false);
            try {
                try (PreparedStatement s = conn.prepareStatement(
                        "UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                    s.setString(1, BCrypt.hashpw(newPassword, BCrypt.gensalt(12)));
                    s.setInt(2, userId);
                    s.executeUpdate();
                }
                markUsed(conn, resetId);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static void markUsed(Connection conn, int resetId) throws SQLException {
        try (PreparedStatement s = conn.prepareStatement(
                "UPDATE password_resets SET used = 1 WHERE reset_id = ?")) {
            s.setInt(1, resetId);
            s.executeUpdate();
        }
    }
}