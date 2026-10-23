import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;

public abstract class BaseDashboard extends JFrame {

    // ================================================================ THEME
    // Formal palette: deep navy, slate neutrals, muted gold, restrained status colors.
    static final Color BG = new Color(0xF4F5F7);
    static final Color SIDEBAR = new Color(0x0B1E36);
    static final Color NAV_TEXT = new Color(0xA7B4C4);
    static final Color ACCENT = new Color(0x1B3A6B);   // navy
    static final Color GOLD = new Color(0xB8934A);   // muted gold highlight
    static final Color INK = new Color(0x16212E);
    static final Color BODY = new Color(0x3F4B59);
    static final Color MUTED = new Color(0x667180);
    static final Color LINE = new Color(0xD9DEE5);
    static final Color GREEN = new Color(0x2F6B4F);
    static final Color AMBER = new Color(0x9C6B1A);
    static final Color RED = new Color(0x9B2C2C);
    static final Color PURPLE = new Color(0x4B4F8A);
    static final Color TEAL = new Color(0x2C6E7F);

    // Fonts: the first one installed on the computer is used (Windows has Calibri and Cambria).
    static final String BODY_FAMILY = pickFamily("Calibri", "Segoe UI", "Arial", "SansSerif");
    static final String HEAD_FAMILY = pickFamily("Cambria", "Georgia", "Times New Roman", "Serif");
    /**
     * Overall text size. Raise it to make everything bigger, lower it to make
     * everything smaller.
     */
    static final float FONT_SCALE = 1.22f;

