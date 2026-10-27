
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/*
These fields are the dashboard’s memory. They store who the resident is, their reports and announcements, and which page they’re on. 
The dyn() helper registers every section that can redraw itself, so when the database changes, we can update the screen without reloading the whole window*/
public class ResidentDashboard extends BaseDashboard {

    private final int userId;
    private String status;                              // VERIFIED, PENDING or REJECTED
    private final List<Report> reports = new ArrayList<>();
    private javax.swing.Timer refreshTimer;
    private boolean refreshing = false;
    private final List<Announcement> posts = loadAnnouncements();

    private final List<Dyn> dyns = new ArrayList<>();      // every live section; redrawn when data changes
    private Dyn reportsView;
    private Dyn reportFormView;
    private Dyn announcementFeedView;
    private Dyn profileHeaderView;
    private String topicFilter = null;                       // announcements filter (null = all)
    private boolean showingHistory = false;                  // My Reports: active list or history

    // ---- UI-only state (no database involved) ----
    private String currentPage = "Home";
    private int lastSeenAnnouncement = 0;                    // highest announcement id the resident has already seen
    private JTextField pPurok, pContact, pBirth;                     // My Profile form fields
    private JTextArea pAddress;
    private boolean profileLoaded = false;
    private boolean setupDone = true;                       // contact number + address filled in

    private Dyn dyn(Supplier<JComponent> source) {
        Dyn d = new Dyn(source);
        dyns.add(d);
        return d;
    }

    /**
     * Only verified residents can file reports.
     */
    private boolean isVerified() {
        return "VERIFIED".equals(status);
    }

