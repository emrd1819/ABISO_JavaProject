
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.function.IntSupplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/**
 * One sign-in page for everyone: the Account's role decides whether the
 * Resident or Staff dashboard opens. Also contains the resident sign-up form.
 * ResidentDashboard.java and StaffDashboard.java in the same package.
 *
 * Design notes: only BaseDashboard members that the original file already used
 * are referenced here.
 */
public class Login extends JFrame {

    // ---- look shared with the dashboards ----
    private static final Color BG = BaseDashboard.BG;
    private static final Color ACCENT = BaseDashboard.ACCENT;
    private static final Color GOLD = BaseDashboard.GOLD;
    private static final Color INK = BaseDashboard.INK;
    private static final Color BODY = BaseDashboard.BODY;
    private static final Color MUTED = BaseDashboard.MUTED;
    private static final Color LINE = BaseDashboard.LINE;
    private static final Color RED = BaseDashboard.RED;
    private static final Color GREEN = BaseDashboard.GREEN;
    private static final Color HINT = new Color(0xA7B0BE);
    private static final Color ICON = new Color(0x8A96A8);

    private static Font font(int style, int size) {
        return BaseDashboard.font(style, size);
    }

    private static Font serif(int style, int size) {
        return BaseDashboard.serif(style, size);
    }

    private static JLabel lbl(String t, int style, int size, Color c) {
        return BaseDashboard.lbl(t, style, size, c);
    }

    private static JPanel column(int gap, JComponent... items) {
        return BaseDashboard.column(gap, items);
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static class Account {

        final int id;
        final String name, email, password, role, purok;   // role: RESIDENT or STAFF
        String status;                                      // VERIFIED, PENDING or REJECTED

        Account(int id, String name, String email, String password, String role, String status, String purok) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.password = password;
            this.role = role;
            this.status = status;
            this.purok = purok;
        }
    }

    // Checks Gmail address format only; it does not verify mailbox ownership.
    private static boolean isValidGmail(String email) {
        return email != null
                && email.matches("(?i)^[a-z0-9](?:[a-z0-9.]{0,62}[a-z0-9])?@gmail\\.com$")
                && !email.substring(0, email.indexOf('@')).contains("..");
    }

    // Letters (including accents and Ñ), spaces, periods, hyphens, apostrophes.
    private static boolean isValidName(String name) {
        return name != null && name.matches("\\p{L}[\\p{L} .'\\-]{0,98}");
    }

    private static Account find(String email) {
        String sql = "SELECT u.user_id, " + Db.fullName("u") + " AS full_name, "
                + "u.email, u.password_hash, u.role, "
                + "u.account_status, u.purok "
                + "FROM users u "
                + "WHERE u.email = ?";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Account(
                            rs.getInt("user_id"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getString("account_status"),
                            rs.getString("purok")
                    );
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error while looking up the account: " + e.getMessage());
            throw new IllegalStateException("Unable to connect to the system. Please check your connection and try again.", e);
        }

        return null;
    }

    // ================================================================ FIELDS
    private JPanel cardBody;
    private Tabs tabs;
    private HintField loginEmail;
    private PasswordBox loginPassword;
    private Message loginError;
    private JButton signInButton;

    private HintField regLast, regFirst, regMiddle, regEmail, regPurok;
    private PasswordBox regPass, regConfirm;
    private StrengthMeter meter;
    private Message regError;
    private JButton registerButton;
    private JComponent loginView, registerView;

