import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens connections to the Abiso MySQL database. Change the three settings
 * below to match your setup.
 */
public final class Db {

    private static final String URL
            = "jdbc:mysql://localhost:3306/abiso_db?useSSL=false&serverTimezone=Asia/Manila";
    private static final String USER = "abiso_app";
    private static final String PASSWORD = "tesdasystem";  // the password you gave abiso_app

    private Db() {
    }

    /**
     * Returns a new connection. Always close it (use try-with-resources).
     */
    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * SQL that joins the three name columns of the users table into
     * "First Middle Last" (the middle name is skipped when empty).
     * Example: Db.fullName("u") for a query that uses "FROM users u".
     */
    public static String fullName(String alias) {
        return "CONCAT_WS(' ', " + alias + ".first_name, NULLIF("
                + alias + ".middle_name, ''), " + alias + ".last_name)";
    }

    /**
     * Quick check: run this file to see whether Java can reach the database.
     */
    public static void main(String[] args) {
        try (Connection c = get()) {
            System.out.println("Connected to: " + c.getCatalog());
            try (java.sql.ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM reports")) {
                rs.next();
                System.out.println("reports table is reachable. Rows: " + rs.getInt(1));
            }
        } catch (SQLException e) {
            System.out.println("Could not connect: " + e.getMessage());
        }
    }
}