    static String pickFamily(String... names) {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) {
            if (have.contains(n)) {
                return n;
            }
        }
        return names[names.length - 1];
    }

    /**
     * Body font.
     */
    static Font font(int style, int size) {
        return new Font(BODY_FAMILY, style, Math.round(size * FONT_SCALE));
    }

    /**
     * Formal serif font used for titles and headings.
     */
    static Font serif(int style, int size) {
        return new Font(HEAD_FAMILY, style, Math.round(size * FONT_SCALE));
    }

    /**
     * Blend a color toward white (0 = white, 1 = original).
     */
    static Color tint(Color c, float a) {
        return new Color(Math.round(255 - (255 - c.getRed()) * a),
                Math.round(255 - (255 - c.getGreen()) * a),
                Math.round(255 - (255 - c.getBlue()) * a));
    }

    /**
     * Blend a color toward black.
     */
    static Color shade(Color c, float a) {
        return new Color(Math.round(c.getRed() * (1 - a)), Math.round(c.getGreen() * (1 - a)),
                Math.round(c.getBlue() * (1 - a)));
    }

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    static String greeting() {
        int h = LocalTime.now().getHour();
        return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
    }

    static String firstName(String full) {
        return full.trim().split("\\s+")[0];
    }

    // ================================================================ ICONS
    /**
     * Draws a simple 20x20 line icon at (x, y).
     */
    static void drawIcon(Graphics2D g, String type, int x, int y, Color c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        g2.setColor(c);
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (type) {
            case "home":
                g2.drawPolyline(new int[]{2, 10, 18}, new int[]{10, 2, 10}, 3);
                g2.drawPolyline(new int[]{4, 4, 16, 16}, new int[]{9, 17, 17, 9}, 4);
                g2.drawPolyline(new int[]{8, 8, 12, 12}, new int[]{17, 12, 12, 17}, 4);
                break;
            case "megaphone":
                g2.drawPolygon(new int[]{3, 8, 17, 17, 8, 3}, new int[]{7, 7, 2, 16, 11, 11}, 6);
                g2.drawLine(6, 11, 7, 17);
                break;
            case "report":
                g2.drawRoundRect(2, 3, 16, 11, 4, 4);
                g2.drawPolyline(new int[]{6, 6, 10}, new int[]{14, 18, 14}, 3);
                g2.drawLine(10, 6, 10, 9);
                g2.fillOval(9, 10, 2, 2);
                break;
            case "list":
                g2.drawRoundRect(4, 3, 12, 15, 3, 3);
                g2.drawRect(7, 1, 6, 4);
                g2.drawLine(7, 9, 13, 9);
                g2.drawLine(7, 12, 13, 12);
                g2.drawLine(7, 15, 10, 15);
                break;
            case "logout":
                g2.drawPolyline(new int[]{8, 3, 3, 8}, new int[]{3, 3, 17, 17}, 4);
                g2.drawLine(8, 10, 17, 10);
                g2.drawPolyline(new int[]{13, 17, 13}, new int[]{6, 10, 14}, 3);
                break;
            case "overview":   // four tiles
                g2.drawRoundRect(2, 2, 6, 6, 2, 2);
                g2.drawRoundRect(12, 2, 6, 6, 2, 2);
                g2.drawRoundRect(2, 12, 6, 6, 2, 2);
                g2.drawRoundRect(12, 12, 6, 6, 2, 2);
                break;
            case "users":      // one person
                g2.drawOval(6, 2, 8, 8);
                g2.drawArc(3, 12, 14, 14, 0, 180);
                break;
            case "team":       // two people
                g2.drawOval(2, 3, 6, 6);
                g2.drawOval(12, 3, 6, 6);
                g2.drawArc(0, 12, 10, 10, 0, 180);
                g2.drawArc(10, 12, 10, 10, 0, 180);
                break;
            case "clock":
                g2.drawOval(2, 2, 16, 16);
                g2.drawPolyline(new int[]{10, 10, 14}, new int[]{5, 10, 12}, 3);
                break;
            case "check":
                g2.drawOval(2, 2, 16, 16);
                g2.drawPolyline(new int[]{6, 9, 14}, new int[]{10, 13, 7}, 3);
                break;
            case "search":
                g2.drawOval(3, 3, 10, 10);
                g2.drawLine(11, 11, 17, 17);
                break;
            case "bell":
                g2.drawArc(4, 2, 12, 14, 0, 180);
                g2.drawLine(4, 9, 3, 14);
                g2.drawLine(16, 9, 17, 14);
                g2.drawLine(2, 14, 18, 14);
                g2.drawArc(8, 13, 4, 5, 180, 180);
                break;
            case "info":
                g2.drawOval(2, 2, 16, 16);
                g2.drawLine(10, 9, 10, 14);
                g2.fillOval(9, 5, 2, 2);
                break;
            default:
                g2.fillOval(7, 7, 6, 6);
        }
        g2.dispose();
    }

    // ================================================================ SHELL
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel nav = new JPanel();
    private final Banner pageBanner = new Banner("", "");
    private final JPanel header;
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final Map<String, String[]> headings = new HashMap<>();
    protected final String userName;
    private final String roleLabel;

    protected BaseDashboard(String windowTitle, String roleLabel, String userName) {
        this.userName = userName;
        this.roleLabel = roleLabel;
        setTitle("Abiso - " + windowTitle);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(1180, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(BG);
        header = buildHeader();
        right.add(header, BorderLayout.NORTH);
        content.setBackground(BG);
        right.add(content, BorderLayout.CENTER);
        add(right, BorderLayout.CENTER);
    }

    /**
     * menuName = sidebar text; icon = home / overview / megaphone / report /
     * list / users / team; heading + subtitle go in the banner. An empty
     * heading hides the banner for that page.
     */
    protected void addPage(String menuName, String icon, String heading, String subtitle, JComponent body) {
        NavItem item = new NavItem(menuName, icon, () -> showPage(menuName));
        nav.add(item);
        nav.add(Box.createVerticalStrut(4));
        navItems.put(menuName, item);
        headings.put(menuName, new String[]{heading, subtitle});
        content.add(body, menuName);
        if (navItems.size() == 1) {
            showPage(menuName);
        }
    }

    /**
     * Older 4-argument version (default dot icon), so other dashboards still
     * compile.
     */
    protected void addPage(String menuName, String heading, String subtitle, JComponent body) {
        addPage(menuName, "dot", heading, subtitle, body);
    }

    /**
     * A page with no menu item (opened from code, e.g. a profile page).
     */
    protected void addHiddenPage(String menuName, String heading, String subtitle, JComponent body) {
        headings.put(menuName, new String[]{heading, subtitle});
        content.add(body, menuName);
    }

    /**
     * Return true in a dashboard that has a profile page; the user card in the
     * sidebar then becomes clickable and calls onProfileClick().
     */
    protected boolean hasProfilePage() {
        return false;
    }

    protected void onProfileClick() {
        // Subclasses open their profile page here.
    }

    protected void showPage(String menuName) {

        cards.show(content, menuName);

        pageBanner.setTexts(
                headings.get(menuName)[0],
                headings.get(menuName)[1]
        );

        header.setVisible(
                !headings.get(menuName)[0].isEmpty()
        );

        navItems.forEach((n, item)
                -> item.setActive(n.equals(menuName)));

        content.revalidate();
        content.repaint();

        header.revalidate();
        header.repaint();

        onPageOpened(menuName);
    }

    protected void onPageOpened(String menuName) {
        // Subclasses can refresh the selected page.
    }

    /**
     * Shows a small red counter on a menu item (0 hides it). The counter pops
     * when the number goes up.
     */
    protected void setBadge(String menuName, int count) {
        NavItem item = navItems.get(menuName);
        if (item != null) {
            item.setBadge(count);
        }
    }

    // ---- toast: a short confirmation card that fades in and out by itself ----
    private JComponent toastView;

    /**
     * Old two-argument form, kept so other dashboards still compile.
     */
    protected void toast(String message, Color accent) {
        boolean ok = GREEN.equals(accent);
        toast(ok ? "Success" : "Notice", message, accent, ok ? "check" : "info");
    }

    /**
     * Shows a card at the bottom right of the window for a few seconds. icon =
     * check / bell / info / megaphone ... (any name drawIcon knows).
     */
    protected void toast(String title, String message, Color accent, String icon) {
        JLayeredPane lp = getLayeredPane();
        if (toastView instanceof ToastView) {
            ((ToastView) toastView).stop();
        }
        if (toastView != null) {
            lp.remove(toastView);
        }
        ToastView t = new ToastView(title, message, accent, icon);
        t.setOnDone(() -> {
            lp.remove(t);
            if (toastView == t) {
                toastView = null;
            }
            lp.repaint();
        });
        Dimension d = t.getPreferredSize();
        t.setBounds(lp.getWidth() - d.width - 24, lp.getHeight() - d.height - 14, d.width, d.height);
        lp.add(t, JLayeredPane.POPUP_LAYER);
        toastView = t;
        t.start();
        lp.repaint();
    }

    /**
     * Rounded confirmation card (replaces the grey JOptionPane). Returns true
     * when the person presses the main button.
     */
    static boolean confirm(Component parent, String icon, Color color, String title, String message,
            String yesText, String noText, boolean danger) {
        Window owner = parent instanceof Window ? (Window) parent
                : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dlg = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        boolean clear = true;
        try {
            dlg.setBackground(new Color(0, 0, 0, 0));
        } catch (RuntimeException ex) {
            clear = false;   // this computer cannot draw see-through windows
        }
        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(!clear);
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(10, 10, 14, 10));

        RoundedPanel box = new RoundedPanel(18, Color.WHITE, LINE).withShadow();
        box.setLayout(new BorderLayout(0, 18));
        box.setBorder(new EmptyBorder(30, 40, 36, 40));

        JPanel ic = new JPanel(new GridBagLayout());
        ic.setOpaque(false);
        ic.add(new IconBubble(icon, color, 72));

        JLabel t = heading(title, 21, INK);
        t.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel m = new JLabel("<html><body style='width:300px'><div style='text-align:center'>"
                + esc(message) + "</div></body></html>");
        m.setFont(font(Font.PLAIN, 14));
        m.setForeground(MUTED);
        m.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel text = new JPanel(new BorderLayout(0, 8));
        text.setOpaque(false);
        text.add(t, BorderLayout.NORTH);
        text.add(m, BorderLayout.CENTER);

        boolean[] result = {false};
        JButton no = secondaryButton(noText);
        JButton yes = danger ? dangerButton(yesText) : primaryButton(yesText);
        no.addActionListener(e -> dlg.dispose());
        yes.addActionListener(e -> {
            result[0] = true;
            dlg.dispose();
        });
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        row.add(no);
        row.add(yes);

        box.add(ic, BorderLayout.NORTH);
        box.add(text, BorderLayout.CENTER);
        box.add(row, BorderLayout.SOUTH);
        root.add(box, BorderLayout.CENTER);
        dlg.setContentPane(root);
        dlg.getRootPane().registerKeyboardAction(e -> dlg.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return result[0];
    }

    /**
     * Called after the user confirms Logout: back to the sign-in page.
     */
    protected void onLogout() {
        dispose();
        new Login().setVisible(true);
    }

    private JPanel buildSidebar() {
        JPanel side = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(0x0F2744), 0, getHeight(), new Color(0x07162A)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        side.setOpaque(true);
        side.setPreferredSize(new Dimension(246, 0));

        // logo band: same top edge and height as the page banner, so the two line up
        JPanel logo = new JPanel(new BorderLayout());
        logo.setOpaque(false);
        logo.setBorder(new EmptyBorder(24, 16, 6, 16));
        logo.setPreferredSize(new Dimension(246, 24 + Banner.HEIGHT + 6));
        // no box: just the mark and the name, centred in the same band as the page banner
        JPanel brand = new JPanel(new BorderLayout(14, 0));
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(0, 14, 0, 10));
        JComponent mark = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(52, 52);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                g2.setPaint(new GradientPaint(0, 0, tint(ACCENT, 0.85f), 52, 52, shade(ACCENT, 0.25f)));
                g2.fillRoundRect(0, 0, 52, 52, 14, 14);
                g2.setColor(GOLD);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawRoundRect(1, 1, 50, 50, 14, 14);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font(HEAD_FAMILY, Font.BOLD, 30));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("A", (52 - fm.stringWidth("A")) / 2, 37);
                g2.setColor(GOLD);
                g2.fillRoundRect(17, 42, 18, 3, 3, 3);
                g2.dispose();
            }
        };
        JPanel markWrap = new JPanel(new GridBagLayout());
        markWrap.setOpaque(false);
        markWrap.add(mark);
        JPanel wordWrap = new JPanel(new GridBagLayout());
        wordWrap.setOpaque(false);
        JLabel word = new JLabel("Abiso");
        word.setFont(new Font(HEAD_FAMILY, Font.BOLD, 34));
        word.setForeground(Color.WHITE);
        GridBagConstraints wc = new GridBagConstraints();
        wc.anchor = GridBagConstraints.WEST;   // keep the word right beside the logo mark
        wc.weightx = 1;
        wordWrap.add(word, wc);
        brand.add(markWrap, BorderLayout.WEST);
        brand.add(wordWrap, BorderLayout.CENTER);
        logo.add(brand, BorderLayout.CENTER);
        side.add(logo, BorderLayout.NORTH);

        // menu
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setOpaque(false);
        nav.setBorder(new EmptyBorder(0, 0, 0, 0));
        JPanel menuLabel = new JPanel(new BorderLayout());
        menuLabel.setOpaque(false);
        menuLabel.setBorder(new EmptyBorder(6, 32, 8, 0));
        menuLabel.add(lbl("MENU", Font.BOLD, 11, new Color(0x6F819A)), BorderLayout.WEST);
        menuLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        nav.add(menuLabel);
        side.add(nav, BorderLayout.CENTER);

        // user card + logout
        JPanel foot = new JPanel(new BorderLayout(0, 8));
        foot.setOpaque(false);
        foot.setBorder(new EmptyBorder(0, 0, 16, 0));
        RoundedPanel who = new RoundedPanel(12, new Color(255, 255, 255, 16), new Color(255, 255, 255, 26));
        who.setLayout(new BorderLayout(10, 0));
        who.setBorder(new EmptyBorder(10, 12, 10, 12));
        who.add(new Avatar(userName, 40), BorderLayout.WEST);
        JPanel names = new JPanel(new GridLayout(2, 1));
        names.setOpaque(false);
        names.add(lbl(userName, Font.BOLD, 13, Color.WHITE));
        names.add(lbl(roleLabel, Font.PLAIN, 12, NAV_TEXT));
        who.add(names, BorderLayout.CENTER);
        if (hasProfilePage()) {
            who.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            who.setToolTipText("View my profile");
            who.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onProfileClick();
                }
            });
        }
        JPanel whoWrap = new JPanel(new BorderLayout());
        whoWrap.setOpaque(false);
        whoWrap.setBorder(new EmptyBorder(8, 16, 4, 16));
        whoWrap.add(who, BorderLayout.CENTER);
        foot.add(whoWrap, BorderLayout.NORTH);
        foot.add(new NavItem("Log out", "logout", () -> {
            if (confirm(this, "logout", ACCENT, "Log out of Abiso?",
                    "You will need to sign in again to see your reports and announcements.",
                    "Log out", "Stay signed in", false)) {
                onLogout();
            }
        }), BorderLayout.SOUTH);
        side.add(foot, BorderLayout.SOUTH);
        return side;
    }

    /**
     * Holds the banner card (title, subtitle and date) shown at the top of each
     * page.
     */
    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(BG);
        h.setBorder(new EmptyBorder(24, 32, 6, 32));
        h.add(pageBanner, BorderLayout.CENTER);
        return h;
    }

    // ================================================================ BANNER
    /**
     * Gradient banner card with a serif title, subtitle and today's date on the
     * right.
     */
    static class Banner extends JPanel {

        static final int HEIGHT = 118;

        private final JLabel title = new JLabel();
        private final JLabel subtitle = new JLabel();

        Banner(String t, String s) {
            setOpaque(false);
            setLayout(new BorderLayout(16, 0));
            setBorder(new EmptyBorder(20, 30, 20, 30));

            title.setFont(serif(Font.BOLD, 26));
            title.setForeground(Color.WHITE);
            subtitle.setFont(font(Font.PLAIN, 13));
            subtitle.setForeground(new Color(0xC9D3E0));
            setTexts(t, s);

            JPanel texts = new JPanel(new GridLayout(2, 1, 0, 4));
            texts.setOpaque(false);
            texts.add(title);
            texts.add(subtitle);

            RoundedPanel date = new RoundedPanel(8, new Color(255, 255, 255, 34), new Color(255, 255, 255, 80));
            date.setLayout(new BorderLayout());
            date.setBorder(new EmptyBorder(8, 18, 8, 18));
            date.add(lbl(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                    Font.BOLD, 13, Color.WHITE));
            JPanel east = new JPanel(new GridBagLayout());
            east.setOpaque(false);
            east.add(date);

            add(texts, BorderLayout.CENTER);
            add(east, BorderLayout.EAST);
        }

        void setTexts(String t, String s) {
            title.setText(t);
            subtitle.setText(s);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            Shape shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 12, 12);
            g2.setClip(shape);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x0B1E36), w, h, new Color(0x1F4B86)));
            g2.fill(shape);
            g2.setColor(new Color(255, 255, 255, 14));
            for (int x = 20; x < w; x += 26) {
                for (int y = 20; y < h; y += 26) {
                    g2.fillOval(x, y, 2, 2);
                }
            }
            g2.setColor(new Color(255, 255, 255, 16));
            g2.fillOval(w - 230, -100, 320, 320);
            g2.setColor(new Color(255, 255, 255, 10));
            g2.fillOval(w - 380, h - 100, 240, 240);
            g2.setColor(GOLD);
            g2.fillRect(0, h - 4, w, 4);
            g2.dispose();
        }
    }

    // ================================================================ LAYOUT HELPERS
    static JLabel lbl(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    /**
     * Serif heading label (titles of cards and pages).
     */
    static JLabel heading(String text, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(serif(Font.BOLD, size));
        l.setForeground(color);
        return l;
    }

    /**
     * Label whose text wraps at a fixed pixel width.
     */
    static JLabel wrap(String text, int width, int size, Color color) {
        JLabel l = new JLabel("<html><body style='width:" + width + "px'>" + text + "</body></html>");
        l.setFont(font(Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    /**
     * Makes text typed by a person safe to show inside a wrapped label (no
     * accidental HTML).
     */
    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    /**
     * Stacks components vertically, each stretched to full width.
     */
    static JPanel column(int gap, JComponent... items) {
        JPanel p = new JPanel(new StackLayout(gap));
        p.setOpaque(false);
        for (JComponent item : items) {
            p.add(item);
        }
        return p;
    }

    /**
     * Vertical stack: every child gets the full width and its own preferred
     * height. Hidden (invisible) children are skipped, so they take no space.
     */
    static class StackLayout implements LayoutManager {

        private final int gap;

        StackLayout(int gap) {
            this.gap = gap;
        }

        public void addLayoutComponent(String name, Component c) {
        }

        public void removeLayoutComponent(Component c) {
        }

        public Dimension preferredLayoutSize(Container p) {
            int w = 0, h = 0, n = 0;
            for (Component c : p.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getPreferredSize();
                w = Math.max(w, d.width);
                h += d.height;
                n++;
            }
            h += gap * Math.max(0, n - 1);
            Insets in = p.getInsets();
            return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
        }

        public Dimension minimumLayoutSize(Container p) {
            Insets in = p.getInsets();
            return new Dimension(in.left + in.right, preferredLayoutSize(p).height);
        }

        public void layoutContainer(Container p) {
            Insets in = p.getInsets();
            int y = in.top, w = p.getWidth() - in.left - in.right;
            for (Component c : p.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                int h = c.getPreferredSize().height;
                c.setBounds(in.left, y, w, h);
                y += h + gap;
            }
        }
    }

    /**
     * Two columns side by side with an exact width ratio (e.g. 2 : 1).
     */
    static JPanel twoCol(JComponent left, JComponent right, double wl, double wr) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.anchor = GridBagConstraints.NORTH;
        c.gridx = 0;
        c.weightx = wl;
        c.insets = new Insets(0, 0, 0, 9);
        p.add(narrow(left), c);
        c.gridx = 1;
        c.weightx = wr;
        c.insets = new Insets(0, 9, 0, 0);
        p.add(narrow(right), c);
        return p;
    }

    private static JComponent narrow(JComponent inner) {
        JPanel holder = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, super.getPreferredSize().height);
            }
        };
        holder.setOpaque(false);
        holder.add(inner, BorderLayout.CENTER);
        return holder;
    }

    static JPanel statRow(JComponent... cards) {
        JPanel row = new JPanel(new GridLayout(1, cards.length, 16, 0));
        row.setOpaque(false);
        for (JComponent c : cards) {
            row.add(c);
        }
        return row;
    }

    /**
     * Wraps page blocks in a scrollable, padded page.
     */
    static JComponent page(JComponent... blocks) {
        ScrollPage sp = new ScrollPage();
        sp.setLayout(new BorderLayout());
        sp.setBackground(BG);
        sp.setBorder(new EmptyBorder(6, 32, 32, 32));
        sp.add(column(18, blocks), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(sp);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    static class ScrollPage extends JPanel implements Scrollable {

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
            return false;
        }
    }

    // ================================================================ COMPONENTS
    /**
     * Rounded panel. With shadow on, a faint shadow shows under the bottom edge
     * (3 px are reserved for it).
     */
    static class RoundedPanel extends JPanel {

        private final int arc;
        private final Color fill, border;
        private boolean shadow;

        RoundedPanel(int arc, Color fill, Color border) {
            this.arc = arc;
            this.fill = fill;
            this.border = border;
            setOpaque(false);
        }

        RoundedPanel withShadow() {
            shadow = true;
            return this;
        }

        boolean hasShadow() {
            return shadow;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth() - 1, h = getHeight() - 1 - (shadow ? 3 : 0);
            if (shadow) {
                g2.setColor(new Color(15, 30, 55, 8));
                g2.fillRoundRect(0, 3, w, h, arc, arc);
                g2.fillRoundRect(0, 2, w, h, arc, arc);
                g2.fillRoundRect(1, 1, w - 2, h, arc, arc);
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, w, h, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static RoundedPanel card() {
        RoundedPanel p = new RoundedPanel(10, Color.WHITE, LINE).withShadow();
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 22, 21, 22));
        return p;
    }

    /**
     * White card with a serif title row and an optional link on the right.
     */
    static RoundedPanel section(String title, String link, JComponent body) {
        return section(title, link, null, body);
    }

    /**
     * Same, but the link runs onLink when clicked (e.g. jump to another page).
     */
    static RoundedPanel section(String title, String link, Runnable onLink, JComponent body) {
        RoundedPanel c = card();
        c.setLayout(new BorderLayout(0, 14));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(0, 0, 2, 0));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setOpaque(false);
        JComponent bar = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(4, 22);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g);
                g2.setColor(GOLD);
                g2.fillRoundRect(0, 0, 4, getHeight(), 4, 4);
                g2.dispose();
            }
        };
        titleRow.add(bar);
        titleRow.add(Box.createHorizontalStrut(10));
        titleRow.add(heading(title, 16, INK));
        head.add(titleRow, BorderLayout.WEST);
        if (link != null) {
            JLabel l = lbl(link + "  \u2192", Font.BOLD, 12, ACCENT);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onLink != null) {
                        onLink.run();
                    }
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
            head.add(l, BorderLayout.EAST);
        }
        c.add(head, BorderLayout.NORTH);
        c.add(body, BorderLayout.CENTER);
        return c;
    }

    static class Dot extends JComponent {

        private final Color color;
        private final int size;
        private final boolean ring;

        Dot(Color color, int size, boolean ring) {
            this.color = color;
            this.size = size;
            this.ring = ring;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(size, size);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            if (ring) {
                g2.setColor(tint(color, 0.18f));
                g2.fillOval(0, 0, size - 1, size - 1);
                int in = size / 3;
                g2.setColor(color);
                g2.fillOval((size - in) / 2, (size - in) / 2, in, in);
            } else {
                g2.setColor(color);
                g2.fillOval(0, 0, size - 1, size - 1);
            }
            g2.dispose();
        }
    }

    /**
     * Round tinted badge with a line icon inside.
     */
    static class IconBubble extends JComponent {

        private final String icon;
        private final Color color;
        private final int size;

        IconBubble(String icon, Color color, int size) {
            this.icon = icon;
            this.color = color;
            this.size = size;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(size, size);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(tint(color, 0.14f));
            g2.fillOval(0, 0, size - 1, size - 1);
            drawIcon(g2, icon, (size - 20) / 2, (size - 20) / 2, color);
            g2.dispose();
        }
    }

    static class Pill extends JComponent {

        private final String text;
        private final Color color;

        Pill(String text, Color color) {
            this.text = text;
            this.color = color;
            setFont(font(Font.BOLD, 12));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + 24, fm.getHeight() + 8);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(tint(color, 0.15f));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.setColor(shade(color, 0.22f));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    static Pill statusPill(String s) {
        Color c;
        switch (s.toLowerCase()) {
            case "waiting":
            case "awaiting":
                c = AMBER;
                break;
            case "reviewing":
                c = ACCENT;
                break;
            case "in progress":
                c = PURPLE;
                break;
            case "resolved":
            case "verified":
            case "active":
                c = GREEN;
                break;
            case "declined":
                c = RED;
                break;
            default:
                c = MUTED;
        }
        return new Pill(s, c);
    }

    static class Avatar extends JComponent {

        private static final Color[] COLORS = {ACCENT, GREEN, PURPLE, AMBER, TEAL, RED};
        private final String initials;
        private final Color color;
        private final int size;

        Avatar(String name, int size) {
            this.size = size;
            String[] parts = name.trim().split("\\s+");
            String i = parts[0].substring(0, 1) + (parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "");
            this.initials = i.toUpperCase();
            this.color = COLORS[Math.abs(name.hashCode()) % COLORS.length];
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(size, size);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(tint(color, 0.22f));
            g2.fillOval(0, 0, size - 1, size - 1);
            g2.setColor(shade(color, 0.2f));
            g2.setFont(font(Font.BOLD, size / 3 + 2));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initials, (size - fm.stringWidth(initials)) / 2,
                    (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /**
     * Rounded button. primary = navy; secondary = white with outline.
     */
    static class RoundButton extends JButton {

        private final Color fill, border, textColor;
        private boolean hover;

        RoundButton(String text, Color fill, Color textColor, Color border, int vpad, int hpad, int fontSize) {
            super(text);
            this.fill = fill;
            this.border = border;
            this.textColor = textColor;
            setFont(font(Font.BOLD, fontSize));
            setForeground(textColor);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(vpad, hpad, vpad, hpad));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public void setEnabled(boolean b) {
            super.setEnabled(b);
            setForeground(b ? textColor : new Color(0x8A94A3));
            setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            Color f = !isEnabled() ? new Color(0xE3E8ED)
                    : getModel().isPressed() ? shade(fill, 0.16f)
                    : hover ? shade(fill, 0.08f) : fill;
            g2.setColor(f);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            if (border != null) {
                g2.setColor(isEnabled() && hover ? ACCENT : border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static JButton primaryButton(String t) {
        return new RoundButton(t, ACCENT, Color.WHITE, null, 10, 22, 13);
    }

    static JButton secondaryButton(String t) {
        return new RoundButton(t, Color.WHITE, INK, LINE, 10, 22, 13);
    }

    static JButton smallButton(String t, Color c) {
        return new RoundButton(t, tint(c, 0.14f), shade(c, 0.25f), null, 6, 14, 12);
    }

    static JButton dangerButton(String t) {
        return new RoundButton(t, tint(RED, 0.14f), shade(RED, 0.25f), null, 10, 22, 13);
    }

    /**
     * Sidebar menu item with a line icon and an optional counter badge.
     */
    static class NavItem extends JComponent {

        private final String text, icon;
        private final Runnable action;
        private boolean active, hover;
        private int badge;
        private float pop = 1f;
        private Timer popTimer;

        NavItem(String text, String icon, Runnable action) {
            this.text = text;
            this.icon = icon;
            this.action = action;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    action.run();
                }
            });
        }

        void setActive(boolean a) {
            active = a;
            repaint();
        }

        void setBadge(int b) {
            boolean up = b > badge;
            badge = b;
            if (up) {
                if (popTimer != null) {
                    popTimer.stop();
                }
                long t0 = System.currentTimeMillis();
                popTimer = new Timer(16, e -> {
                    float t = (System.currentTimeMillis() - t0) / 500f;
                    if (t >= 1f) {
                        pop = 1f;
                        popTimer.stop();
                    } else {
                        pop = 1f + 0.5f * (float) Math.sin(t * Math.PI) * (1f - t * 0.4f);
                    }
                    repaint();
                });
                popTimer.start();
            }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(246, 48);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 48);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            if (active || hover) {
                g2.setColor(new Color(255, 255, 255, active ? 26 : 12));
                g2.fillRoundRect(14, 3, getWidth() - 28, getHeight() - 6, 10, 10);
            }
            if (active) {
                g2.setColor(GOLD);
                g2.fillRoundRect(0, 11, 4, getHeight() - 22, 4, 4);
            }
            drawIcon(g2, icon, 30, (getHeight() - 20) / 2, active ? GOLD : NAV_TEXT);
            g2.setColor(active ? Color.WHITE : NAV_TEXT);
            g2.setFont(font(active ? Font.BOLD : Font.PLAIN, 14));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 62, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            if (badge > 0) {
                String s = badge > 99 ? "99+" : String.valueOf(badge);
                g2.setFont(font(Font.BOLD, 11));
                fm = g2.getFontMetrics();
                int bw = Math.max(26, fm.stringWidth(s) + 16), bh = 26;
                int bx = getWidth() - 28 - bw, by = (getHeight() - bh) / 2;
                g2.translate(bx + bw / 2.0, by + bh / 2.0);   // grow from the centre while it pops
                g2.scale(pop, pop);
                g2.translate(-(bx + bw / 2.0), -(by + bh / 2.0));
                g2.setColor(new Color(0xD64545));
                g2.fillRoundRect(bx, by, bw, bh, bh, bh);
                g2.setColor(new Color(255, 255, 255, 190));
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawRoundRect(bx, by, bw, bh, bh, bh);
                g2.setColor(Color.WHITE);
                g2.drawString(s, bx + (bw - fm.stringWidth(s)) / 2, by + (bh + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    /**
     * Confirmation card shown by toast(): white, coloured edge, icon, title and
     * message, with a timer bar that shrinks. Click it to dismiss.
     */
    static class ToastView extends JComponent {

        private static final int LIFE = 4600;
        private final String title, text, icon;
        private final Color accent;
        private float alpha = 0f, progress = 1f;
        private long t0;
        private Timer anim;
        private Runnable onDone;

        ToastView(String title, String text, Color accent, String icon) {
            this.title = title;
            this.text = text;
            this.accent = accent;
            this.icon = icon;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    t0 = System.currentTimeMillis() - LIFE;   // click = dismiss now
                }
            });
        }

        void setOnDone(Runnable r) {
            onDone = r;
        }

        void start() {
            t0 = System.currentTimeMillis();
            anim = new Timer(30, e -> {
                long el = System.currentTimeMillis() - t0;
                if (el < 250) {
                    alpha = el / 250f;
                } else if (el < LIFE) {
                    alpha = 1f;
                    progress = 1f - (el - 250) / (float) (LIFE - 250);
                } else if (el < LIFE + 300) {
                    alpha = 1f - (el - LIFE) / 300f;
                    progress = 0f;
                } else {
                    anim.stop();
                    if (onDone != null) {
                        onDone.run();
                    }
                    return;
                }
                repaint();
            });
            anim.start();
        }

        void stop() {
            if (anim != null) {
                anim.stop();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int tw = Math.max(getFontMetrics(font(Font.BOLD, 14)).stringWidth(title),
                    getFontMetrics(font(Font.PLAIN, 13)).stringWidth(text));
            return new Dimension(Math.max(330, Math.min(560, tw + 120)) + 8, 92);
        }

        private static String fit(FontMetrics fm, String s, int max) {
            if (fm.stringWidth(s) <= max) {
                return s;
            }
            while (s.length() > 1 && fm.stringWidth(s + "\u2026") > max) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "\u2026";
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            float a = Math.max(0f, Math.min(1f, alpha));
            g2.setComposite(AlphaComposite.SrcOver.derive(a));
            g2.translate(0, Math.round((1f - a) * 14));
            int w = getWidth() - 8, h = getHeight() - 12;

            g2.setColor(new Color(15, 30, 55, 16));
            g2.fillRoundRect(2, 6, w, h, 18, 18);
            g2.fillRoundRect(1, 3, w, h, 18, 18);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, 16, 16);
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, w, h, 16, 16);

            g2.setClip(new RoundRectangle2D.Float(0, 0, w + 1, h + 1, 16, 16));
            g2.setColor(accent);
            g2.fillRect(0, 0, 6, h);
            g2.setColor(tint(accent, 0.45f));
            g2.fillRect(6, h - 3, (int) ((w - 6) * progress), 4);

            int by = (h - 44) / 2 - 1;
            if ("check".equals(icon)) {
                g2.setColor(accent);
                g2.fillOval(24, by, 44, 44);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D tick = new Path2D.Float();
                tick.moveTo(35, by + 23);
                tick.lineTo(43, by + 31);
                tick.lineTo(57, by + 14);
                g2.draw(tick);
            } else {
                g2.setColor(tint(accent, 0.14f));
                g2.fillOval(24, by, 44, 44);
                drawIcon(g2, icon, 24 + 12, by + 12, accent);
            }

            Font tf = font(Font.BOLD, 14), xf = font(Font.PLAIN, 13);
            FontMetrics ft = g2.getFontMetrics(tf), fx = g2.getFontMetrics(xf);
            int room = w - 86 - 18;
            int ty = (h - (ft.getHeight() + fx.getHeight() + 2)) / 2 + ft.getAscent();
            g2.setFont(tf);
            g2.setColor(INK);
            g2.drawString(fit(ft, title, room), 86, ty);
            g2.setFont(xf);
            g2.setColor(BODY);
            g2.drawString(fit(fx, text, room), 86, ty + ft.getDescent() + 2 + fx.getAscent());
            g2.dispose();
        }
    }

    static JComponent statCard(String label, String value, String note, Color accent) {
        return statCard(label, value, note, accent, null, null);
    }

    static JComponent statCard(String label, String value, String note, Color accent, String icon, Runnable onClick) {
        StatCard p = new StatCard(accent, onClick != null);
        p.setLayout(new BorderLayout(0, 2));
        p.setBorder(new EmptyBorder(16, 18, 17, 18));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(lbl(label, Font.PLAIN, 13, MUTED), BorderLayout.WEST);
        JComponent badge = icon == null ? new Dot(accent, 24, true) : new IconBubble(icon, accent, 38);
        top.add(badge, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);
        p.add(lbl(value, Font.BOLD, 34, INK), BorderLayout.CENTER);
        p.add(lbl(note, Font.PLAIN, 12, shade(accent, 0.1f)), BorderLayout.SOUTH);
        if (onClick != null) {
            p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            p.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    p.setHover(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    p.setHover(false);
                }
            });
        }
        return p;
    }

    static class StatCard extends RoundedPanel {

        private final Color accent;
        private boolean hover;

        StatCard(Color accent, boolean clickable) {
            super(10, Color.WHITE, LINE);
            withShadow();
            this.accent = accent;
        }

        void setHover(boolean h) {
            hover = h;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (hover) {
                Graphics2D g2 = aa(g);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 4, 10, 10);
                g2.dispose();
            }
        }
    }

    // ---- table with colored status badges ----
    static JComponent table(String[] cols, Object[][] rows, int statusCol, int... widths) {
        DefaultTableModel model = new DefaultTableModel(rows, cols) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable t = new JTable(model);
        t.setRowHeight(58);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFont(font(Font.PLAIN, 13));
        t.setForeground(INK);
        t.setSelectionBackground(new Color(0xE9EEF5));
        t.setSelectionForeground(INK);
        t.setFillsViewportHeight(false);
        t.putClientProperty("hover", -1);
        for (int i = 0; i < widths.length && i < cols.length; i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // highlight the row under the mouse
        t.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = t.rowAtPoint(e.getPoint());
                if (!Integer.valueOf(r).equals(t.getClientProperty("hover"))) {
                    t.putClientProperty("hover", r);
                    t.repaint();
                }
            }
        });
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                t.putClientProperty("hover", -1);
                t.repaint();
            }
        });

        MatteBorder rowLine = new MatteBorder(0, 0, 1, 0, LINE);
        DefaultTableCellRenderer plain = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel,
                    boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tb, v, sel, false, r, c);
                boolean hov = Integer.valueOf(r).equals(tb.getClientProperty("hover"));
                setBackground(sel ? new Color(0xE9EEF5) : hov ? new Color(0xF4F7FB) : Color.WHITE);
                setBorder(BorderFactory.createCompoundBorder(rowLine, new EmptyBorder(0, 8, 0, 8)));
                setForeground(c == 0 ? INK : BODY);
                setFont(font(c == 0 ? Font.BOLD : Font.PLAIN, 13));
                return this;
            }
        };
        TableCellRenderer pill = (tb, v, sel, foc, r, c)
                -> new PillCell(String.valueOf(v), sel, Integer.valueOf(r).equals(tb.getClientProperty("hover")));
        for (int i = 0; i < cols.length; i++) {
            t.getColumnModel().getColumn(i).setCellRenderer(i == statusCol ? pill : plain);
        }

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setResizingAllowed(false);
        h.setPreferredSize(new Dimension(0, 38));
        h.setDefaultRenderer((tb, v, sel, foc, r, c) -> {
            JLabel l = lbl(String.valueOf(v).toUpperCase(), Font.BOLD, 11, MUTED);
            l.setOpaque(true);
            l.setBackground(new Color(0xF6F7F9));
            l.setBorder(BorderFactory.createCompoundBorder(rowLine, new EmptyBorder(0, 8, 0, 8)));
            return l;
        });

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(h, BorderLayout.NORTH);
        wrap.add(t, BorderLayout.CENTER);
        return wrap;
    }

    /**
     * Table cell that paints a status badge directly (no layout needed).
     */
    static class PillCell extends JComponent {

        private final Pill pill;
        private final boolean selected, hover;

        PillCell(String status, boolean selected, boolean hover) {
            this.pill = statusPill(status);
            this.selected = selected;
            this.hover = hover;
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(selected ? new Color(0xE9EEF5) : hover ? new Color(0xF4F7FB) : Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(LINE);
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
            Dimension d = pill.getPreferredSize();
            pill.setSize(d);
            Graphics g2 = g.create(8, (getHeight() - d.height) / 2, d.width, d.height);
            pill.paint(g2);
            g2.dispose();
        }
    }

    // ---- row with avatar, name, subtitle and something on the right ----
    static JComponent personRow(String name, String sub, JComponent right) {
        return personRow(name, sub, right, null);
    }

    /**
     * Same, but when onNameClick is given the avatar and the name act like a
     * link (hand cursor, highlight on hover).
     */
    static JComponent personRow(String name, String sub, JComponent right, Runnable onNameClick) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(new MatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(10, 0, 10, 0)));
        Avatar avatar = new Avatar(name, 40);
        row.add(avatar, BorderLayout.WEST);
        JPanel texts = new JPanel(new GridLayout(2, 1));
        texts.setOpaque(false);
        JLabel nameLabel = lbl(name, Font.BOLD, 14, INK);
        texts.add(nameLabel);
        if (onNameClick != null) {
            makeLink(nameLabel, INK, ACCENT, onNameClick);
            makeLink(avatar, null, null, onNameClick);
        }
        texts.add(lbl(sub, Font.PLAIN, 12, MUTED));
        row.add(texts, BorderLayout.CENTER);
        if (right != null) {
            JPanel r = new JPanel(new GridBagLayout());
            r.setOpaque(false);
            r.add(right);
            row.add(r, BorderLayout.EAST);
        }
        return row;
    }

    /**
     * Makes a component behave like a link: hand cursor, optional colour change
     * on hover (labels only) and a click action.
     */
    static void makeLink(JComponent c, Color normal, Color hover, Runnable onClick) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.setToolTipText("View information");
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (hover != null) {
                    c.setForeground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (normal != null) {
                    c.setForeground(normal);
                }
            }
        });
    }

    static JPanel buttons(JComponent... bs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        for (JComponent b : bs) {
            p.add(b);
        }
        return p;
    }

    // ---- activity line: colored dot, text, time ----
    static JComponent activityRow(String text, String time, Color dot) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 0, 8, 0));
        JPanel d = new JPanel(new GridBagLayout());
        d.setOpaque(false);
        d.add(new Dot(dot, 10, false));
        row.add(d, BorderLayout.WEST);
        row.add(lbl(text, Font.PLAIN, 13, INK), BorderLayout.CENTER);
        row.add(lbl(time, Font.PLAIN, 12, MUTED), BorderLayout.EAST);
        return row;
    }

    // ---- 4-step progress tracker ----
    static final String[] STEPS = {"Received", "Under review", "In progress", "Resolved"};

    static class ProgressSteps extends JComponent {

        private final String[] steps;
        private final int current;

        ProgressSteps(String[] steps, int current) {
            this.steps = steps;
            this.current = current;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, 76);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int n = steps.length, margin = 44, y = 20, r = 12;
            int span = getWidth() - 2 * margin;
            for (int i = 0; i < n - 1; i++) {
                int x1 = margin + i * span / (n - 1), x2 = margin + (i + 1) * span / (n - 1);
                g2.setColor(i < current ? ACCENT : LINE);
                g2.fillRoundRect(x1, y - 2, x2 - x1, 4, 4, 4);
            }
            for (int i = 0; i < n; i++) {
                int x = margin + i * span / (n - 1);
                if (i < current) {
                    g2.setColor(ACCENT);
                    g2.fillOval(x - r, y - r, 2 * r, 2 * r);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x - 5, y, x - 1, y + 4);
                    g2.drawLine(x - 1, y + 4, x + 6, y - 4);
                } else if (i == current) {
                    g2.setColor(tint(ACCENT, 0.2f));
                    g2.fillOval(x - r - 4, y - r - 4, 2 * r + 8, 2 * r + 8);
                    g2.setColor(Color.WHITE);
                    g2.fillOval(x - r, y - r, 2 * r, 2 * r);
                    g2.setColor(ACCENT);
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawOval(x - r, y - r, 2 * r, 2 * r);
                    g2.fillOval(x - 4, y - 4, 8, 8);
                } else {
                    g2.setColor(LINE);
                    g2.fillOval(x - r, y - r, 2 * r, 2 * r);
                }
                g2.setStroke(new BasicStroke(1f));
                g2.setFont(font(i == current ? Font.BOLD : Font.PLAIN, 12));
                g2.setColor(i <= current ? INK : MUTED);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(steps[i], x - fm.stringWidth(steps[i]) / 2, y + r + 24);
            }
            g2.dispose();
        }
    }

    /**
     * Which step of the tracker a status is on (Resolved = every step
     * complete).
     */
    static int stepOf(String status) {
        switch (status) {
            case "Submitted":
                return 0;
            case "Viewed":
                return 1;
            case "In progress":
                return 2;
            default:
                return 4;
        }
    }

    /**
     * Light card around a progress tracker (used in the report details).
     */
    static JComponent progressCard(JComponent steps) {
        RoundedPanel pc = new RoundedPanel(10, new Color(0xF7F9FC), LINE);
        pc.setLayout(new BorderLayout());
        pc.setBorder(new EmptyBorder(16, 24, 10, 24));
        pc.add(steps, BorderLayout.CENTER);
        return pc;
    }

    // ---- vertical bar chart ----
    static class BarChart extends JComponent {

        private final String[] labels;
        private final int[] values;
        private final Color color;

        BarChart(String[] labels, int[] values, Color color) {
            this.labels = labels;
            this.values = values;
            this.color = color;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, 190);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int max = 1;
            for (int v : values) {
                max = Math.max(max, v);
            }
            int n = values.length, top = 22, bottom = 26, h = getHeight() - top - bottom;
            int slot = getWidth() / n, bw = Math.min(38, slot - 16);
            g2.setColor(LINE);
            g2.drawLine(0, top + h, getWidth(), top + h);
            for (int i = 0; i < n; i++) {
                int bh = Math.max(4, (int) ((double) values[i] / max * h));
                int x = i * slot + (slot - bw) / 2, y = top + h - bh;
                g2.setColor(values[i] == max ? color : tint(color, 0.35f));
                g2.fillRoundRect(x, y, bw, bh, 6, 6);
                g2.setFont(font(Font.BOLD, 12));
                g2.setColor(INK);
                FontMetrics fm = g2.getFontMetrics();
                String v = String.valueOf(values[i]);
                g2.drawString(v, x + (bw - fm.stringWidth(v)) / 2, y - 6);
                g2.setFont(font(Font.PLAIN, 12));
                g2.setColor(MUTED);
                fm = g2.getFontMetrics();
                g2.drawString(labels[i], x + (bw - fm.stringWidth(labels[i])) / 2, top + h + 18);
            }
            g2.dispose();
        }
    }

    // ---- horizontal bars (ranking) ----
    static class HBars extends JComponent {

        private final String[] labels;
        private final int[] values;
        private final Color[] colors;

        HBars(String[] labels, int[] values, Color[] colors) {
            this.labels = labels;
            this.values = values;
            this.colors = colors;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(300, labels.length * 38);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int max = 1;
            for (int v : values) {
                max = Math.max(max, v);
            }
            int labelW = 130, valueW = 36;
            for (int i = 0; i < labels.length; i++) {
                int y = i * 38 + 10;
                g2.setFont(font(Font.PLAIN, 13));
                g2.setColor(INK);
                g2.drawString(labels[i], 0, y + 12);
                int trackX = labelW, trackW = getWidth() - labelW - valueW;
                g2.setColor(new Color(0xE9EDF2));
                g2.fillRoundRect(trackX, y + 3, trackW, 10, 10, 10);
                g2.setColor(colors[i % colors.length]);
                g2.fillRoundRect(trackX, y + 3, Math.max(10, (int) ((double) values[i] / max * trackW)), 10, 10, 10);
                g2.setFont(font(Font.BOLD, 13));
                g2.setColor(INK);
                g2.drawString(String.valueOf(values[i]), getWidth() - valueW + 10, y + 12);
            }
            g2.dispose();
        }
    }

    // ---- a note box (info / warning) ----
    static JComponent notice(Color c, String title, String text) {
        RoundedPanel p = new RoundedPanel(10, tint(c, 0.12f), tint(c, 0.4f));
        p.setLayout(new BorderLayout(14, 0));
        p.setBorder(new EmptyBorder(14, 18, 14, 18));
        JPanel d = new JPanel(new GridBagLayout());
        d.setOpaque(false);
        d.add(new Dot(c, 28, true));
        p.add(d, BorderLayout.WEST);
        JPanel t = new JPanel(new GridLayout(2, 1, 0, 2));
        t.setOpaque(false);
        t.add(lbl(title, Font.BOLD, 14, shade(c, 0.35f)));
        t.add(lbl(text, Font.PLAIN, 13, BODY));
        p.add(t, BorderLayout.CENTER);
        return p;
    }

    // ---- "coming soon" page ----
    static JComponent placeholder(String title, String line) {
        RoundedPanel c = card();
        c.setLayout(new GridBagLayout());
        c.setBorder(new EmptyBorder(70, 20, 70, 20));
        JPanel col = new JPanel(new GridLayout(4, 1, 0, 8));
        col.setOpaque(false);
        JPanel icon = new JPanel(new GridBagLayout());
        icon.setOpaque(false);
        icon.add(new Dot(ACCENT, 56, true));
        col.add(icon);
        JLabel t = heading(title, 18, INK);
        t.setHorizontalAlignment(SwingConstants.CENTER);
        col.add(t);
        JLabel l = lbl(line, Font.PLAIN, 13, MUTED);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        col.add(l);
        JPanel pw = new JPanel(new GridBagLayout());
        pw.setOpaque(false);
        pw.add(new Pill("Coming soon", ACCENT));
        col.add(pw);
        c.add(col);
        return page(c);
    }

    // ================================================================ SHARED BY RESIDENT + STAFF
    /**
     * Colour of an announcement category.
     */
    static Color tagColor(String tag) {
        switch (tag) {
            case "Event":
                return ACCENT;
            case "Health":
                return GREEN;
            case "Notice":
                return AMBER;
            case "Reminder":
                return PURPLE;
            default:
                return MUTED;
        }
    }

    /**
     * Runs a data change. If the data layer refuses it, the reason is shown and
     * false is returned.
     */
    static boolean attempt(Component parent, Runnable change) {
        try {
            change.run();
            return true;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Action not allowed", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    /**
     * A panel whose content is rebuilt from the data whenever refresh() is
     * called. If the supplier returns null the panel hides itself and takes no
     * space.
     */
    static class Dyn extends JPanel {

        private final Supplier<JComponent> source;

        Dyn(Supplier<JComponent> source) {
            super(new BorderLayout());
            this.source = source;

            setOpaque(true);
            setBackground(BG);

            refresh();
        }

        void refresh() {
            JComponent c = source.get();

            removeAll();

            if (c != null) {
                add(c, BorderLayout.CENTER);
            }

            setVisible(c != null);

            revalidate();
            repaint();
        }
    }

    /**
     * Friendly text shown when a list has nothing in it.
     */
    static JComponent emptyState(String text) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(22, 0, 22, 0));
        JPanel col = new JPanel(new GridLayout(2, 1, 0, 10));
        col.setOpaque(false);
        JPanel icon = new JPanel(new GridBagLayout());
        icon.setOpaque(false);
        icon.add(new Dot(MUTED, 38, true));
        col.add(icon);
        JLabel l = new JLabel("<html><body style='width:250px; text-align:center'>" + esc(text) + "</body></html>");
        l.setFont(font(Font.PLAIN, 13));
        l.setForeground(MUTED);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        col.add(l);
        p.add(col);
        return p;
    }

    /**
     * Finds the JTable inside the component made by table(...).
     */
    static JTable tableOf(JComponent tableComponent) {
        for (Component c : tableComponent.getComponents()) {
            if (c instanceof JTable) {
                return (JTable) c;
            }
        }
        return null;
    }

    /**
     * Makes the rows of a table(...) clickable.
     */
    static JComponent clickable(JComponent tableComponent, IntConsumer onRow) {
        JTable t = tableOf(tableComponent);
        if (t == null) {
            return tableComponent;
        }
        t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = t.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    onRow.accept(row);
                }
            }
        });
        return tableComponent;
    }

    /**
     * Filter chip: white with outline, navy when selected.
     */
    static class Chip extends JComponent {

        private final String text;
        private boolean selected, hover;
        private Runnable onClick;

        Chip(String text) {
            this.text = text;
            setFont(font(Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) {
                        onClick.run();
                    }
                }
            });
        }

        void setOnClick(Runnable r) {
            onClick = r;
        }

        void setSelected(boolean s) {
            selected = s;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(text) + 34, fm.getHeight() + 14);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(selected ? ACCENT : hover ? new Color(0xEEF1F5) : Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            if (!selected) {
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            }
            g2.setFont(getFont());
            g2.setColor(selected ? Color.WHITE : BODY);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /**
     * A row of filter chips. options = {label, value}; the first one starts
     * selected.
     */
    static JPanel chipRow(String[][] options, Consumer<String> onPick) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        List<Chip> chips = new ArrayList<>();
        for (String[] o : options) {
            Chip chip = new Chip(o[0]);
            chips.add(chip);
            chip.setOnClick(() -> {
                for (Chip c : chips) {
                    c.setSelected(c == chip);
                }
                onPick.accept(o[1]);
            });
            if (!chips.isEmpty() && chips.size() > 1) {
                row.add(Box.createHorizontalStrut(8));
            }
            row.add(chip);
        }
        chips.get(0).setSelected(true);
        return row;
    }

    // ---- search box ----
    /**
     * True when the search text is empty or appears in any of the fields (case
     * does not matter).
     */
    static boolean matches(String query, String... fields) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(query)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Search box with a magnifier icon. onChange receives the lower-case text
     * on every keystroke.
     */
    static JComponent searchField(String hint, Consumer<String> onChange) {
        return new SearchBox(hint, onChange);
    }

    static class SearchBox extends JPanel {

        private boolean focused;

        SearchBox(String hint, Consumer<String> onChange) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(0, 42, 0, 14));
            JTextField f = new JTextField() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (getDocument().getLength() == 0) {
                        Graphics2D g2 = aa(g);
                        g2.setColor(new Color(0xA7B0BE));
                        g2.setFont(getFont());
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(hint, getInsets().left, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                        g2.dispose();
                    }
                }
            };
            f.setFont(font(Font.PLAIN, 13));
            f.setBorder(null);
            f.setOpaque(false);
            f.setForeground(INK);
            f.setCaretColor(ACCENT);
            f.addFocusListener(new FocusAdapter() {
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
            f.getDocument().addDocumentListener(new DocumentListener() {
                private void changed() {
                    onChange.accept(f.getText().trim().toLowerCase());
                }

                @Override
                public void insertUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    changed();
                }
            });
            add(f, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, 46);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth(), h = getHeight();
            if (focused) {
                g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 40));
                g2.setStroke(new BasicStroke(4f));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 14, 14);
            }
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(3, 3, w - 7, h - 7, 10, 10);
            g2.setColor(focused ? ACCENT : LINE);
            g2.setStroke(new BasicStroke(focused ? 1.4f : 1f));
            g2.drawRoundRect(3, 3, w - 7, h - 7, 10, 10);
            drawIcon(g2, "search", 16, (h - 20) / 2, focused ? ACCENT : new Color(0x8A96A8));
            g2.dispose();
        }
    }

    /**
     * Round "official staff" badge: navy circle, gold ring, megaphone icon.
     */
    static class StaffBadge extends JComponent {

        private final int size;

        StaffBadge(int size) {
            this.size = size;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(size, size);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(ACCENT);
            g2.fillOval(0, 0, size - 1, size - 1);
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawOval(1, 1, size - 3, size - 3);
            drawIcon(g2, "megaphone", (size - 20) / 2, (size - 20) / 2, Color.WHITE);
            g2.dispose();
        }
    }

    /**
     * Small grey box with label / value rows (when, where, location...).
     */
    static JComponent factBox(String[][] facts) {
        JPanel box = new JPanel(new GridBagLayout());
        box.setBorder(new EmptyBorder(12, 16, 12, 16));
        box.setBackground(new Color(0xF4F6F9));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        for (int i = 0; i < facts.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            c.weightx = 0;
            c.insets = new Insets(3, 0, 3, 18);
            box.add(lbl(facts[i][0].toUpperCase(), Font.BOLD, 11, MUTED), c);
            c.gridx = 1;
            c.weightx = 1;
            c.insets = new Insets(3, 0, 3, 0);
            box.add(lbl(facts[i][1], Font.PLAIN, 13, INK), c);
        }
        return box;
    }

    /**
     * A bulletin-board post by barangay staff. Tap it to expand or collapse the
     * full announcement. Staff can pass an "actions" component (Edit / Delete);
     * residents pass null.
     */
    static class PostCard extends RoundedPanel {

        private final Color stripe;
        private final JComponent preview, detail;
        private final JLabel hint;
        private boolean open, hover;

        PostCard(Announcement a, JComponent actions) {
            super(10, Color.WHITE, LINE);
            withShadow();
            Color tc = tagColor(a.tag);
            this.stripe = a.pinned ? GOLD : tc;
            setLayout(new BorderLayout(0, 12));
            setBorder(new EmptyBorder(18, 26, 17, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String when = Fmt.when(a.postedAt);

            JPanel header = new JPanel(new BorderLayout(12, 0));
            header.setOpaque(false);
            header.add(new StaffBadge(46), BorderLayout.WEST);
            JPanel who = new JPanel(new GridLayout(2, 1));
            who.setOpaque(false);
            who.add(lbl("Barangay Staff", Font.BOLD, 15, INK));
            who.add(lbl("Posted by " + a.staffName + ", " + a.staffRole + "  \u00B7  " + when, Font.PLAIN, 12, MUTED));
            header.add(who, BorderLayout.CENTER);
            JPanel pills = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            pills.setOpaque(false);
            if (a.pinned) {
                pills.add(new Pill("Pinned", GOLD));
            }
            pills.add(new Pill(a.tag, tc));
            JPanel pillWrap = new JPanel(new GridBagLayout());
            pillWrap.setOpaque(false);
            pillWrap.add(pills);
            header.add(pillWrap, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            preview = wrap(esc(a.summary), 560, 14, BODY);
            List<JComponent> parts = new ArrayList<>();
            parts.add(wrap(esc(a.fullText), 560, 14, BODY));
            if (a.facts != null && a.facts.length > 0) {
                parts.add(factBox(a.facts));
            }
            parts.add(lbl("Posted by " + a.staffName + "  \u00B7  " + when, Font.PLAIN, 12, MUTED));
            detail = column(12, parts.toArray(new JComponent[0]));
            detail.setVisible(false);
            add(column(10, heading(a.title, 19, INK), preview, detail), BorderLayout.CENTER);

            hint = lbl("Tap to read the full announcement  \u25BE", Font.BOLD, 12, ACCENT);
            JPanel foot = new JPanel(new BorderLayout());
            foot.setOpaque(false);
            foot.setBorder(new EmptyBorder(4, 0, 0, 0));
            foot.add(hint, BorderLayout.WEST);
            if (actions != null) {
                foot.add(actions, BorderLayout.EAST);
            }
            add(foot, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggle();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    // the mouse may only have moved onto a child (button, label): keep the highlight then
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), PostCard.this);
                    hover = PostCard.this.contains(p);
                    repaint();
                }
            });
        }

        void toggle() {
            open = !open;
            detail.setVisible(open);
            preview.setVisible(!open);
            hint.setText(open ? "Show less  \u25B4" : "Tap to read the full announcement  \u25BE");
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = aa(g);
            g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 4, 10, 10));
            g2.setColor(stripe);
            g2.fillRect(0, 0, 6, getHeight());
            if (hover) {
                g2.setColor(new Color(stripe.getRed(), stripe.getGreen(), stripe.getBlue(), 90));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 4, 10, 10);
            }
            g2.dispose();
        }
    }

    static List<JComponent> reportBlocks(Report r, boolean staffView) {
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pills.setOpaque(false);
        pills.add(statusPill(r.status));
        pills.add(Box.createHorizontalStrut(8));
        pills.add(new Pill(r.category, MUTED));
        JComponent header = column(6, lbl("REPORT " + r.ref(), Font.BOLD, 11, MUTED),
                heading(r.subject, 21, INK), pills);

        JComponent progress = "Declined".equals(r.status)
                ? notice(RED, "This report was declined", "See the staff note below for the reason.")
                : progressCard(new ProgressSteps(STEPS, stepOf(r.status)));

        List<String[]> f = new ArrayList<>();
        if (staffView) {
            f.add(new String[]{"Resident", r.resident});
        }
        f.add(new String[]{"Filed on", Fmt.date(r.filed)});
        f.add(new String[]{"Location", r.location});
        f.add(r.isClosed() ? new String[]{"Closed on", Fmt.date(r.closedOn)}
                : new String[]{"Last update", Fmt.date(r.updated)});

        JComponent what = column(6, heading(staffView ? "Reported details" : "What you reported", 15, INK),
                wrap(esc(r.details), 720, 15, BODY));

        String noteTitle = staffView ? "Notes sent to the resident" : "Updates from barangay staff";
        JComponent updates;
        if (r.notes.isEmpty()) {
            updates = column(6, heading(noteTitle, 15, INK),
                    lbl(staffView ? "No notes yet." : "No updates yet. Barangay staff will review your report soon.",
                            Font.PLAIN, 13, MUTED));
        } else {
            List<JComponent> rows = new ArrayList<>();
            rows.add(heading(noteTitle, 15, INK));
            for (Note n : r.notes) {
                rows.add(noteRow(n));
            }
            updates = column(8, rows.toArray(new JComponent[0]));
        }

        List<JComponent> blocks = new ArrayList<>();
        blocks.add(header);
        blocks.add(progress);
        blocks.add(factBox(f.toArray(new String[0][])));
        blocks.add(what);
        blocks.add(updates);
        return blocks;
    }

    /**
     * One staff note: dot, message, date and who wrote it.
     */
    static JComponent noteRow(Note n) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JPanel dot = new JPanel(new BorderLayout());
        dot.setOpaque(false);
        dot.setBorder(new EmptyBorder(5, 0, 0, 0));
        dot.add(new Dot(GOLD, 10, false), BorderLayout.NORTH);
        row.add(dot, BorderLayout.WEST);
        row.add(column(2, wrap(esc(n.text), 680, 14, INK),
                lbl(Fmt.date(n.date) + "  \u00B7  " + n.staff + ", Barangay Staff", Font.PLAIN, 12, MUTED)),
                BorderLayout.CENTER);
        return row;
    }

    /**
     * Puts content in a pop-up (scrollable if tall) and resizes it. Esc closes
     * the pop-up.
     */
    static void showIn(JDialog d, JComponent content, Component relativeTo) {
        JPanel pad = new JPanel(new BorderLayout());
        pad.setBackground(Color.WHITE);
        pad.setBorder(new EmptyBorder(28, 36, 28, 36));
        pad.add(content, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(pad);
        sp.setBorder(null);
        sp.getViewport().setBackground(Color.WHITE);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        Dimension pref = pad.getPreferredSize();
        int maxH = Math.min(780, Toolkit.getDefaultToolkit().getScreenSize().height - 100);
        sp.setPreferredSize(new Dimension(Math.max(900, pref.width) + 18,
                Math.max(540, Math.min(pref.height + 4, maxH))));
        d.setContentPane(sp);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.pack();
        d.setLocationRelativeTo(relativeTo);
    }

    // ---- form helpers ----
    static JComponent labeled(String text, JComponent field) {
        return column(6, lbl(text, Font.BOLD, 13, INK), field);
    }

    private static Border fieldBorder(boolean focused) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(focused ? ACCENT : LINE, focused ? 2 : 1),
                new EmptyBorder(focused ? 8 : 9, focused ? 9 : 10, focused ? 8 : 9, focused ? 9 : 10));
    }

    static JTextField inputField() {
        JTextField f = new JTextField();
        f.setFont(font(Font.PLAIN, 13));
        f.setBorder(fieldBorder(false));
        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                f.setBorder(fieldBorder(true));
            }

            @Override
            public void focusLost(FocusEvent e) {
                f.setBorder(fieldBorder(false));
            }
        });
        return f;
    }

    static JTextArea inputArea(int rows) {
        JTextArea a = new JTextArea(rows, 20);
        a.setFont(font(Font.PLAIN, 13));
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(new EmptyBorder(8, 10, 8, 10));
        return a;
    }

    static JScrollPane scrollOf(JTextArea a) {
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createLineBorder(LINE));
        a.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                sp.setBorder(BorderFactory.createLineBorder(ACCENT, 2));
            }

            @Override
            public void focusLost(FocusEvent e) {
                sp.setBorder(BorderFactory.createLineBorder(LINE));
            }
        });
        return sp;
    }

    static JComboBox<String> comboOf(String... items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(font(Font.PLAIN, 13));
        return c;
    }

    /**
     * Buttons side by side with 8 px between them and no outer padding (flush
     * with the card edge).
     */
    static JComponent tight(JComponent... bs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        p.setOpaque(false);
        for (int i = 0; i < bs.length; i++) {
            if (i > 0) {
                p.add(Box.createHorizontalStrut(8));
            }
            p.add(bs[i]);
        }
        return p;
    }

    /**
     * Row with something on the left and something on the right (either can be
     * null). Both sides are centred vertically so they line up.
     */
    static JComponent toolbar(JComponent left, JComponent right) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        if (left != null) {
            JPanel l = new JPanel(new GridBagLayout());
            l.setOpaque(false);
            l.add(left);
            bar.add(l, BorderLayout.WEST);
        }
        if (right != null) {
            JPanel r = new JPanel(new GridBagLayout());
            r.setOpaque(false);
            r.add(right);
            bar.add(r, BorderLayout.EAST);
        }
        return bar;
    }

    static JComponent titleBlock(String title, JComponent sub) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);
        p.add(heading(title, 17, INK));
        p.add(sub);
        return p;
    }

    // ================================================================ INPUT RULES
    /**
     * Stops unwanted characters as the person types or pastes, and caps the
     * length. Line breaks and tabs become spaces.
     */
    static void restrictInput(javax.swing.text.JTextComponent field,
            java.util.function.IntPredicate allowed, int maxLength) {

        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(
                new javax.swing.text.DocumentFilter() {

            private String clean(String text, int currentLength, int replacedLength) {
                StringBuilder out = new StringBuilder();
                if (text == null) {
                    return "";
                }
                String flat = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
                int room = maxLength - (currentLength - replacedLength);
                for (int i = 0; i < flat.length() && out.length() < room;) {
                    int cp = flat.codePointAt(i);
                    if (allowed.test(cp)) {
                        out.appendCodePoint(cp);
                    }
                    i += Character.charCount(cp);
                }
                return out.toString();
            }

            @Override
            public void insertString(FilterBypass fb, int offset, String text,
                    javax.swing.text.AttributeSet attr) throws javax.swing.text.BadLocationException {
                String c = clean(text, fb.getDocument().getLength(), 0);
                if (!c.isEmpty()) {
                    super.insertString(fb, offset, c, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text,
                    javax.swing.text.AttributeSet attrs) throws javax.swing.text.BadLocationException {
                super.replace(fb, offset, length,
                        clean(text, fb.getDocument().getLength(), length), attrs);
            }
        });
    }

    /** Contact number: digits only. */
    static boolean isDigit(int cp) {
        return cp >= '0' && cp <= '9';
    }

    /** Home address: letters, numbers, spaces, commas, periods and hyphens. */
    static boolean isAddressChar(int cp) {
        return Character.isLetterOrDigit(cp) || cp == ' ' || cp == ',' || cp == '.' || cp == '-';
    }

    static boolean isValidContactNumber(String s) {
        return s != null && s.matches("[0-9]{11}");
    }

    static boolean isValidAddress(String s) {
        return s != null && s.matches("[\\p{L}0-9 .,\\-]+");
    }

    static final String CONTACT_RULE = "Contact number must be exactly 11 digits (numbers only), for example 09171234567.";
    static final String ADDRESS_RULE = "Home address may only contain letters, numbers, spaces, commas, periods and hyphens.";

    // ================================================================ SHARED DATA MODELS AND CONSTANTS
    static final String[] STATUSES = {"Submitted", "Viewed", "In progress", "Resolved", "Declined"};
    static final String[] CATEGORIES = {"Sanitation", "Roads & Lights", "Noise", "Peace & Order", "Others"};
    static final String[] TAGS = {"Event", "Health", "Notice", "Reminder"};

    static class Note {

        final LocalDate date;
        final String staff;
        final String text;

        Note(LocalDate date, String staff, String text) {
            this.date = date;
            this.staff = staff;
            this.text = text;
        }
    }

    static class Report {

        final int id;
        int residentId;
        final String resident;
        String subject, category, location, details, status;
        final LocalDate filed;
        LocalDate updated, closedOn;
        final List<Note> notes = new ArrayList<>();

        Report(int id, String resident, String subject, String category, String location,
                String details, LocalDate filed, LocalDate updated, String status) {
            this.id = id;
            this.residentId = 0;
            this.resident = resident;
            this.subject = subject;
            this.category = category;
            this.location = location;
            this.details = details;
            this.filed = filed;
            this.updated = updated;
            this.status = status;
            if ("Resolved".equals(this.status)
                    || "Declined".equals(this.status)) {
                this.closedOn = updated;
            }
        }

        Report(int id, int residentId, String resident,
                String subject, String category, String location,
                String details, LocalDate filed,
                LocalDate updated, String status) {

            this(id, resident, subject, category, location,
                    details, filed, updated, status);

            // Store the actual MySQL resident ID
            this.residentId = residentId;
        }

        String ref() {
            return String.format("R-%04d", id);
        }

        boolean isClosed() {
            return "Resolved".equals(status) || "Declined".equals(status);
        }

        /**
         * A resident may edit or delete a report ONLY while it is still
         * Submitted.
         */
        boolean canResidentEdit() {
            return "Submitted".equals(status);
        }
    }

    static class Resident {

        final int userId;
        final String name;
        String purok, status;
        LocalDate registered, decidedOn;

        Resident(int userId, String name, String purok,
                String status, LocalDate registered,
                LocalDate decidedOn) {
            this.userId = userId;
            this.name = name;
            this.purok = purok;
            this.status = status;
            this.registered = registered;
            this.decidedOn = decidedOn;
        }
    }

    static class Announcement {

        final int id;
        String tag, title, summary, fullText, staffName, staffRole;
        String[][] facts;
        LocalDateTime postedAt;
        boolean pinned;

        Announcement(int id) {
            this.id = id;
        }
    }

    /**
     * Date and time text.
     */
    static class Fmt {

        static String date(LocalDate d) {
            return d == null ? "-" : d.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
        }

        static String when(LocalDateTime t) {
            LocalDate today = LocalDate.now();
            String time = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
            if (t.toLocalDate().equals(today)) {
                return "Today, " + time;
            }
            if (t.toLocalDate().equals(today.minusDays(1))) {
                return "Yesterday, " + time;
            }
            return date(t.toLocalDate()) + ", " + time;
        }
    }

    static List<Report> loadReports() {
        List<Report> reports = new ArrayList<>();

        String sql
                = "SELECT r.report_id, r.resident_id, "
                + Db.fullName("u") + " AS full_name, "
                + "r.subject, r.category, r.location, r.details, "
                + "r.status, r.filed_on, r.updated_on, r.closed_on "
                + "FROM reports r "
                + "JOIN users u ON r.resident_id = u.user_id "
                + "ORDER BY r.filed_on DESC, r.report_id DESC";

        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt
                = conn.prepareStatement(sql);
                java.sql.ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                java.sql.Date filed = rs.getDate("filed_on");
                java.sql.Date updated = rs.getDate("updated_on");

                Report report = new Report(
                        rs.getInt("report_id"),
                        rs.getInt("resident_id"),
                        rs.getString("full_name"),
                        rs.getString("subject"),
                        rs.getString("category"),
                        rs.getString("location"),
                        rs.getString("details"),
                        filed == null ? null : filed.toLocalDate(),
                        updated == null ? null : updated.toLocalDate(),
                        rs.getString("status")
                );

                java.sql.Date closed = rs.getDate("closed_on");
                report.closedOn
                        = closed == null ? null : closed.toLocalDate();

                reports.add(report);
                loadReportNotes(report);
            }

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Unable to load reports from MySQL.", e
            );
        }

        return reports;
    }

    static void loadReportNotes(Report report) {

        String sql
                = "SELECT n.created_on, " + Db.fullName("u") + " AS full_name, n.note "
                + "FROM report_notes n "
                + "JOIN users u ON n.staff_id = u.user_id "
                + "WHERE n.report_id = ? "
                + "ORDER BY n.created_on ASC, n.note_id ASC";

        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt
                = conn.prepareStatement(sql)) {

            stmt.setInt(1, report.id);

            try (java.sql.ResultSet rs = stmt.executeQuery()) {

                report.notes.clear();

                while (rs.next()) {

                    java.sql.Date date
                            = rs.getDate("created_on");

                    String staffName
                            = rs.getString("full_name");

                    String noteText
                            = rs.getString("note");

                    report.notes.add(new Note(
                            date.toLocalDate(),
                            staffName,
                            noteText
                    ));
                }
            }

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Unable to load report notes from MySQL: "
                    + e.getMessage(), e);
        }
    }

    static List<Report> loadResidentReports(int userId) {
        List<Report> ownReports = new ArrayList<>();

        for (Report report : loadReports()) {
            if (report.residentId == userId) {
                ownReports.add(report);
            }
        }

        return ownReports;
    }

    static List<Announcement> loadAnnouncements() {

        List<Announcement> announcements = new ArrayList<>();

        String sql = "SELECT a.announcement_id, a.tag, a.title, a.summary, "
                + "a.full_text, a.schedule, a.venue, a.pinned, a.posted_at, "
                + Db.fullName("u") + " AS full_name "
                + "FROM announcements a "
                + "JOIN users u ON a.staff_id = u.user_id "
                + "ORDER BY a.pinned DESC, a.posted_at DESC, a.announcement_id DESC";

        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                java.sql.ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {

                Announcement a = new Announcement(rs.getInt("announcement_id"));

                a.tag = rs.getString("tag");
                a.title = rs.getString("title");
                a.summary = rs.getString("summary");
                a.fullText = rs.getString("full_text");
                a.pinned = rs.getBoolean("pinned");

                java.sql.Timestamp posted = rs.getTimestamp("posted_at");
                a.postedAt = posted.toLocalDateTime();

                a.staffName = rs.getString("full_name");
                a.staffRole = "Barangay Staff";

                // "When" and "Where" are stored as the schedule and venue
                // columns; the screens still show them as detail rows.
                List<String[]> facts = new ArrayList<>();
                String schedule = rs.getString("schedule");
                String venue = rs.getString("venue");
                if (schedule != null && !schedule.trim().isEmpty()) {
                    facts.add(new String[]{"When", schedule});
                }
                if (venue != null && !venue.trim().isEmpty()) {
                    facts.add(new String[]{"Where", venue});
                }
                a.facts = facts.toArray(new String[0][]);

                announcements.add(a);
            }

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Unable to load announcements: " + e.getMessage(), e);
        }

        return announcements;
    }
}