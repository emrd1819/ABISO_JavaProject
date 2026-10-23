import java.io.Console;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;
import org.mindrot.jbcrypt.BCrypt;

/**
 * One-time utility that creates a staff account in the users table.
 *
 * Run it from a terminal so the password is hidden. If it is run from an IDE
 * (no terminal), it still works, but the password is visible while typing.
 */
public class CreateStaffAccount {

    private static String ask(Console console, Scanner in, String prompt, String fallback) {
        String answer = console != null ? console.readLine("%s", prompt) : readLine(in, prompt);
        answer = answer == null ? "" : answer.trim();
        return answer.isEmpty() ? fallback : answer;
    }

    private static String readLine(Scanner in, String prompt) {
        System.out.print(prompt);
        return in.hasNextLine() ? in.nextLine() : "";
    }

    public static void main(String[] args) {

        Console console = System.console();
        Scanner in = null;

        if (console == null) {
            System.err.println("No hidden-input terminal found (for example, running from an IDE).");
            System.err.println("The password will be VISIBLE while you type it.");
            in = new Scanner(System.in);
        }

        String firstName = ask(console, in, "First name [ABISO]: ", "ABISO");
        String lastName = ask(console, in, "Last name [Staff]: ", "Staff");
        String email = ask(console, in, "Email [staff@abiso.local]: ", "staff@abiso.local").toLowerCase();

        String password;
        if (console != null) {
            char[] chars = console.readPassword("Enter a new staff password: ");
            password = chars == null ? "" : new String(chars);
            if (chars != null) {
                java.util.Arrays.fill(chars, '\0');
            }
        } else {
            password = readLine(in, "Enter a new staff password: ");
        }

        if (password.length() < 8) {
            System.out.println("Password must be at least 8 characters.");
            return;
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        String sql = "INSERT INTO users "
                + "(last_name, first_name, email, password_hash, role, "
                + "account_status, verified_on) "
                + "VALUES (?, ?, ?, ?, 'STAFF', 'VERIFIED', CURDATE())";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, lastName);
            stmt.setString(2, firstName);
            stmt.setString(3, email);
            stmt.setString(4, passwordHash);
            stmt.executeUpdate();

            try (PreparedStatement q = conn.prepareStatement(
                    "SELECT user_code FROM users WHERE email = ?")) {
                q.setString(1, email);
                try (ResultSet rs = q.executeQuery()) {
                    String code = rs.next() ? rs.getString(1) : "(none)";
                    System.out.println("Staff account created successfully! ID: " + code
                            + "  Email: " + email);
                }
            }

        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            System.err.println("That email is already registered.");
        } catch (SQLException e) {
            System.err.println("Staff creation failed: " + e.getMessage());
        }
    }
}