    // ================================================================ WINDOW
    public Login() {
        setTitle("Abiso - Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 740);
        setMinimumSize(new Dimension(1000, 660));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        buildLogin();
        buildRegister();
        add(brandPanel(), BorderLayout.WEST);
        add(rightSide(), BorderLayout.CENTER);
        showLogin();
    }

    private void setDefault(JButton b) {
        JRootPane rp = getRootPane();
        if (rp != null) {
            rp.setDefaultButton(b);
        }
    }

    // ================================================================ LEFT: BRAND PANEL
    private JComponent brandPanel() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(brandWidth(), 0);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = BaseDashboard.aa(g);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(0x081A30), w, h, new Color(0x255A9E)));
                g2.fillRect(0, 0, w, h);

                // subtle dot grid
                g2.setColor(new Color(255, 255, 255, 16));
                for (int x = 24; x < w; x += 26) {
                    for (int y = 24; y < h; y += 26) {
                        g2.fillOval(x, y, 2, 2);
                    }
                }

                // soft shapes and rings
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fillOval(w - 240, -140, 420, 420);
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillOval(-160, h - 280, 380, 380);
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(255, 255, 255, 26));
                g2.drawOval(-60, h - 180, 180, 180);
                g2.drawOval(-110, h - 230, 280, 280);
                g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 70));
                g2.drawOval(w - 150, 90, 120, 120);

                // gold edge
                g2.setPaint(new GradientPaint(0, 0, GOLD, 0, h, mix(GOLD, Color.WHITE, 0.35f)));
                g2.fillRect(w - 4, 0, 4, h);
                g2.dispose();
            }
        };

        // logo
        JPanel logo = new JPanel(new BorderLayout(14, 0));
        logo.setOpaque(false);
        logo.setBorder(new EmptyBorder(40, 48, 0, 20));
        JComponent mark = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(56, 56);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = BaseDashboard.aa(g);
                g2.setPaint(new GradientPaint(0, 0, mix(ACCENT, Color.WHITE, 0.15f), 56, 56, mix(ACCENT, Color.BLACK, 0.25f)));
                g2.fillRoundRect(0, 0, 56, 56, 16, 16);
                g2.setColor(GOLD);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawRoundRect(1, 1, 54, 54, 16, 16);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font(BaseDashboard.HEAD_FAMILY, Font.BOLD, 32));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("A", (56 - fm.stringWidth("A")) / 2, 40);
                g2.setColor(GOLD);
                g2.fillRoundRect(18, 45, 20, 3, 3, 3);
                g2.dispose();
            }
        };
        JPanel wordWrap = new JPanel(new GridBagLayout());
        wordWrap.setOpaque(false);
        JLabel word = new JLabel("Abiso");
        word.setFont(new Font(BaseDashboard.HEAD_FAMILY, Font.BOLD, 40));
        word.setForeground(Color.WHITE);
        GridBagConstraints wc = new GridBagConstraints();
        wc.anchor = GridBagConstraints.WEST;
        wc.weightx = 1;
        wordWrap.add(word, wc);
        logo.add(mark, BorderLayout.WEST);
        logo.add(wordWrap, BorderLayout.CENTER);
        p.add(logo, BorderLayout.NORTH);

        // headline + short description + features, centered vertically
        JLabel headline = BaseDashboard.wrap("Barangay Information and Complaint System", 390, 30, Color.WHITE);
        headline.setFont(serif(Font.BOLD, 30));
        JComponent goldRule = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(52, 4);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = BaseDashboard.aa(g);
                g2.setColor(GOLD);
                g2.fillRoundRect(0, 0, 52, 4, 4, 4);
                g2.dispose();
            }
        };
        JPanel ruleWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ruleWrap.setOpaque(false);
        ruleWrap.add(goldRule);
        JLabel intro = BaseDashboard.wrap(
                "Report concerns, follow their progress and read official announcements from your barangay, all in one place.",
                380, 14, new Color(0xC9D3E0));

        JPanel features = column(20,
                feature("Submit and track complaints", "File a report online and follow every update."),
                feature("Read official announcements", "News and notices straight from the barangay hall."),
                feature("Know the status of every report", "From received to resolved, nothing gets lost."));
        features.setOpaque(false);

        JPanel copy = column(20, headline, ruleWrap, intro, features);
        copy.setOpaque(false);
        JPanel centre = new JPanel(new GridBagLayout());
        centre.setOpaque(false);
        centre.setBorder(new EmptyBorder(0, 48, 0, 48));
        GridBagConstraints cc = new GridBagConstraints();
        cc.weightx = 1;
        cc.fill = GridBagConstraints.HORIZONTAL;
        centre.add(copy, cc);
        p.add(centre, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setBorder(new EmptyBorder(0, 48, 32, 48));
        foot.add(lbl("Serving one barangay, one community.", Font.PLAIN, 12, new Color(0x9FB0C6)), BorderLayout.WEST);
        p.add(foot, BorderLayout.SOUTH);
        return p;
    }

    private static JComponent feature(String title, String sub) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);

        JComponent badge = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(36, 36);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = BaseDashboard.aa(g);
                g2.setColor(new Color(255, 255, 255, 22));
                g2.fillOval(0, 0, 35, 35);
                g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 150));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(0, 0, 35, 35);
                g2.setColor(GOLD);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D check = new Path2D.Float();
                check.moveTo(11, 18);
                check.lineTo(16, 23);
                check.lineTo(25, 13);
                g2.draw(check);
                g2.dispose();
            }
        };
        JPanel badgeWrap = new JPanel(new BorderLayout());
        badgeWrap.setOpaque(false);
        badgeWrap.add(badge, BorderLayout.NORTH);

        JPanel text = column(3,
                lbl(title, Font.BOLD, 14, Color.WHITE),
                lbl(sub, Font.PLAIN, 12, new Color(0xA9BAD0)));
        text.setOpaque(false);
        row.add(badgeWrap, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    // ================================================================ RIGHT: CARD
    /**
     * Scroll content that is centered when there is room and scrolls when the
     * window is small.
     */
    private static class CenterPage extends JPanel implements Scrollable {

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 18;
        }

        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return 120;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport
                    && ((JViewport) getParent()).getHeight() > getPreferredSize().height;
        }
    }

    /**
     * White rounded card with a soft drop shadow.
     */
    private static class ShadowCard extends JPanel {

        static final int M = 16;   // room reserved for the shadow
        private final IntSupplier width;

        ShadowCard(IntSupplier width) {
            this.width = width;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width.getAsInt(), super.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = BaseDashboard.aa(g);
            int w = getWidth(), h = getHeight();
            for (int i = 0; i < M; i++) {
                int a = (int) (30 * Math.pow((i + 1.0) / M, 2.6));
                g2.setColor(new Color(15, 30, 55, a));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(i, i + 3, w - 2 * i - 1, h - 2 * i - 4, 28 - i, 28 - i);
            }
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(M, M, w - 2 * M - 1, h - 2 * M - 1, 16, 16);
            g2.setColor(LINE);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(M, M, w - 2 * M - 1, h - 2 * M - 1, 16, 16);
            g2.dispose();
        }
    }

    private int brandWidth() {
        return Math.max(500, (int) (getWidth() * 0.40));
    }

    private int cardWidth() {
        int avail = getWidth() - brandWidth() - 2 * 36 - 24;
        return Math.max(480, Math.min(900, avail));
    }

    private JComponent rightSide() {
        cardBody = new JPanel(new BorderLayout());
        cardBody.setOpaque(false);

        tabs = new Tabs("Sign in", this::showLogin, "Create account", this::showRegister);

        ShadowCard card = new ShadowCard(this::cardWidth);
        card.setLayout(new BorderLayout(0, 24));
        int m = ShadowCard.M;
        card.setBorder(new EmptyBorder(m + 28, m + 32, m + 28, m + 32));
        card.add(tabs, BorderLayout.NORTH);
        card.add(cardBody, BorderLayout.CENTER);

        CenterPage page = new CenterPage();
        page.setLayout(new GridBagLayout());
        page.setBackground(BG);
        page.setBorder(new EmptyBorder(12, 12, 12, 12));
        page.add(card);

        JScrollPane sp = new JScrollPane(page);
        sp.setBorder(null);
        sp.getViewport().setBackground(BG);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    private void swap(JComponent view) {
        cardBody.removeAll();
        cardBody.add(view, BorderLayout.CENTER);
        cardBody.revalidate();
        cardBody.repaint();
    }

    void showLogin() {
        loginError.clear();
        tabs.select(0);
        swap(loginView);
        setDefault(signInButton);
        loginEmail.requestFocusInWindow();
    }

    void showRegister() {
        regError.clear();
        tabs.select(1);
        swap(registerView);
        setDefault(registerButton);
        regLast.requestFocusInWindow();
    }

    // ================================================================ SIGN IN VIEW
    private void buildLogin() {
        loginEmail = textField("you@example.com");
        loginPassword = new PasswordBox("Enter your password", true);
        loginError = new Message();
        signInButton = new GradientButton("Sign in");
        signInButton.addActionListener(e -> signIn());

        JPanel forgotRow = new JPanel(new BorderLayout());
        forgotRow.setOpaque(false);
        forgotRow.add(link("Forgot password?", ()
                -> new ForgotPasswordDialog(this, loginEmail.getText().trim()).setVisible(true)),
                BorderLayout.EAST);

        loginView = column(18,
                column(4, BaseDashboard.heading("Welcome back", 28, INK),
                        lbl("Sign in to continue to Abiso.", Font.PLAIN, 14, MUTED)),
                BaseDashboard.labeled("Email", new FieldShell(MAIL, loginEmail, null)),
                column(8, BaseDashboard.labeled("Password", loginPassword), forgotRow),
                loginError,
                signInButton,
                rule(),
                centered(lbl("Staff: use the account from the barangay office.", Font.PLAIN, 12, MUTED)));
    }

    /**
     * Checks the sign-in form and opens the right dashboard.
     */
    void signIn() {
        String email = loginEmail.getText().trim();
        char[] passwordChars = loginPassword.field.getPassword();

        try {
            if (email.isEmpty() || passwordChars.length == 0) {
                loginError.show("Please enter your email and password.");
                return;
            }

            if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                loginError.show("Please enter a valid email address.");
                return;
            }

            Account acc;
            try {
                acc = find(email);
            } catch (IllegalStateException ex) {
                loginError.show(ex.getMessage());
                return;
            }

            boolean valid = false;

            if (acc != null && acc.password != null) {
                try {
                    valid = BCrypt.checkpw(
                            new String(passwordChars),
                            acc.password
                    );
                } catch (IllegalArgumentException e) {
                    valid = false;
                }
            }

            if (!valid) {
                loginError.show("Incorrect email or password.");
                return;
            }

            open(acc);

        } finally {
            java.util.Arrays.fill(passwordChars, '\0');
        }
    }

    private void open(Account acc) {
        dispose();
        if ("STAFF".equals(acc.role)) {
            new StaffDashboard(acc.id, acc.name).setVisible(true);
        } else {
            new ResidentDashboard(acc.id, acc.name, acc.status).setVisible(true);
        }
    }

    // ================================================================ SIGN UP VIEW
    private void buildRegister() {
        regLast = textField("e.g. Dela Cruz");
        regFirst = textField("e.g. Juan");
        regMiddle = textField("Optional");
        regEmail = textField("you@example.com");
        regPurok = textField("e.g. Purok 4");
        regPass = new PasswordBox("Password", false);
        regConfirm = new PasswordBox("Confirm", false);
        regError = new Message();
        meter = new StrengthMeter();
        registerButton = new GradientButton("Create account");
        registerButton.addActionListener(e -> register());

        regPass.field.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                meter.set(strength(new String(regPass.field.getPassword())));
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });

        JPanel passRow = new JPanel(new GridLayout(1, 2, 12, 0));
        passRow.setOpaque(false);
        passRow.add(BaseDashboard.labeled("Password", regPass));
        passRow.add(BaseDashboard.labeled("Confirm password", regConfirm));

        JPanel nameRow = new JPanel(new GridLayout(1, 2, 12, 0));
        nameRow.setOpaque(false);
        nameRow.add(BaseDashboard.labeled("Last name", new FieldShell(-1, regLast, null)));
        nameRow.add(BaseDashboard.labeled("First name", new FieldShell(-1, regFirst, null)));

        registerView = column(14,
                column(4, BaseDashboard.heading("Create your account", 28, INK),
                        BaseDashboard.wrap("For residents of the barangay. After signing in, finish setting up your profile so staff can verify your residency.",
                                340, 13, MUTED)),
                nameRow,
                BaseDashboard.labeled("Middle name (optional)", new FieldShell(USER, regMiddle, null)),
                BaseDashboard.labeled("Email", new FieldShell(MAIL, regEmail, null)),
                BaseDashboard.labeled("Purok / address", new FieldShell(PIN, regPurok, null)),
                column(8, passRow, meter),
                regError,
                registerButton);
    }

    /**
     * Checks the sign-up form and creates a pending resident account.
     */
    void register() {
        String last = regLast.getText().trim().replaceAll("\\s+", " ");
        String first = regFirst.getText().trim().replaceAll("\\s+", " ");
        String middle = regMiddle.getText().trim().replaceAll("\\s+", " ");
        String email = regEmail.getText().trim().toLowerCase();
        String purok = regPurok.getText().trim();
        String p1 = new String(regPass.field.getPassword());
        String p2 = new String(regConfirm.field.getPassword());

        if (last.isEmpty() || first.isEmpty() || email.isEmpty() || purok.isEmpty() || p1.isEmpty()) {
            regError.show("Please fill in all the fields. Only the middle name is optional.");
            return;
        }
        if (!isValidName(last) || !isValidName(first)
                || (!middle.isEmpty() && !isValidName(middle))) {
            regError.show("Names may only contain letters, spaces, periods, hyphens and apostrophes.");
            return;
        }
        if (!isValidGmail(email)) {
            regError.show("Please enter a valid Gmail address (example@gmail.com).");
            return;
        }
        try {
            if (find(email) != null) {
                regError.show("That email is already registered.");
                return;
            }
        } catch (IllegalStateException ex) {
            regError.show(ex.getMessage());
            return;
        }
        if (p1.length() < 8) {
            regError.show("Password must be at least 8 characters.");
            return;
        }
        if (!p1.equals(p2)) {
            regError.show("Passwords do not match.");
            return;
        }

        String passwordHash = BCrypt.hashpw(p1, BCrypt.gensalt(12));

        String sql = "INSERT INTO users "
                + "(last_name, first_name, middle_name, email, password_hash, "
                + "role, purok, account_status) "
                + "VALUES (?, ?, ?, ?, ?, 'RESIDENT', ?, 'PENDING')";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, last);
            stmt.setString(2, first);
            stmt.setString(3, middle.isEmpty() ? null : middle);
            stmt.setString(4, email);
            stmt.setString(5, passwordHash);
            stmt.setString(6, purok);
            stmt.executeUpdate();

        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            regError.show("That email is already registered.");
            return;
        } catch (SQLException e) {
            regError.show("Registration failed. Please try again.");
            System.err.println("Registration error: " + e.getMessage());
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Your account has been created.\nSign in and finish setting up your profile\n(contact number and address).\n"
                + "Barangay staff can verify your residency once that is done.",
                "Account created", JOptionPane.INFORMATION_MESSAGE);
        regLast.setText("");
        regFirst.setText("");
        regMiddle.setText("");
        regPurok.setText("");
        regEmail.setText("");
        regPass.field.setText("");
        regConfirm.field.setText("");
        loginEmail.setText(email);
        loginPassword.field.setText("");
        showLogin();
        loginPassword.field.requestFocusInWindow();
    }

    // ================================================================ SMALL PIECES
    private static final int USER = 0, MAIL = 1, LOCK = 2, PIN = 3;

    /**
     * Draws a simple 18x18 line icon.
     */
    private static void drawIcon(Graphics2D g, int kind, int x, int y, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case USER:
                g.drawOval(x + 5, y + 1, 8, 8);
                g.drawArc(x + 2, y + 11, 14, 14, 0, 180);
                break;
            case MAIL:
                g.drawRoundRect(x + 1, y + 3, 16, 12, 4, 4);
                g.drawLine(x + 2, y + 5, x + 9, y + 10);
                g.drawLine(x + 9, y + 10, x + 16, y + 5);
                break;
            case LOCK:
                g.drawRoundRect(x + 3, y + 8, 12, 9, 3, 3);
                g.drawArc(x + 5, y + 1, 8, 13, 0, 180);
                g.fillOval(x + 8, y + 11, 2, 3);
                break;
            case PIN:
                g.drawOval(x + 4, y + 1, 10, 10);
                g.drawLine(x + 5, y + 10, x + 9, y + 17);
                g.drawLine(x + 9, y + 17, x + 13, y + 10);
                g.fillOval(x + 7, y + 4, 4, 4);
                break;
            default:
        }
    }

    private static void paintHint(Graphics g, JTextComponent c, String hint) {
        if (hint == null || c.getDocument().getLength() > 0) {
            return;
        }
        Graphics2D g2 = BaseDashboard.aa(g);
        g2.setColor(HINT);
        g2.setFont(c.getFont());
        FontMetrics fm = g2.getFontMetrics();
        Insets in = c.getInsets();
        g2.drawString(hint, in.left, (c.getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
    }

    /**
     * Text field that shows a grey hint while empty.
     */
    static class HintField extends JTextField {

        private final String hint;

        HintField(String hint) {
            this.hint = hint;
            setFont(font(Font.PLAIN, 14));
            setForeground(INK);
            setCaretColor(ACCENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintHint(g, this, hint);
        }
    }

    static class HintPassword extends JPasswordField {

        private final String hint;

        HintPassword(String hint) {
            this.hint = hint;
            setFont(font(Font.PLAIN, 14));
            setForeground(INK);
            setCaretColor(ACCENT);
            setEchoChar('\u2022');
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintHint(g, this, hint);
        }
    }

    private static HintField textField(String hint) {
        return new HintField(hint);
    }

    /**
     * Rounded input box with a leading icon and a glowing ring while focused.
     */
    static class FieldShell extends JPanel {

        private boolean focused;
        private final int icon;

        FieldShell(int icon, JTextComponent input, JComponent trailing) {
            super(new BorderLayout(8, 0));
            this.icon = icon;
            setOpaque(false);
            setBorder(new EmptyBorder(0, icon < 0 ? 16 : 44, 0, 16));
            input.setOpaque(false);
            input.setBorder(null);
            input.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
            add(input, BorderLayout.CENTER);
            if (trailing != null) {
                add(trailing, BorderLayout.EAST);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, 50);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = BaseDashboard.aa(g);
            int w = getWidth(), h = getHeight();
            if (focused) {
                g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 45));
                g2.setStroke(new BasicStroke(4f));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 14, 14);
            }
            g2.setColor(focused ? Color.WHITE : new Color(0xFAFBFD));
            g2.fillRoundRect(3, 3, w - 7, h - 7, 10, 10);
            g2.setColor(focused ? ACCENT : LINE);
            g2.setStroke(new BasicStroke(focused ? 1.4f : 1f));
            g2.drawRoundRect(3, 3, w - 7, h - 7, 10, 10);
            if (icon >= 0) {
                drawIcon(g2, icon, 17, (h - 18) / 2, focused ? ACCENT : ICON);
            }
            g2.dispose();
        }
    }

    /**
     * Password field with a Show / Hide switch.
     */
    static class PasswordBox extends FieldShell {

        final JPasswordField field;

        PasswordBox(String hint, boolean withIcon) {
            this(new HintPassword(hint), withIcon);
        }

        private PasswordBox(HintPassword f, boolean withIcon) {
            super(withIcon ? LOCK : -1, f, toggle(f));
            this.field = f;
        }

        private static JLabel toggle(JPasswordField field) {
            JLabel toggle = lbl("Show", Font.BOLD, 12, ACCENT);
            toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggle.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    boolean hidden = field.getEchoChar() != (char) 0;
                    field.setEchoChar(hidden ? (char) 0 : '\u2022');
                    toggle.setText(hidden ? "Hide" : "Show");
                }
            });
            return toggle;
        }
    }

    /**
     * Segmented Sign in / Create account switch at the top of the card.
     */
    static class Tabs extends JComponent {

        private final String[] names;
        private final Runnable[] actions;
        private int sel;

        Tabs(String a, Runnable ra, String b, Runnable rb) {
            names = new String[]{a, b};
            actions = new Runnable[]{ra, rb};
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = e.getX() < getWidth() / 2 ? 0 : 1;
                    if (i != sel) {
                        actions[i].run();
                    }
                }
            });
        }

        void select(int i) {
            sel = i;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, 44);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = BaseDashboard.aa(g);
            int w = getWidth(), h = getHeight(), half = w / 2;
            g2.setColor(new Color(0xEEF1F6));
            g2.fillRoundRect(0, 0, w, h, 14, 14);
            int x = sel * half;
            g2.setColor(new Color(15, 30, 55, 14));
            g2.fillRoundRect(x + 4, 6, half - 8, h - 8, 11, 11);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x + 4, 4, half - 8, h - 8, 11, 11);
            g2.setColor(LINE);
            g2.drawRoundRect(x + 4, 4, half - 9, h - 9, 11, 11);
            g2.setFont(font(Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < 2; i++) {
                g2.setColor(i == sel ? ACCENT : MUTED);
                int tx = i * half + (half - fm.stringWidth(names[i])) / 2;
                g2.drawString(names[i], tx, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    /**
     * Main action button: gradient, soft shadow, hover and pressed states.
     */
    static class GradientButton extends JButton {

        GradientButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setForeground(Color.WHITE);
            setFont(font(Font.BOLD, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, 52);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = BaseDashboard.aa(g);
            int w = getWidth(), h = getHeight() - 5;
            boolean down = getModel().isPressed(), over = getModel().isRollover();
            Color top = over ? mix(ACCENT, Color.WHITE, 0.14f) : ACCENT;
            Color bottom = mix(ACCENT, Color.BLACK, down ? 0.35f : 0.2f);
            g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), over ? 90 : 60));
            g2.fillRoundRect(2, down ? 3 : 5, w - 4, h, 12, 12);
            g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g2.fillRoundRect(0, down ? 2 : 0, w, h, 12, 12);
            g2.setFont(getFont());
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
                    (h + fm.getAscent() - fm.getDescent()) / 2 + (down ? 2 : 0));
            g2.dispose();
        }
    }

    /**
     * Four-segment password strength bar with a short label.
     */
    private static int strength(String p) {
        if (p.isEmpty()) {
            return 0;
        }
        if (p.length() < 8) {
            return 1;
        }
        int s = 1;
        if (p.length() >= 12) {
            s++;
        }
        if (p.matches(".*[a-z].*") && p.matches(".*[A-Z].*")) {
            s++;
        }
        if (p.matches(".*\\d.*") && p.matches(".*[^A-Za-z0-9].*")) {
            s++;
        }
        return Math.min(4, s);
    }

    static class StrengthMeter extends JComponent {

        private int level;

        void set(int l) {
            level = l;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, 18);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = BaseDashboard.aa(g);
            int h = getHeight();
            Color[] cols = {RED, GOLD, ACCENT, GREEN};
            String[] names = {"Use at least 8 characters.", "Weak", "Fair", "Good", "Strong"};
            Color on = level == 0 ? LINE : cols[level - 1];
            for (int i = 0; i < 4; i++) {
                g2.setColor(i < level ? on : new Color(0xE3E8EF));
                g2.fillRoundRect(i * 36, h / 2 - 2, 32, 5, 5, 5);
            }
            g2.setFont(font(level == 0 ? Font.PLAIN : Font.BOLD, 12));
            g2.setColor(level == 0 ? MUTED : on);
            FontMetrics fm = g2.getFontMetrics();
            int tx = level == 0 ? 0 : 4 * 36 + 6;
            if (level == 0) {
                g2.drawString(names[0], 0, (h + fm.getAscent() - fm.getDescent()) / 2);
            } else {
                g2.drawString(names[level], tx, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    private static JLabel link(String text, Runnable action) {
        JLabel l = lbl(text, Font.BOLD, 12, ACCENT);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                l.setForeground(GOLD);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                l.setForeground(ACCENT);
            }
        });
        return l;
    }

    private static JComponent centered(JLabel l) {
        l.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    private static JComponent rule() {
        return new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(10, 1);
            }

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(LINE);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
    }

    /**
     * Red message box with a warning dot, shown under the form when something
     * is wrong.
     */
    static class Message extends BaseDashboard.RoundedPanel {

        private final JLabel text = new JLabel();

        Message() {
            super(10, BaseDashboard.tint(RED, 0.12f), BaseDashboard.tint(RED, 0.4f));
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(11, 42, 11, 14));
            text.setFont(font(Font.PLAIN, 13));
            text.setForeground(BaseDashboard.shade(RED, 0.35f));
            add(text, BorderLayout.CENTER);
            setVisible(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = BaseDashboard.aa(g);
            int d = 18, y = (getHeight() - d) / 2;
            g2.setColor(RED);
            g2.fillOval(14, y, d, d);
            g2.setColor(Color.WHITE);
            g2.setFont(font(Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("!", 14 + (d - fm.stringWidth("!")) / 2, y + (d + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        void show(String message) {
            text.setText("<html><body style='width:270px'>" + BaseDashboard.esc(message) + "</body></html>");
            setVisible(true);
            revalidate();
            repaint();
        }

        void clear() {
            setVisible(false);
            revalidate();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Login().setVisible(true));
    }
}