    private String loadVerificationStatus() {

        if (userId <= 0) {
            return status;
        }

        String sql = "SELECT account_status FROM users "
                + "WHERE user_id = ?";

        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt
                = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("account_status");
                }
            }

            throw new IllegalStateException(
                    "Resident account could not be found.");

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Unable to check account verification status.", e);
        }
    }

    // ================================================================ CONSTRUCTOR
    public ResidentDashboard(int userId, String name, String verificationStatus) {
        super("Resident", "Resident", name);
        this.userId = userId;
        this.status = verificationStatus;
        if (userId > 0) {
            reports.addAll(BaseDashboard.loadResidentReports(userId));
        }
        lastSeenAnnouncement = readSeen();
        setupDone = isVerified() || detailsComplete();

        // addPage(menu name, icon, page heading, subtitle, body)
        addPage("Home", "home", "", "", home(name));   // empty heading = Home builds its own banner
        addPage("Announcements", "megaphone", "Announcements",
                "Official announcements from barangay staff. Tap a post to read it in full.", announcements());
        reportFormView = dyn(this::reportFormPage);

        JPanel reportPageHolder = new JPanel(new BorderLayout());
        reportPageHolder.setBackground(BG);
        reportPageHolder.add(reportFormView, BorderLayout.CENTER);

        addPage("Report a Concern", "report", "Report a Concern",
                "Tell the barangay about a problem in your community.",
                reportPageHolder);
        addPage("My Reports", "list", "My Reports",
                "Tap any report to see its full details.", myReports());
        // no menu item: it opens from the user card at the bottom of the sidebar
        addHiddenPage("My Profile", "My Profile",
                "Manage your personal details and check your account status.", profile());
        updateAnnouncementBadge();
        startAutoRefresh();
        if (needsSetup()) {   // a new resident lands on the setup page first
            SwingUtilities.invokeLater(() -> {
                showPage("My Profile");
                toast("Finish setting up", "Add your contact number and address so staff can verify you.",
                        GOLD, "info");
            });
        }
    }

    @Override
    protected boolean hasProfilePage() {
        return true;
    }

    @Override
    protected void onProfileClick() {
        showPage("My Profile");
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.dispose();
    }

    /**
     * Redraws every live section after the data changes.
     */
    private void refreshAll() {

        for (Dyn d : dyns) {

            // Keep the report form and opened announcements
            // unchanged during automatic refresh.
            if (d.isShowing()
                    && d != reportFormView
                    && d != announcementFeedView) {

                d.refresh();
            }
        }

        updateAnnouncementBadge();
    }

    @Override
    protected void onPageOpened(String menuName) {
        currentPage = menuName;
        if ("Announcements".equals(menuName)) {
            markAnnouncementsSeen();
        }
        if ("My Profile".equals(menuName)) {
            loadProfileOnce();
        }

        // Refresh only components on the selected page.
        SwingUtilities.invokeLater(() -> {
            refreshAll();
        });
    }

    // ------------------------------------------------------------ ACCOUNT SETUP
    /**
     * A resident has finished setting up when the contact number and the home
     * address are filled in.
     */
    private boolean detailsComplete() {
        if (userId <= 0) {
            return true;
        }
        return !loadResidentColumn("contact_number").trim().isEmpty()
                && !loadResidentColumn("address").trim().isEmpty();
    }

    /**
     * Pending residents must finish their profile before staff can verify them.
     */
    private boolean needsSetup() {
        return !isVerified() && !"REJECTED".equals(status) && !setupDone;
    }

    /**
     * Progress card: account created, complete profile, verification, verified.
     * Hidden once verified (or declined).
     */
    private JComponent setupBody() {
        if (isVerified() || "REJECTED".equals(status)) {
            return null;
        }
        boolean done = setupDone;
        RoundedPanel c = card();
        c.setLayout(new BorderLayout(0, 12));
        c.add(column(4,
                heading(done ? "Your details are in" : "Finish setting up your account", 18, INK),
                wrap(done ? "Barangay staff are checking your details. You can already read announcements; "
                        + "reporting unlocks once you are verified."
                        : "Add your contact number and home address. Staff can only verify your residency after this step.",
                        760, 13, MUTED)), BorderLayout.NORTH);
        c.add(new ProgressSteps(new String[]{"Account created", "Complete profile", "Verification", "Verified"},
                done ? 2 : 1), BorderLayout.CENTER);
        if (!done) {
            JButton go = primaryButton("Complete my profile");
            go.addActionListener(e -> showPage("My Profile"));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            row.setOpaque(false);
            row.add(go);
            c.add(row, BorderLayout.SOUTH);
        }
        return c;
    }

    // ------------------------------------------------------------ NOTIFICATIONS (UI only)
    // "Seen announcements" is remembered in a small file in the user's home folder
    private java.nio.file.Path seenFile() {
        return java.nio.file.Paths.get(System.getProperty("user.home"), ".abiso",
                "seen_announcement_" + userId + ".txt");
    }

    private int readSeen() {
        try {
            return Integer.parseInt(new String(java.nio.file.Files.readAllBytes(seenFile()),
                    java.nio.charset.StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            return 0;   // no file yet: everything counts as new
        }
    }

    private void writeSeen(int value) {
        try {
            java.nio.file.Path p = seenFile();
            java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.write(p, String.valueOf(value)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // remembering what was seen is only a convenience
        }
    }

    private int maxPostId() {
        int max = 0;
        for (Announcement a : posts) {
            max = Math.max(max, a.id);
        }
        return max;
    }

    /**
     * Announcements posted by staff that this resident has not opened yet.
     */
    private int unreadCount() {
        if ("Announcements".equals(currentPage)) {
            return 0;
        }
        int n = 0;
        for (Announcement a : posts) {
            if (a.id > lastSeenAnnouncement) {
                n++;
            }
        }
        return n;
    }

    private void markAnnouncementsSeen() {
        int max = maxPostId();
        if (max > lastSeenAnnouncement) {
            lastSeenAnnouncement = max;
            writeSeen(max);
        }
    }

    private void updateAnnouncementBadge() {
        if ("Announcements".equals(currentPage)) {
            markAnnouncementsSeen();
        }
        setBadge("Announcements", unreadCount());
    }

    /**
     * Pop-up shown when the verification status changes while the resident is
     * logged in.
     */
    private void statusChanged(String now) {
        if ("VERIFIED".equals(now)) {
            toast("Account verified", "You are now verified. You can file reports.", GREEN, "check");
        } else if ("REJECTED".equals(now)) {
            toast("Verification not approved", "Visit the barangay hall with a valid ID so staff can reconsider.",
                    RED, "info");
        } else if ("PENDING".equals(now)) {
            toast("Registration under review", "Staff are reconsidering your registration.", ACCENT, "info");
        }
        if (reportFormView != null) {
            reportFormView.refresh();   // unlock (or lock) the Report a Concern page
        }
    }

    // Compare announcement content, not just IDs, so edits and deletions are detected.
    private String announcementSnapshot(List<Announcement> announcements) {
        StringBuilder result = new StringBuilder();
        java.util.List<Announcement> ordered = new ArrayList<>(announcements);
        ordered.sort((a, b) -> Integer.compare(a.id, b.id));
        for (Announcement a : ordered) {
            result.append(a.id).append('|')
                    .append(a.tag).append('|')
                    .append(a.title).append('|')
                    .append(a.summary).append('|')
                    .append(a.fullText).append('|')
                    .append(a.pinned).append('|')
                    .append(a.postedAt).append('|');
            if (a.facts != null) {
                for (String[] fact : a.facts) {
                    result.append(java.util.Arrays.toString(fact)).append('|');
                }
            }
            result.append('\n');
        }
        return result.toString();
    }

    private void startAutoRefresh() {

        refreshTimer = new javax.swing.Timer(5000, e -> {

            if (refreshing || !isDisplayable() || userId <= 0) {
                return;
            }

            refreshing = true;

            new SwingWorker<Object[], Void>() {

                @Override
                protected Object[] doInBackground() throws Exception {

                    List<Report> latestReports
                            = BaseDashboard.loadResidentReports(userId);

                    List<Announcement> latestAnnouncements
                            = loadAnnouncements();

                    String latestStatus = loadVerificationStatus();

                    return new Object[]{
                        latestReports,
                        latestAnnouncements,
                        latestStatus
                    };
                }

                @Override
                protected void done() {
                    try {
                        Object[] data = get();

                        @SuppressWarnings("unchecked")
                        List<Report> latestReports
                                = (List<Report>) data[0];

                        @SuppressWarnings("unchecked")
                        List<Announcement> latestAnnouncements
                                = (List<Announcement>) data[1];

                        String latestStatus = (String) data[2];

                        // remember the old state so we can tell what changed
                        java.util.Map<Integer, String> oldReportStatus = new java.util.HashMap<>();
                        for (Report r : reports) {
                            oldReportStatus.put(r.id, r.status);
                        }
                        int oldMaxPost = maxPostId();
                        String oldAnnouncementSnapshot = announcementSnapshot(posts);
                        String oldStatus = status;

                        reports.clear();
                        reports.addAll(latestReports);

                        posts.clear();
                        posts.addAll(latestAnnouncements);

                        if (!latestStatus.equals(status)) {
                            status = latestStatus;
                        }

                        // what is new?
                        Report changedReport = null;
                        for (Report r : reports) {
                            String before = oldReportStatus.get(r.id);
                            if (before != null && !before.equals(r.status)) {
                                changedReport = r;
                            }
                        }
                        Announcement newestPost = null;
                        for (Announcement a : posts) {
                            if (a.id > oldMaxPost && a.id > lastSeenAnnouncement
                                    && (newestPost == null || a.id > newestPost.id)) {
                                newestPost = a;
                            }
                        }

                        refreshAll();

                        // Only rebuild the bulletin board when its contents change.
                        // This keeps expanded posts open during ordinary 5-second checks.
                        if (!oldAnnouncementSnapshot.equals(announcementSnapshot(posts))
                                && announcementFeedView != null) {
                            announcementFeedView.refresh();
                        }

                        if (oldStatus != null && !oldStatus.equals(status)) {
                            statusChanged(status);
                        } else if (changedReport != null) {
                            toast("Report update", changedReport.ref() + " is now \"" + changedReport.status + "\".",
                                    ACCENT, "info");
                        } else if (newestPost != null && !"Announcements".equals(currentPage)) {
                            toast("New announcement", newestPost.title, GOLD, "bell");
                        }

                    } catch (Exception ex) {
                        System.err.println(
                                "Resident auto-refresh failed: "
                                + ex.getMessage());
                    } finally {
                        refreshing = false;
                    }
                }

            }.execute();
        });

        refreshTimer.start();
    }

    // ---------------------------------------------------------------- HOME
    private JComponent home(String name) {
        JComponent notice = dyn(this::verificationNotice);

        JComponent left = column(18,
                section("Latest report", "View details", () -> {
                    Report r = latestActive();
                    if (r != null) {
                        openReport(r);
                    }
                }, dyn(this::latestBody)),
                section("Recent reports", "View all", () -> showPage("My Reports"), dyn(this::recentBody)));
        JComponent right = column(18,
                section("Barangay updates", "See all", () -> showPage("Announcements"), dyn(this::updatesBody)),
                section("Something wrong?", null, dyn(this::helpBody)));

        JComponent banner = new Banner(greeting() + ", " + firstName(name) + "!",
                "Track your reports and stay updated with your barangay.");
        // top gap lines the banner up with the sidebar logo card (same 24 px as the other pages)
        JPanel bannerWrap = new JPanel(new BorderLayout());
        bannerWrap.setOpaque(false);
        bannerWrap.setBorder(new EmptyBorder(18, 0, 0, 0));
        bannerWrap.add(banner, BorderLayout.CENTER);

        return page(bannerWrap, notice, dyn(this::setupBody), twoCol(left, right, 3, 2));
    }

    private JComponent verificationNotice() {
        if ("REJECTED".equals(status)) {
            return notice(RED, "Verification was not approved",
                    "Visit the barangay hall with a valid ID. Staff can reconsider your registration after checking it.");
        }
        return null;   // pending residents see the setup card instead
    }

    /**
     * The newest report that is still open (Submitted, Viewed or In progress).
     */
    private Report latestActive() {
        for (Report r : mine()) {
            if (!r.isClosed()) {
                return r;
            }
        }
        return null;
    }

    private JComponent latestBody() {
        Report r = latestActive();
        if (r == null) {
            return emptyState("You have no active reports right now. Anything you file will be tracked here.");
        }
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(lbl(r.subject, Font.BOLD, 15, INK), BorderLayout.WEST);
        head.add(statusPill(r.status), BorderLayout.EAST);
        JComponent steps = new ProgressSteps(STEPS, stepOf(r.status));
        String line;
        if (r.notes.isEmpty()) {
            line = "Filed " + Fmt.date(r.filed) + "  -  waiting for barangay staff to review it.";
        } else {
            Note n = r.notes.get(r.notes.size() - 1);
            line = Fmt.date(n.date) + "  -  " + n.staff + ": " + n.text;
        }
        return column(8, head, steps, wrap(esc(line), 430, 12, MUTED));
    }

    private JComponent recentBody() {
        List<Report> mine = mine();
        List<Report> shown = mine.size() > 4 ? mine.subList(0, 4) : mine;
        if (shown.isEmpty()) {
            return emptyState("You haven't filed any reports yet.");
        }
        Object[][] rows = new Object[shown.size()][];
        for (int i = 0; i < rows.length; i++) {
            Report r = shown.get(i);
            rows[i] = new Object[]{r.subject, Fmt.date(r.filed), r.status};
        }
        return clickable(table(new String[]{"Concern", "Filed", "Status"}, rows, 2, 240, 100, 110),
                row -> openReport(shown.get(row)));
    }

    private JComponent updatesBody() {
        List<Announcement> list = sortedPosts();
        if (list.isEmpty()) {
            return emptyState("No announcements yet.");
        }
        int n = Math.min(3, list.size());
        JComponent[] rows = new JComponent[n];
        for (int i = 0; i < n; i++) {
            Announcement a = list.get(i);
            rows[i] = miniUpdate(a.title, Fmt.date(a.postedAt.toLocalDate()) + "  -  " + a.tag, tagColor(a.tag));
        }
        return column(0, rows);
    }

    private JComponent helpBody() {
        boolean v = isVerified();
        JButton report = primaryButton("Report a concern");
        report.setEnabled(v);
        report.setToolTipText(v ? null : "Available after your residency is verified");
        report.addActionListener(e -> showPage("Report a Concern"));
        JPanel help = new JPanel(new GridLayout(2, 1, 0, 12));
        help.setOpaque(false);
        help.add(wrap("See something that needs fixing? Let the barangay know.", 200, 13, BODY));
        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        b.setOpaque(false);
        b.add(report);
        help.add(b);
        return help;
    }

    /**
     * One line in "Barangay updates": a coloured dot for the topic, title and
     * date. Clicking opens Announcements.
     */
    private JComponent miniUpdate(String title, String sub, Color tag) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(9, 0, 9, 0)));
        JPanel dot = new JPanel(new GridBagLayout());
        dot.setOpaque(false);
        dot.add(new Dot(tag, 10, false));
        row.add(dot, BorderLayout.WEST);
        JPanel texts = new JPanel(new GridLayout(2, 1));
        texts.setOpaque(false);
        JLabel t = lbl(title, Font.BOLD, 13, INK);
        texts.add(t);
        texts.add(lbl(sub, Font.PLAIN, 12, MUTED));
        row.add(texts, BorderLayout.CENTER);
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showPage("Announcements");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                t.setForeground(ACCENT);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                t.setForeground(INK);
            }
        });
        return row;
    }

    // ------------------------------------------------- ANNOUNCEMENTS (BULLETIN BOARD)
    private JComponent announcements() {
        Dyn feed = dyn(this::feedBody);
        announcementFeedView = feed;
        String[][] topics = {{"All", null}, {"Events", "Event"}, {"Health", "Health"},
        {"Notices", "Notice"}, {"Reminders", "Reminder"}};
        JPanel chips = chipRow(topics, tag -> {
            topicFilter = tag;
            feed.refresh();
        });
        return page(chips, feed);
    }

    /**
     * Only barangay staff can post, so every card is shown as coming from
     * staff.
     */
    private JComponent feedBody() {
        List<JComponent> cards = new ArrayList<>();
        for (Announcement a : sortedPosts()) {
            if (topicFilter == null || topicFilter.equals(a.tag)) {
                cards.add(new PostCard(a, null));
            }
        }
        if (cards.isEmpty()) {
            return emptyState("There are no announcements here yet.");
        }
        return column(14, cards.toArray(new JComponent[0]));
    }

    // ------------------------------------------------------------ REPORT FORM
    private JComponent reportFormPage() {
        if (!isVerified()) {
            RoundedPanel locked = card();
            locked.setLayout(new BorderLayout(0, 10));
            locked.setBorder(new EmptyBorder(40, 30, 43, 30));
            JPanel icon = new JPanel(new GridBagLayout());
            icon.setOpaque(false);
            icon.add(new IconBubble("clock", AMBER, 56));
            JLabel t = heading("Verification needed", 18, INK);
            t.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel d = lbl("You'll be able to file reports once barangay staff confirm your residency.",
                    Font.PLAIN, 13, MUTED);
            d.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel pw = new JPanel(new GridBagLayout());
            pw.setOpaque(false);
            pw.add(statusPill("Awaiting"));
            JPanel text = new JPanel(new BorderLayout(0, 8));
            text.setOpaque(false);
            text.add(t, BorderLayout.NORTH);
            text.add(d, BorderLayout.CENTER);
            text.add(pw, BorderLayout.SOUTH);
            locked.add(icon, BorderLayout.NORTH);
            locked.add(text, BorderLayout.CENTER);
            return page(locked);
        }

        JComboBox<String> category = comboOf(CATEGORIES);
        JTextField subject = inputField();
        JTextField location = inputField();
        JTextArea details = inputArea(5);

        JButton clear = secondaryButton("Clear");
        JButton submit = primaryButton("Submit report");
        Runnable reset = () -> {
            category.setSelectedIndex(0);
            subject.setText("");
            location.setText("");
            details.setText("");
        };
        clear.addActionListener(e -> reset.run());
        submit.addActionListener(e -> {
            boolean ok = attempt(this, () -> submitReport(subject.getText(),
                    (String) category.getSelectedItem(), location.getText(), details.getText()));
            if (!ok) {
                return;
            }
            reset.run();
            showingHistory = false;
            reportsView.refresh();
            showPage("My Reports");
            toast("Report submitted", "Barangay staff will review it shortly.", GREEN, "check");
        });

        JComponent form = column(14,
                labeled("Category", category),
                labeled("What is the concern about?", subject),
                labeled("Where is it happening?", location),
                labeled("Tell us more", scrollOf(details)),
                buttons(clear, submit));

        JComponent tips = column(10,
                wrap("<b>Be specific.</b> Name the street, purok or landmark.", 200, 13, BODY),
                wrap("<b>Stay factual.</b> Describe what happened and when.", 200, 13, BODY),
                wrap("<b>Edit while waiting.</b> You can edit or delete a report until staff accept it.",
                        200, 13, BODY));

        return page(twoCol(section("New report", null, form), section("Writing tips", null, tips), 3, 2));
    }

    // -------------------------------------------------------------- MY REPORTS
    private JComponent myReports() {
        reportsView = dyn(this::reportsViewBody);
        RoundedPanel card = card();
        card.add(reportsView, BorderLayout.CENTER);
        return page(card);
    }

    /**
     * Active list, or the report history, depending on the toggle.
     */
    private JComponent reportsViewBody() {
        List<Report> active = new ArrayList<>(), closed = new ArrayList<>();
        for (Report r : mine()) {
            (r.isClosed() ? closed : active).add(r);
        }

        if (showingHistory) {
            JButton back = secondaryButton("\u2190  Back to active reports");
            back.addActionListener(e -> {
                showingHistory = false;
                reportsView.refresh();
            });
            JComponent body = closed.isEmpty()
                    ? emptyState("No closed reports yet. Resolved or declined reports will appear here.")
                    : clickable(table(new String[]{"Concern", "Category", "Filed", "Closed on", "Status"},
                    rowsOf(closed, true), 4, 260, 120, 100, 100, 110), row -> openReport(closed.get(row)));
            // title on the left and the button on the same row, vertically centred
            return column(16,
                    toolbar(titleBlock("Report history",
                            lbl(closed.size() + " closed reports (resolved or declined)", Font.PLAIN, 12, MUTED)),
                            back),
                    body);
        }

        JButton viewHistory = secondaryButton("View Report History");
        JButton newReport = primaryButton("New report");
        newReport.addActionListener(e -> showPage("Report a Concern"));
        viewHistory.addActionListener(e -> {
            showingHistory = true;
            reportsView.refresh();
        });
        JComponent body = active.isEmpty()
                ? emptyState("You have no active reports. Tap \"New report\" to file one.")
                : clickable(table(new String[]{"Concern", "Category", "Filed", "Last update", "Status"},
                rowsOf(active, false), 4, 260, 120, 100, 100, 110), row -> openReport(active.get(row)));
        return column(16,
                toolbar(titleBlock("Active reports", lbl("Reports that are waiting or being handled by barangay staff.",
                        Font.PLAIN, 12, MUTED)),
                        tight(newReport, viewHistory)),
                body);
    }

    private static Object[][] rowsOf(List<Report> list, boolean closedView) {
        Object[][] rows = new Object[list.size()][];
        for (int i = 0; i < rows.length; i++) {
            Report r = list.get(i);
            rows[i] = new Object[]{r.subject, r.category, Fmt.date(r.filed),
                Fmt.date(closedView ? r.closedOn : r.updated), r.status};
        }
        return rows;
    }

    // ------------------------------------------------------------ REPORT DETAILS (pop-up)
    private void openReport(Report r) {
        if (r == null) {
            return;
        }
        JDialog d = new JDialog(this, "Report " + r.ref(), true);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        showIn(d, detailView(d, r), this);
        d.setVisible(true);
    }

    /**
     * All the information about one report, with Edit / Delete (only while it
     * is Submitted).
     */
    private JComponent detailView(JDialog d, Report r) {
        List<JComponent> blocks = new ArrayList<>(reportBlocks(r, false));
        boolean canEdit = r.canResidentEdit();
        if (!canEdit) {
            blocks.add(notice(ACCENT, "Editing is locked",
                    r.isClosed() ? "This report is closed, so it can no longer be edited or deleted."
                    : "Barangay staff already accepted this report, so you can no longer edit or delete it."));
        }

        JButton delete = dangerButton("Delete report");
        JButton edit = secondaryButton("Edit report");
        JButton close = primaryButton("Close");
        delete.setEnabled(canEdit);
        edit.setEnabled(canEdit);
        if (!canEdit) {
            delete.setToolTipText("Only pending reports can be deleted");
            edit.setToolTipText("Only pending reports can be edited");
        }
        delete.addActionListener(e -> {
            boolean ok = confirm(d, "report", RED, "Delete this report?",
                    "Report " + r.ref() + " will be permanently removed. This cannot be undone.",
                    "Delete report", "Keep it", true);
            if (ok && attempt(d, () -> deleteReport(r))) {
                d.dispose();
                toast("Report deleted", "Report " + r.ref() + " was removed.", ACCENT, "info");
            }
        });
        edit.addActionListener(e -> showIn(d, editView(d, r), this));
        close.addActionListener(e -> d.dispose());

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(delete);
        actions.add(left, BorderLayout.WEST);
        actions.add(buttons(edit, close), BorderLayout.EAST);
        blocks.add(actions);

        return column(18, blocks.toArray(new JComponent[0]));
    }

    /**
     * Edit form for a pending report. The data layer re-checks that the report
     * is still Submitted.
     */
    private JComponent editView(JDialog d, Report r) {
        JComboBox<String> category = comboOf(CATEGORIES);
        category.setSelectedItem(r.category);
        JTextField subject = inputField();
        subject.setText(r.subject);
        JTextField location = inputField();
        location.setText(r.location);
        JTextArea details = inputArea(5);
        details.setText(r.details);

        JButton cancel = secondaryButton("Cancel");
        JButton save = primaryButton("Save changes");
        cancel.addActionListener(e -> showIn(d, detailView(d, r), this));
        save.addActionListener(e -> {
            boolean ok = attempt(d, () -> editReport(r, subject.getText(),
                    (String) category.getSelectedItem(), location.getText(), details.getText()));
            if (ok) {
                d.dispose();
                toast("Changes saved", "Report " + r.ref() + " was updated.", GREEN, "check");
            }
        });

        return column(16,
                column(4, lbl("EDIT REPORT " + r.ref(), Font.BOLD, 11, MUTED),
                        heading("Update your report", 21, INK),
                        lbl("You can make changes until barangay staff accept your report.", Font.PLAIN, 12, MUTED)),
                labeled("Category", category),
                labeled("What is the concern about?", subject),
                labeled("Where is it happening?", location),
                labeled("Tell us more", scrollOf(details)),
                buttons(cancel, save));
    }

    // ------------------------------------------------------------ MY PROFILE
    private JComponent profile() {
        profileHeaderView = dyn(this::profileHeader);
        pPurok = inputField();
        pContact = inputField();
        pBirth = inputField();
        pAddress = inputArea(3);
        restrictInput(pContact, BaseDashboard::isDigit, 11);
        restrictInput(pBirth, cp -> isDigit(cp) || cp == '-', 10);
        restrictInput(pAddress, BaseDashboard::isAddressChar, 200);

        JButton discard = secondaryButton("Discard changes");
        JButton save = primaryButton("Save changes");
        discard.addActionListener(e -> {
            profileLoaded = false;
            loadProfileOnce();
        });
        save.addActionListener(e -> {
            boolean ok = attempt(this, () -> saveProfile(pPurok.getText(), pContact.getText(), pAddress.getText(), pBirth.getText()));
            if (ok) {
                boolean first = !setupDone;
                setupDone = true;
                refreshAll();
                if (first && !isVerified()) {
                    toast("Profile completed", "Staff can now verify your account.", GREEN, "check");
                } else {
                    toast("Profile updated", "Your details were saved.", GREEN, "check");
                }
            }
        });

        JComponent form = column(14,
                labeled("Purok / zone *", pPurok),
                labeled("Contact number *", pContact),
                labeled("Birth date * (YYYY-MM-DD)", pBirth),
                labeled("Home address *", scrollOf(pAddress)),
                buttons(discard, save));

        Dyn accountFacts = dyn(this::accountFacts);
        accountFacts.setOpaque(false);
        JComponent account = column(14,
                accountFacts,
                wrap("Your name and verification are managed by barangay staff. "
                        + "To correct them, please visit the barangay hall with a valid ID.", 230, 13, BODY));

        return page(dyn(this::setupBody), profileHeaderView,
                twoCol(section("Personal details", null, form), section("Account", null, account), 3, 2));
    }

    /**
     * Name, email and role shown in the Account card.
     */
    private JComponent accountFacts() {
        String email = loadResidentColumn("email").trim();
        return factBox(new String[][]{
            {"Name", userName},
            {"Email", email.isEmpty() ? "Not provided" : email},
            {"Role", "Resident"}});
    }

    private JComponent profileHeader() {
        RoundedPanel c = card();
        c.setLayout(new BorderLayout(22, 0));
        c.setBorder(new EmptyBorder(24, 28, 26, 28));

        JPanel av = new JPanel(new GridBagLayout());
        av.setOpaque(false);
        av.add(new Avatar(userName, 88));
        c.add(av, BorderLayout.WEST);

        String s = status;
        String pillText = "VERIFIED".equals(s) ? "Verified" : "REJECTED".equals(s) ? "Declined" : "Awaiting";
        String note = "VERIFIED".equals(s) ? "Your residency is confirmed. You can file reports."
                : "REJECTED".equals(s) ? "Visit the barangay hall with a valid ID so staff can reconsider."
                : !setupDone ? "Complete your details so staff can verify you."
                        : "Barangay staff are confirming your residency.";
        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pillRow.setOpaque(false);
        pillRow.add(statusPill(pillText));
        pillRow.add(Box.createHorizontalStrut(12));
        pillRow.add(lbl(note, Font.PLAIN, 13, MUTED));

        JPanel texts = new JPanel(new GridBagLayout());
        texts.setOpaque(false);
        texts.add(column(8, heading(userName, 26, INK),
                lbl("Resident of the barangay", Font.PLAIN, 14, BODY), pillRow),
                new GridBagConstraints() {
            {
                weightx = 1;
                fill = GridBagConstraints.HORIZONTAL;
            }
        });
        c.add(texts, BorderLayout.CENTER);
        return c;
    }

    /**
     * Fills the profile form once per visit.
     */
    private void loadProfileOnce() {
        if (profileLoaded || userId <= 0) {
            return;
        }
        profileLoaded = true;
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                return new String[]{
                    loadResidentColumn("purok"),
                    loadResidentColumn("contact_number"),
                    loadResidentColumn("address"),
                    loadResidentColumn("birth_date")};
            }

            @Override
            protected void done() {
                try {
                    String[] v = get();
                    pPurok.setText(v[0]);
                    pContact.setText(v[1]);
                    pAddress.setText(v[2]);
                    pBirth.setText(v[3]);
                } catch (Exception ex) {
                    System.err.println("Profile load failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Reads one column of this resident's row. Only called with our own
     * constant column names (never user input). A missing column just leaves
     * the field blank.
     */
    private String loadResidentColumn(String column) {
        String sql = "SELECT " + column + " FROM users WHERE user_id = ?";
        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return v == null ? "" : v;
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Profile: could not read " + column + ": " + e.getMessage());
        }
        return "";
    }

    private void saveProfile(String purok, String contact, String address, String birth) {

        if (userId <= 0) {
            throw new IllegalStateException(
                    "Please log in with your resident account.");
        }

        String validPurok = need(purok, "purok");
        String validContact = need(contact, "contact number");
        if (!isValidContactNumber(validContact)) {
            throw new IllegalArgumentException(CONTACT_RULE);
        }
        String validAddress = need(address, "home address").replaceAll("\\s+", " ");
        if (!isValidAddress(validAddress)) {
            throw new IllegalArgumentException(ADDRESS_RULE);
        }

        java.time.LocalDate validBirth;
        try {
            validBirth = java.time.LocalDate.parse(need(birth, "birth date"));
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Birth date must be a real date written as YYYY-MM-DD, for example 1995-03-27.");
        }
        if (validBirth.isAfter(java.time.LocalDate.now())
                || validBirth.isBefore(java.time.LocalDate.of(1900, 1, 1))) {
            throw new IllegalArgumentException("Please enter a valid birth date.");
        }

        String sql = "UPDATE users SET purok = ?, contact_number = ?, address = ?, birth_date = ? "
                + "WHERE user_id = ? AND role = 'RESIDENT'";

        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt
                = conn.prepareStatement(sql)) {

            stmt.setString(1, validPurok);
            stmt.setString(2, validContact);
            stmt.setString(3, validAddress);
            stmt.setDate(4, java.sql.Date.valueOf(validBirth));
            stmt.setInt(5, userId);

            stmt.executeUpdate();

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Failed to update profile: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------ DATABASE ACTIONS
    // These operations use the MySQL database.
    private List<Report> mine() {
        List<Report> out = new ArrayList<>();
        for (Report r : reports) {
            if (r.residentId == userId) {
                out.add(r);
            }
        }
        out.sort((a, b) -> {
            int c = b.filed.compareTo(a.filed);
            return c != 0 ? c : Integer.compare(b.id, a.id);
        });
        return out;
    }

    private List<Announcement> sortedPosts() {
        List<Announcement> out = new ArrayList<>(posts);
        out.sort((a, b) -> a.pinned != b.pinned ? (a.pinned ? -1 : 1) : b.postedAt.compareTo(a.postedAt));
        return out;
    }

    private static String need(String value, String what) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Please fill in the " + what + ".");
        }
        return value.trim();
    }

    private void submitReport(String subject, String category,
            String location, String details) {

        if (!isVerified()) {
            throw new IllegalStateException(
                    "Your residency must be verified before you can file reports.");
        }

        String validSubject = need(subject, "concern");
        String validLocation = need(location, "location");
        String validDetails = need(details, "details");

        if (userId <= 0) {
            throw new IllegalStateException(
                    "Please log in with your resident account.");
        }

        String sql = "INSERT INTO reports "
                + "(resident_id, subject, category, location, details, "
                + "status, filed_on, updated_on) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        LocalDate today = LocalDate.now();

        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt
                = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, validSubject);
            stmt.setString(3, category);
            stmt.setString(4, validLocation);
            stmt.setString(5, validDetails);
            stmt.setString(6, "Submitted");
            stmt.setDate(7, java.sql.Date.valueOf(today));
            stmt.setDate(8, java.sql.Date.valueOf(today));

            stmt.executeUpdate();

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Failed to save report to MySQL: " + e.getMessage(), e);
        }

        reports.clear();
        reports.addAll(BaseDashboard.loadResidentReports(userId));
        refreshAll();
    }

    private void editReport(Report r, String subject,
            String category, String location, String details) {

        if (userId <= 0) {
            throw new IllegalStateException(
                    "Please log in with your resident account.");
        }

        String validSubject = need(subject, "concern");
        String validLocation = need(location, "location");
        String validDetails = need(details, "details");

        String sql = "UPDATE reports SET "
                + "subject = ?, category = ?, location = ?, "
                + "details = ?, updated_on = CURDATE() "
                + "WHERE report_id = ? AND resident_id = ? "
                + "AND status = 'Submitted'";

        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt
                = conn.prepareStatement(sql)) {

            stmt.setString(1, validSubject);
            stmt.setString(2, category);
            stmt.setString(3, validLocation);
            stmt.setString(4, validDetails);
            stmt.setInt(5, r.id);
            stmt.setInt(6, userId);

            int updated = stmt.executeUpdate();

            if (updated == 0) {
                throw new IllegalStateException(
                        "This report can no longer be edited. "
                        + "It may have already been accepted by staff.");
            }

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Failed to update report: " + e.getMessage(), e);
        }

        reports.clear();
        reports.addAll(BaseDashboard.loadResidentReports(userId));
        refreshAll();
    }

    private void deleteReport(Report r) {

        if (userId <= 0) {
            throw new IllegalStateException(
                    "Please log in with your resident account.");
        }

        String sql = "DELETE FROM reports "
                + "WHERE report_id = ? AND resident_id = ? "
                + "AND status = 'Submitted'";

        try (java.sql.Connection conn = Db.get();
                java.sql.PreparedStatement stmt
                = conn.prepareStatement(sql)) {

            stmt.setInt(1, r.id);
            stmt.setInt(2, userId);

            int deleted = stmt.executeUpdate();

            if (deleted == 0) {
                throw new IllegalStateException(
                        "This report can no longer be deleted. "
                        + "It may have already been accepted by staff.");
            }

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Unable to delete report: " + e.getMessage(), e);
        }

        reports.clear();
        reports.addAll(BaseDashboard.loadResidentReports(userId));

        refreshAll();
    }

    /**
     * Always start from the sign-in page (no test account), even when this file
     * is the one being run.
     */
    public static void main(String[] args) {
        Login.main(args);
    }
}
