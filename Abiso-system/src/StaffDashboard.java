import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class StaffDashboard extends BaseDashboard {

    /**
     * Label shown for staff on their profile and on announcements they post.
     */
    private static final String STAFF_ROLE = "Barangay Staff";
    private final int staffId;

    private final List<Report> reports = loadReports();
    private final List<Resident> residents = loadResidents();
    private final List<Announcement> posts = loadAnnouncements();
    /**
     * Residents who finished setting up their profile. null = could not tell,
     * so nobody is blocked.
     */
    private volatile java.util.Set<Integer> completeIds = loadCompleteIds();

    private final List<Dyn> dyns = new ArrayList<>();   // every live section; redrawn when data changes
    private javax.swing.Timer refreshTimer;
    private boolean refreshing = false;
    private String reportFilter = null;                   // Reports page filter (null = all)
    private String reportQuery = "";                      // Reports page search text
    private String residentFilter = null;                 // Residents page filter (null = all)
    private String residentQuery = "";                    // Residents page search text
    private String topicFilter = null;                    // Announcements page filter (null = all)

    // ---- UI-only state ----
    private String currentPage = "Overview";
    private final String staffPosition = STAFF_ROLE;      // shown on the profile and on new posts
    private Dyn profileHeaderView;
    private Dyn announcementFeedView;
    private JTextField sContact;                          // My Profile form field
    private boolean profileLoaded = false;

    private Dyn dyn(Supplier<JComponent> source) {
        Dyn d = new Dyn(source);
        dyns.add(d);
        return d;
    }

    // ================================================================ CONSTRUCTOR
    public StaffDashboard(int staffId, String name) {
        super("Barangay Staff", "Barangay Staff", name);
        this.staffId = staffId;

        addPage("Overview", "overview", "", "", overview(name));   // empty heading = Overview builds its own banner
        addPage("Residents", "users", "Residents",
                "Confirm who lives in the barangay before they can file reports.", residents());
        addPage("Reports", "list", "Reports",
                "Tap a report to review it, change its status and send a note to the resident.", reports());
        addPage("Announcements", "megaphone", "Announcements",
                "Post news and notices. Residents see them on their bulletin board.", announcements());
        // no menu item: it opens from the user card at the bottom of the sidebar
        addHiddenPage("My Profile", "My Profile",
                "Manage your details and check your account information.", profile());
        updateBadges();
        startAutoRefresh();
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
    protected void onPageOpened(String menuName) {
        currentPage = menuName;
        if ("My Profile".equals(menuName)) {
            loadProfileOnce();
        }
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.dispose();
    }

    private void refreshAll() {
        refreshAll(false);
    }

    private void refreshAll(boolean preserveOpenAnnouncements) {
        for (Dyn d : dyns) {
            // Keep expanded announcements open during the timed refresh.
            // Staff edits, posts, and deletions still update the feed immediately.
            if (preserveOpenAnnouncements && d == announcementFeedView && d.isShowing()) {
                continue;
            }
            d.refresh();
        }
        updateBadges();
    }

    // Detect additions, edits, and deletions without redrawing unchanged posts.
    private String announcementSnapshot(List<Announcement> announcements) {
        StringBuilder result = new StringBuilder();
        List<Announcement> ordered = new ArrayList<>(announcements);
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

            if (refreshing || !isDisplayable()) {
                return;
            }

            refreshing = true;

            new SwingWorker<List<?>[], Void>() {

                @Override
                protected List<?>[] doInBackground() throws Exception {

                    List<Report> latestReports = loadReports();
                    List<Resident> latestResidents = loadResidents();
                    List<Announcement> latestAnnouncements
                            = loadAnnouncements();

                    completeIds = loadCompleteIds();

                    return new List<?>[]{
                        latestReports,
                        latestResidents,
                        latestAnnouncements
                    };
                }

                @Override
                protected void done() {
                    try {
                        List<?>[] data = get();

                        @SuppressWarnings("unchecked")
                        List<Report> latestReports
                                = (List<Report>) data[0];

                        @SuppressWarnings("unchecked")
                        List<Resident> latestResidents
                                = (List<Resident>) data[1];

                        @SuppressWarnings("unchecked")
                        List<Announcement> latestAnnouncements
                                = (List<Announcement>) data[2];

                        // remember what we already knew, so we can tell what is new
                        java.util.Set<Integer> oldReportIds = new java.util.HashSet<>();
                        for (Report r : reports) {
                            oldReportIds.add(r.id);
                        }
                        java.util.Set<Integer> oldResidentIds = new java.util.HashSet<>();
                        for (Resident r : residents) {
                            oldResidentIds.add(r.userId);
                        }

                        String previousAnnouncements = announcementSnapshot(posts);

                        reports.clear();
                        reports.addAll(latestReports);

                        residents.clear();
                        residents.addAll(latestResidents);

                        posts.clear();
                        posts.addAll(latestAnnouncements);

                        Report newReport = null;
                        for (Report r : reports) {
                            if (!oldReportIds.contains(r.id) && "Submitted".equals(r.status)
                                    && (newReport == null || r.id > newReport.id)) {
                                newReport = r;
                            }
                        }
                        Resident newResident = null;
                        for (Resident r : residents) {
                            if (!oldResidentIds.contains(r.userId) && "PENDING".equals(r.status)) {
                                newResident = r;
                            }
                        }

                        refreshAll(true);
                        // Refresh the bulletin board only if an announcement changed.
                        // Normal 5-second checks leave expanded cards untouched.
                        if (!previousAnnouncements.equals(announcementSnapshot(posts))
                                && announcementFeedView != null
                                && announcementFeedView.isShowing()) {
                            announcementFeedView.refresh();
                        }

                        if (newReport != null && !"Reports".equals(currentPage)) {
                            toast("New report", newReport.ref() + ": " + newReport.subject, GOLD, "bell");
                        } else if (newResident != null && !"Residents".equals(currentPage)) {
                            toast("New registration", newResident.name + " is waiting for verification.",
                                    GOLD, "bell");
                        }

                    } catch (Exception ex) {
                        System.err.println(
                                "Auto-refresh failed: " + ex.getMessage());
                    } finally {
                        refreshing = false;
                    }
                }
            }.execute();
        });

        refreshTimer.start();
    }

    private void updateBadges() {
        int waiting = 0, pending = 0;
        for (Report r : reports) {
            if ("Submitted".equals(r.status)) {
                waiting++;
            }
        }
        for (Resident r : residents) {
            if ("PENDING".equals(r.status)) {
                pending++;
            }
        }
        setBadge("Reports", waiting);
        setBadge("Residents", pending);
    }

    // ================================================================ OVERVIEW
    private JComponent overview(String name) {
        JComponent banner = new Banner(greeting() + ", " + firstName(name) + "!",
                "Here's what needs your attention today.");
        // top gap lines the banner up with the sidebar logo (same 24 px as the other pages)
        JPanel bannerWrap = new JPanel(new BorderLayout());
        bannerWrap.setOpaque(false);
        bannerWrap.setBorder(new EmptyBorder(18, 0, 0, 0));
        bannerWrap.add(banner, BorderLayout.CENTER);

        JButton post = primaryButton("Post an announcement");
        post.addActionListener(e -> {
            showPage("Announcements");
            openAnnouncementForm(null);
        });
        JButton review = secondaryButton("Review new reports");
        review.addActionListener(e -> showPage("Reports"));
        JPanel quick = new JPanel(new GridLayout(2, 1, 0, 10));
        quick.setOpaque(false);
        quick.add(post);
        quick.add(review);

        JComponent left = column(18,
                section("Reports needing attention", "View all", () -> showPage("Reports"), dyn(this::attentionBody)),
                section("Reports by category", null, dyn(this::categoriesBody)));
        JComponent right = column(18,
                section("Residents to verify", "See all", () -> showPage("Residents"), dyn(this::verifyBody)),
                section("Quick actions", null, quick));

        return page(bannerWrap, twoCol(left, right, 3, 2));
    }

    /**
     * Short date such as "Oct 11" (used where space is tight).
     */
    private static String shortDate(LocalDate d) {
        return d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH));
    }

    private JComponent attentionBody() {
        List<Report> open = new ArrayList<>();
        for (Report r : sortedReports()) {
            if (!r.isClosed()) {
                open.add(r);
            }
        }
        List<Report> shown = open.size() > 6 ? open.subList(0, 6) : open;
        if (shown.isEmpty()) {
            return emptyState("Nothing needs attention. Every report has been handled.");
        }
        Object[][] rows = new Object[shown.size()][];
        for (int i = 0; i < rows.length; i++) {
            Report r = shown.get(i);
            rows[i] = new Object[]{r.ref(), r.subject, shortDate(r.filed), r.status};
        }
        return clickable(table(new String[]{"Ref", "Concern", "Filed", "Status"}, rows, 3, 80, 190, 70, 120),
                row -> openReview(shown.get(row)));
    }

    private JComponent categoriesBody() {
        String[] cats = CATEGORIES;
        int[] counts = new int[cats.length];
        int total = 0;
        for (Report r : sortedReports()) {
            for (int i = 0; i < cats.length; i++) {
                if (cats[i].equals(r.category)) {
                    counts[i]++;
                    total++;
                }
            }
        }
        if (total == 0) {
            return emptyState("No reports have been filed yet.");
        }
        return new HBars(cats, counts, new Color[]{GREEN, AMBER, PURPLE, ACCENT, TEAL});
    }

    private JComponent verifyBody() {
        List<JComponent> rows = new ArrayList<>();
        for (Resident r : sortedResidents()) {
            if (!"PENDING".equals(r.status) || rows.size() >= 3) {
                continue;
            }
            JButton review = smallButton("Review", ACCENT);
            review.addActionListener(e -> showPage("Residents"));
            rows.add(personRow(r.name, residentNo(r) + "  -  " + r.purok, review, () -> openResident(r, this)));
        }
        if (rows.isEmpty()) {
            return emptyState("No residents are waiting for verification.");
        }
        return column(0, rows.toArray(new JComponent[0]));
    }

    // ================================================================ RESIDENTS
    private JComponent residents() {
        Dyn list = dyn(this::residentList);
        JComponent search = searchField("Search by name, resident no. or purok",
                q -> {
                    residentQuery = q;
                    list.refresh();
                });
        String[][] filters = {{"All", null}, {"Pending", "PENDING"}, {"Verified", "VERIFIED"}, {"Declined", "REJECTED"}};
        JPanel chips = chipRow(filters, s -> {
            residentFilter = s;
            list.refresh();
        });
        return page(section("Residents", null, column(14, search, chips, list)));
    }

    private JComponent residentList() {
        List<Resident> all = new ArrayList<>();
        for (Resident r : sortedResidents()) {
            if ((residentFilter == null || residentFilter.equals(r.status)) && matches(residentQuery, r.name, r.purok, residentNo(r))) {
                all.add(r);
            }
        }
        if (all.isEmpty()) {
            return emptyState("No residents match your search or filter.");
        }
        JComponent[] rows = new JComponent[all.size()];
        for (int i = 0; i < rows.length; i++) {
            Resident r = all.get(i);
            if ("PENDING".equals(r.status)) {
                JButton decline = smallButton("Decline", RED);
                JButton approve = smallButton("Approve", GREEN);
                boolean ready = isReady(r);
                approve.setEnabled(ready);
                if (!ready) {
                    approve.setToolTipText("This resident has not finished setting up their profile yet");
                }
                decline.addActionListener(e -> {
                    boolean ok = confirm(this, "users", RED, "Decline this resident?",
                            r.name + "'s registration will be marked as not approved.",
                            "Decline", "Cancel", true);
                    if (ok && attempt(this, () -> decideResident(r, false))) {
                        toast("Resident declined", r.name + " was not approved.", ACCENT, "info");
                    }
                });
                approve.addActionListener(e -> {
                    if (attempt(this, () -> decideResident(r, true))) {
                        toast("Resident approved", r.name + " can now file reports.", GREEN, "check");
                    }
                });
                rows[i] = personRow(r.name, residentNo(r) + "  -  " + r.purok + (ready ? "  -  registered " : "  -  setting up profile  -  registered ")
                        + Fmt.date(r.registered),
                        buttons(decline, approve), () -> openResident(r, this));
            } else if ("VERIFIED".equals(r.status)) {
                rows[i] = personRow(r.name, residentNo(r) + "  -  " + r.purok + "  -  verified " + Fmt.date(r.decidedOn),
                        statusPill("Verified"), () -> openResident(r, this));
            } else {
                JButton reconsider = smallButton("Reconsider", ACCENT);
                reconsider.addActionListener(e -> reconsiderFlow(r, this));
                rows[i] = personRow(r.name, residentNo(r) + "  -  " + r.purok + "  -  not approved",
                        buttons(reconsider, statusPill("Declined")), () -> openResident(r, this));
            }
        }
        return column(0, rows);
    }

    // ================================================================ REPORTS
    private JComponent reports() {
        Dyn list = dyn(this::reportsTable);
        JComponent search = searchField("Search by reference, concern, category or resident",
                q -> {
                    reportQuery = q;
                    list.refresh();
                });
        String[][] filters = {{"All", null}, {"Submitted", "Submitted"}, {"Viewed", "Viewed"},
        {"In progress", "In progress"}, {"Resolved", "Resolved"}, {"Declined", "Declined"}};
        JPanel chips = chipRow(filters, status -> {
            reportFilter = status;
            list.refresh();
        });
        return page(section("All reports", null, column(14, search, chips, list)));
    }

    private JComponent reportsTable() {
        List<Report> shown = new ArrayList<>();
        for (Report r : sortedReports()) {
            if ((reportFilter == null || reportFilter.equals(r.status))
                    && matches(reportQuery, r.ref(), r.subject, r.category, r.resident)) {
                shown.add(r);
            }
        }
        if (shown.isEmpty()) {
            return emptyState("No reports match your search or filter.");
        }
        Object[][] rows = new Object[shown.size()][];
        for (int i = 0; i < rows.length; i++) {
            Report r = shown.get(i);
            rows[i] = new Object[]{r.ref(), r.subject, r.category, r.resident, Fmt.date(r.filed), r.status};
        }
        return clickable(table(new String[]{"Ref", "Concern", "Category", "Resident", "Filed", "Status"},
                rows, 5, 80, 210, 120, 130, 120, 120), row -> openReview(shown.get(row)));
    }

    // ------------------------------------------------------------ REVIEW POP-UP
    private void openReview(Report r) {
        if (r == null) {
            return;
        }
        JDialog d = new JDialog(this, "Review " + r.ref(), true);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        showIn(d, reviewView(d, r), this);
        d.setVisible(true);
    }

    /**
     * Full report details plus the controls to change its status and notify the
     * resident.
     */
    private JComponent reviewView(JDialog d, Report r) {
        List<JComponent> blocks = new ArrayList<>(reportBlocks(r, true));
        blocks.add(1, residentLink(r, d));   // clickable resident name under the title

        JComboBox<String> status = comboOf(STATUSES);
        status.setSelectedItem(r.status);
        JTextArea note = inputArea(3);

        // one-click starting points for the note
        String[][] templates = {
            {"Accepted", "Report accepted. Barangay staff are now working on it."},
            {"Site visit", "A staff member will visit the area this week."},
            {"Need details", "Please visit the barangay hall with more details about this report."},
            {"Resolved", "The concern has been addressed. Thank you for reporting it."}};
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        quick.setOpaque(false);
        for (int i = 0; i < templates.length; i++) {
            String[] t = templates[i];
            JButton b = smallButton(t[0], ACCENT);
            b.addActionListener(e -> {
                String cur = note.getText().trim();
                note.setText(cur.isEmpty() ? t[1] : cur + " " + t[1]);
                note.requestFocusInWindow();
            });
            if (i > 0) {
                quick.add(Box.createHorizontalStrut(8));
            }
            quick.add(b);
        }

        blocks.add(heading("Update this report", 15, INK));
        blocks.add(labeled("Status", status));
        blocks.add(labeled("Note to the resident (optional, required when declining)",
                column(8, quick, scrollOf(note))));

        JButton close = secondaryButton("Close");
        JButton save = primaryButton("Save update");
        close.addActionListener(e -> d.dispose());
        save.addActionListener(e -> {
            boolean ok = attempt(d, () -> updateReport(r,
                    (String) status.getSelectedItem(), note.getText()));
            if (ok) {
                d.dispose();
                toast("Report updated", r.ref() + " is now \"" + r.status + "\".", GREEN, "check");
            }
        });
        blocks.add(buttons(close, save));

        return column(18, blocks.toArray(new JComponent[0]));
    }

    // ================================================================ ANNOUNCEMENTS
    private JComponent announcements() {
        Dyn feed = dyn(this::staffFeed);
        announcementFeedView = feed;
        String[][] topics = {{"All", null}, {"Events", "Event"}, {"Health", "Health"},
        {"Notices", "Notice"}, {"Reminders", "Reminder"}};
        JPanel chips = chipRow(topics, tag -> {
            topicFilter = tag;
            feed.refresh();
        });
        JButton add = primaryButton("New announcement");
        add.addActionListener(e -> openAnnouncementForm(null));
        return page(toolbar(chips, add), feed);
    }

    private JComponent staffFeed() {
        List<JComponent> cards = new ArrayList<>();
        for (Announcement a : sortedPosts()) {
            if (topicFilter != null && !topicFilter.equals(a.tag)) {
                continue;
            }
            JButton edit = smallButton("Edit", ACCENT);
            JButton delete = smallButton("Delete", RED);
            edit.addActionListener(e -> openAnnouncementForm(a));
            delete.addActionListener(e -> {
                boolean ok = confirm(this, "megaphone", RED, "Delete this announcement?",
                        "\"" + a.title + "\" will no longer be visible to residents.",
                        "Delete", "Keep it", true);
                if (ok && attempt(this, () -> deleteAnnouncement(a))) {
                    toast("Announcement deleted", "It was removed from the bulletin board.", ACCENT, "info");
                }
            });
            cards.add(new PostCard(a, tight(edit, delete)));
        }
        if (cards.isEmpty()) {
            return emptyState("There are no announcements here yet.");
        }
        return column(14, cards.toArray(new JComponent[0]));
    }

    /**
     * New announcement (existing == null) or edit an existing one.
     */
    private void openAnnouncementForm(Announcement existing) {
        JDialog d = new JDialog(this, existing == null ? "New announcement" : "Edit announcement", true);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JComboBox<String> tag = comboOf(TAGS);
        JTextField title = inputField(), summary = inputField(), when = inputField(), where = inputField();
        JTextArea full = inputArea(5);
        JCheckBox pin = new JCheckBox("Pin to the top of the bulletin board");
        pin.setOpaque(false);
        pin.setFont(font(Font.PLAIN, 13));
        List<String[]> keep = new ArrayList<>();   // other details (Who, Contact...) stay as they were

        if (existing != null) {
            tag.setSelectedItem(existing.tag);
            title.setText(existing.title);
            summary.setText(existing.summary);
            full.setText(existing.fullText);
            pin.setSelected(existing.pinned);
            for (String[] f : existing.facts) {
                if ("When".equals(f[0])) {
                    when.setText(f[1]);
                } else if ("Where".equals(f[0])) {
                    where.setText(f[1]);
                } else {
                    keep.add(f);
                }
            }
        }

        JButton cancel = secondaryButton("Cancel");
        JButton save = primaryButton(existing == null ? "Post announcement" : "Save changes");
        cancel.addActionListener(e -> d.dispose());
        save.addActionListener(e -> {
            List<String[]> facts = new ArrayList<>();
            if (!when.getText().trim().isEmpty()) {
                facts.add(new String[]{"When", when.getText().trim()});
            }
            if (!where.getText().trim().isEmpty()) {
                facts.add(new String[]{"Where", where.getText().trim()});
            }
            facts.addAll(keep);
            String[][] arr = facts.toArray(new String[0][]);
            String tg = (String) tag.getSelectedItem();
            boolean ok = existing == null
                    ? attempt(d, () -> postAnnouncement(tg, title.getText(), summary.getText(),
                    full.getText(), arr, pin.isSelected()))
                    : attempt(d, () -> updateAnnouncement(existing, tg, title.getText(), summary.getText(),
                    full.getText(), arr, pin.isSelected()));
            if (ok) {
                d.dispose();
                if (existing == null) {
                    toast("Announcement posted", "It is now on the bulletin board.", GREEN, "check");
                } else {
                    toast("Announcement updated", "Your changes were saved.", GREEN, "check");
                }
            }
        });

        JComponent form = column(14,
                column(4, heading(existing == null ? "New announcement" : "Edit announcement", 21, INK),
                        lbl("Residents will see this on their bulletin board.", Font.PLAIN, 12, MUTED)),
                labeled("Category", tag),
                labeled("Title", title),
                labeled("Short summary (shown before the post is opened)", summary),
                labeled("Full announcement", scrollOf(full)),
                labeled("When (optional)", when),
                labeled("Where (optional)", where),
                pin,
                buttons(cancel, save));
        showIn(d, form, this);
        d.setVisible(true);
    }

    // ------------------------------------------------------------ MY PROFILE
    private JComponent profile() {
        profileHeaderView = dyn(this::profileHeader);
        sContact = inputField();
        restrictInput(sContact, BaseDashboard::isDigit, 11);

        JButton discard = secondaryButton("Discard changes");
        JButton save = primaryButton("Save changes");
        discard.addActionListener(e -> {
            profileLoaded = false;
            loadProfileOnce();
        });
        save.addActionListener(e -> {
            boolean ok = attempt(this, () -> saveProfile(sContact.getText()));
            if (ok) {
                profileHeaderView.refresh();
                toast("Profile updated", "Your details were saved.", GREEN, "check");
            }
        });

        JComponent form = column(14,
                labeled("Contact number", sContact),
                buttons(discard, save));

        JComponent account = column(14,
                factBox(new String[][]{
                    {"Name", userName},
                    {"Role", "Barangay Staff"}}),
                wrap("Your name and login are managed by the system administrator. "
                        + "Contact them if these need to be corrected.", 230, 13, BODY));

        return page(profileHeaderView,
                twoCol(section("Personal details", null, form), section("Account", null, account), 3, 2));
    }

    private JComponent profileHeader() {
        RoundedPanel c = card();
        c.setLayout(new BorderLayout(22, 0));
        c.setBorder(new EmptyBorder(24, 28, 26, 28));

        JPanel av = new JPanel(new GridBagLayout());
        av.setOpaque(false);
        av.add(new Avatar(userName, 88));
        c.add(av, BorderLayout.WEST);

        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pillRow.setOpaque(false);
        pillRow.add(statusPill("Active"));
        pillRow.add(Box.createHorizontalStrut(12));
        pillRow.add(lbl("You can review reports, verify residents and post announcements.",
                Font.PLAIN, 13, MUTED));

        JPanel texts = new JPanel(new GridBagLayout());
        texts.setOpaque(false);
        texts.add(column(8, heading(userName, 26, INK),
                lbl(staffPosition, Font.PLAIN, 14, BODY), pillRow),
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
        if (profileLoaded || staffId <= 0) {
            return;
        }
        profileLoaded = true;
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                return new String[]{
                    loadStaffColumn("contact_number")};
            }

            @Override
            protected void done() {
                try {
                    String[] v = get();
                    sContact.setText(v[0]);
                } catch (Exception ex) {
                    System.err.println("Profile load failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Reads one column of this staff member's row. Only called with our own
     * constant column names (never user input). A missing column just leaves
     * the field blank.
     */
    private String loadStaffColumn(String column) {
        String sql = "SELECT " + column + " FROM users WHERE user_id = ?";
        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, staffId);
            try (ResultSet rs = stmt.executeQuery()) {
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

    private void saveProfile(String contact) {

        if (staffId <= 0) {
            throw new IllegalStateException(
                    "Please log in with a valid staff account.");
        }

        String validContact = contact == null ? "" : contact.trim();
        if (!validContact.isEmpty() && !isValidContactNumber(validContact)) {
            throw new IllegalArgumentException(CONTACT_RULE);
        }

        String sql = "UPDATE users SET contact_number = ? "
                + "WHERE user_id = ? AND role = 'STAFF'";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, validContact.isEmpty() ? null : validContact);
            stmt.setInt(2, staffId);

            stmt.executeUpdate();

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Failed to update profile: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------ RESIDENT INFORMATION
    private Resident findResident(int userId) {
        for (Resident r : residents) {
            if (r.userId == userId) {
                return r;
            }
        }
        return null;
    }

    /**
     * "Reported by <name>" line for the review pop-up; the name opens the
     * resident's information.
     */
    private JComponent residentLink(Report r, Window owner) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(lbl("Reported by", Font.PLAIN, 13, MUTED));
        JLabel name = lbl(r.resident, Font.BOLD, 14, ACCENT);
        row.add(name);
        Resident res = findResident(r.residentId);
        if (res != null) {
            makeLink(name, ACCENT, GOLD, () -> openResident(res, owner));
        }
        return row;
    }

    private void openResident(Resident r, Window owner) {
        if (r == null) {
            return;
        }
        JDialog d = new JDialog(owner, "Resident information", Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        showIn(d, residentInfoView(d, r), owner);
        d.setSize(700, 540);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    /**
     * user_code of each resident (e.g. RB1-0002), filled by loadResidents().
     */
    private static final java.util.Map<Integer, String> RESIDENT_CODES = new java.util.HashMap<>();

    /**
     * Resident number shown to staff: the user_code stored in the database.
     */
    private static String residentNo(Resident r) {
        String code = RESIDENT_CODES.get(r.userId);
        return code == null || code.isEmpty() ? "Not assigned" : code;
    }

    /**
     * Turns the stored yyyy-MM-dd text into "Mar 27, 1995  (age 31)".
     */
    private static String birthText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Not provided";
        }
        try {
            LocalDate b = LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
            int age = java.time.Period.between(b, LocalDate.now()).getYears();
            return Fmt.date(b) + "  (age " + age + ")";
        } catch (java.time.format.DateTimeParseException ex) {
            return raw;
        }
    }

    private JComponent residentInfoView(JDialog d, Resident r) {
        String contact = loadResidentColumn(r.userId, "contact_number");
        String address = loadResidentColumn(r.userId, "address");
        String birth = birthText(loadResidentColumn(r.userId, "birth_date"));
        int total = 0, open = 0;
        for (Report x : reports) {
            if (x.residentId == r.userId) {
                total++;
                if (!x.isClosed()) {
                    open++;
                }
            }
        }
        boolean pending = "PENDING".equals(r.status);
        String pill = "VERIFIED".equals(r.status) ? "Verified" : "REJECTED".equals(r.status) ? "Declined" : "Awaiting";

        JPanel av = new JPanel(new GridBagLayout());
        av.setOpaque(false);
        av.add(new Avatar(r.name, 76));
        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pillRow.setOpaque(false);
        pillRow.add(statusPill(pill));
        JPanel top = new JPanel(new BorderLayout(20, 0));
        top.setOpaque(false);
        top.add(av, BorderLayout.WEST);
        top.add(column(6, lbl("RESIDENT", Font.BOLD, 11, MUTED), heading(r.name, 23, INK), pillRow),
                BorderLayout.CENTER);

        String decision = "VERIFIED".equals(r.status) ? "Verified on"
                : "REJECTED".equals(r.status) ? "Declined on" : "Status";
        String decisionValue = pending ? "Waiting for verification" : Fmt.date(r.decidedOn);
        JComponent facts = factBox(new String[][]{
            {"Resident no.", residentNo(r)},
            {"Birth date", birth},
            {"Purok", r.purok == null || r.purok.isEmpty() ? "Not provided" : r.purok},
            {"Registered on", Fmt.date(r.registered)},
            {decision, decisionValue},
            {"Contact number", contact.isEmpty() ? "Not provided" : contact},
            {"Home address", address.isEmpty() ? "Not provided" : address},
            {"Profile setup", isReady(r) ? "Complete" : "Not finished yet"},
            {"Reports filed", total + " (" + open + " active)"}});

        JButton close = secondaryButton("Close");
        close.addActionListener(e -> d.dispose());

        JComponent actions;
        if (pending) {
            JButton decline = dangerButton("Decline");
            JButton approve = primaryButton("Approve");
            boolean ready = isReady(r);
            approve.setEnabled(ready);
            if (!ready) {
                approve.setToolTipText("This resident has not finished setting up their profile yet");
            }
            decline.addActionListener(e -> {
                boolean ok = confirm(d, "users", RED, "Decline this resident?",
                        r.name + "'s registration will be marked as not approved.",
                        "Decline", "Cancel", true);
                if (ok && attempt(d, () -> decideResident(r, false))) {
                    d.dispose();
                    toast("Resident declined", r.name + " was not approved.", ACCENT, "info");
                }
            });
            approve.addActionListener(e -> {
                if (attempt(d, () -> decideResident(r, true))) {
                    d.dispose();
                    toast("Resident approved", r.name + " can now file reports.", GREEN, "check");
                }
            });
            actions = buttons(close, decline, approve);
        } else if ("REJECTED".equals(r.status)) {
            JButton reconsider = primaryButton("Reconsider");
            reconsider.addActionListener(e -> {
                if (confirmReconsider(d, r) && attempt(d, () -> reconsiderResident(r))) {
                    d.dispose();
                    toast("Moved to pending", r.name + " can now be verified again.", GREEN, "check");
                }
            });
            actions = buttons(close, reconsider);
        } else {
            actions = buttons(close);
        }
        return column(20, top, facts, actions);
    }

    /**
     * Reads one column of a resident's row. Only called with our own constant
     * column names (never user input). A missing column just gives "".
     */
    private String loadResidentColumn(int userId, String column) {
        String sql = "SELECT " + column + " FROM users WHERE user_id = ?";
        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return v == null ? "" : v;
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Resident info: could not read " + column + ": " + e.getMessage());
        }
        return "";
    }

    // ------------------------------------------------------------ SETUP + RECONSIDER
    /**
     * Ids of residents whose contact number and address are filled in.
     * Returns null when the columns cannot be read, so nobody gets blocked.
     */
    private java.util.Set<Integer> loadCompleteIds() {
        String sql = "SELECT user_id FROM users "
                + "WHERE role = 'RESIDENT' "
                + "AND contact_number IS NOT NULL AND contact_number <> '' "
                + "AND address IS NOT NULL AND address <> ''";
        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
            return ids;
        } catch (java.sql.SQLException e) {
            System.err.println("Could not check profile setup: " + e.getMessage());
            return null;
        }
    }

    private boolean isReady(Resident r) {
        java.util.Set<Integer> ids = completeIds;
        return ids == null || ids.contains(r.userId);
    }

    private boolean confirmReconsider(Window owner, Resident r) {
        return confirm(owner, "users", ACCENT, "Reconsider this resident?",
                r.name + " will go back to the pending list. Continue only after they have shown "
                + "a valid ID at the barangay hall.",
                "Reconsider", "Cancel", false);
    }

    private void reconsiderFlow(Resident r, Window owner) {
        if (confirmReconsider(owner, r) && attempt(owner, () -> reconsiderResident(r))) {
            toast("Moved to pending", r.name + " can now be verified again.", GREEN, "check");
        }
    }

    /**
     * Declined -> pending again, so staff can approve after checking an ID.
     */
    private void reconsiderResident(Resident res) {
        String sql = "UPDATE users "
                + "SET account_status = 'PENDING', verified_by = NULL, verified_on = NULL "
                + "WHERE user_id = ? AND role = 'RESIDENT' AND account_status = 'REJECTED'";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, res.userId);

            if (stmt.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "This resident is no longer declined. Please refresh.");
            }

            res.status = "PENDING";
            res.decidedOn = null;
            refreshAll();

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Database error: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------ DATABASE ACTIONS
    // These operations use the MySQL database.
    private List<Report> sortedReports() {
        List<Report> out = new ArrayList<>(reports);
        out.sort((a, b) -> {
            int c = b.filed.compareTo(a.filed);
            return c != 0 ? c : Integer.compare(b.id, a.id);
        });
        return out;
    }

    private List<Resident> loadResidents() {
        List<Resident> list = new ArrayList<>();

        String sql = "SELECT u.user_id, u.user_code, " + Db.fullName("u") + " AS full_name, u.purok, "
                + "u.account_status, DATE(u.created_at) AS registered_on, u.verified_on "
                + "FROM users u "
                + "WHERE u.role = 'RESIDENT' "
                + "ORDER BY u.created_at DESC";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int userId = rs.getInt("user_id");
                RESIDENT_CODES.put(userId, rs.getString("user_code"));
                String name = rs.getString("full_name");
                String purok = rs.getString("purok");
                String status = rs.getString("account_status");

                java.sql.Date registeredDate
                        = rs.getDate("registered_on");

                java.sql.Date decidedDate
                        = rs.getDate("verified_on");

                LocalDate registered = registeredDate == null
                        ? null : registeredDate.toLocalDate();

                LocalDate decided = decidedDate == null
                        ? null : decidedDate.toLocalDate();

                list.add(new Resident(
                        userId, name, purok, status, registered, decided
                ));
            }

        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "Unable to load residents from MySQL.", e);
        }

        return list;
    }

    /**
     * Pending first, then verified, then not approved.
     */
    private List<Resident> sortedResidents() {
        List<Resident> out = new ArrayList<>(residents);
        out.sort(Comparator.comparingInt((Resident r) -> "PENDING".equals(r.status) ? 0 : "VERIFIED".equals(r.status) ? 1 : 2)
                .thenComparing(r -> r.name));
        return out;
    }

    /**
     * Pinned posts first, then newest.
     */
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

    private void updateReport(Report r, String newStatus, String note) {

        String text = note == null ? "" : note.trim();

        if (newStatus == null
                || !java.util.Arrays.asList(STATUSES).contains(newStatus)) {
            throw new IllegalArgumentException("Invalid report status.");
        }

        if ("Declined".equals(newStatus) && text.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please write a reason for declining the report.");
        }

        if (newStatus.equals(r.status) && text.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nothing to update.");
        }

        if (!text.isEmpty() && staffId <= 0) {
            throw new IllegalStateException(
                    "Please log in with a valid staff account to add notes.");
        }

        String updateSql
                = "UPDATE reports SET status = ?, updated_on = CURDATE(), "
                + "closed_on = ? WHERE report_id = ?";

        String noteSql
                = "INSERT INTO report_notes "
                + "(report_id, staff_id, note, created_on) "
                + "VALUES (?, ?, ?, CURDATE())";

        LocalDate today = LocalDate.now();
        boolean closed = "Resolved".equals(newStatus)
                || "Declined".equals(newStatus);

        try (Connection conn = Db.get()) {

            conn.setAutoCommit(false);

            try {
                try (PreparedStatement stmt
                        = conn.prepareStatement(updateSql)) {

                    stmt.setString(1, newStatus);

                    if (closed) {
                        stmt.setDate(2, java.sql.Date.valueOf(today));
                    } else {
                        stmt.setNull(2, java.sql.Types.DATE);
                    }

                    stmt.setInt(3, r.id);

                    if (stmt.executeUpdate() != 1) {
                        throw new java.sql.SQLException(
                                "Report not found in MySQL.");
                    }
                }

                if (!text.isEmpty()) {
                    try (PreparedStatement stmt
                            = conn.prepareStatement(noteSql)) {

                        stmt.setInt(1, r.id);
                        stmt.setInt(2, staffId);
                        stmt.setString(3, text);

                        stmt.executeUpdate();
                    }
                }

                conn.commit();

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to update report: " + e.getMessage(), e);
        }

        r.status = newStatus;
        r.updated = today;
        r.closedOn = closed ? today : null;

        if (!text.isEmpty()) {
            r.notes.add(new Note(today, userName, text));
        }

        refreshAll();
    }

    private void decideResident(Resident res, boolean approve) {
        String newStatus = approve ? "VERIFIED" : "REJECTED";

        if (approve && !isReady(res)) {
            throw new IllegalStateException(
                    "This resident has not finished setting up their profile yet.");
        }

        String sql = "UPDATE users "
                + "SET account_status = ?, verified_by = ?, verified_on = CURDATE() "
                + "WHERE user_id = ? AND role = 'RESIDENT' AND account_status = 'PENDING'";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newStatus);
            if (staffId > 0) {
                stmt.setInt(2, staffId);
            } else {
                stmt.setNull(2, java.sql.Types.INTEGER);
            }
            stmt.setInt(3, res.userId);

            int updated = stmt.executeUpdate();

            if (updated != 1) {
                throw new IllegalStateException(
                        "No matching pending resident found. Please refresh.");
            }

            res.status = newStatus;
            res.decidedOn = LocalDate.now();
            refreshAll();

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Database error: " + e.getMessage(), e);
        }
    }

    private void postAnnouncement(String tag, String title,
            String summary, String fullText,
            String[][] facts, boolean pinned) {

        if (staffId <= 0) {
            throw new IllegalStateException(
                    "Please log in using a valid staff account.");
        }

        String validTitle = need(title, "title");
        String validSummary = need(summary, "short summary");
        String validFullText = need(fullText, "full announcement");

        String sql = "INSERT INTO announcements "
                + "(staff_id, tag, title, summary, full_text, schedule, venue, pinned, posted_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        int announcementId = 0;

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(
                        sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, staffId);
            stmt.setString(2, tag);
            stmt.setString(3, validTitle);
            stmt.setString(4, validSummary);
            stmt.setString(5, validFullText);
            stmt.setString(6, factValue(facts, "When"));
            stmt.setString(7, factValue(facts, "Where"));
            stmt.setBoolean(8, pinned);

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new java.sql.SQLException(
                            "Unable to retrieve announcement ID.");
                }
                announcementId = keys.getInt(1);
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to save announcement to MySQL: "
                    + e.getMessage(), e);
        }

        Announcement a = new Announcement(announcementId);

        a.staffName = userName;
        a.staffRole = staffPosition;
        a.postedAt = LocalDateTime.now();

        fillPost(a, tag, validTitle, validSummary,
                validFullText, facts, pinned);

        posts.add(a);
        refreshAll();

    }

    private void updateAnnouncement(Announcement a, String tag,
            String title, String summary, String fullText,
            String[][] facts, boolean pinned) {

        String validTitle = need(title, "title");
        String validSummary = need(summary, "short summary");
        String validFullText = need(fullText, "full announcement");

        String updateSql = "UPDATE announcements "
                + "SET tag = ?, title = ?, summary = ?, "
                + "full_text = ?, schedule = ?, venue = ?, pinned = ? "
                + "WHERE announcement_id = ?";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(updateSql)) {

            stmt.setString(1, tag);
            stmt.setString(2, validTitle);
            stmt.setString(3, validSummary);
            stmt.setString(4, validFullText);
            stmt.setString(5, factValue(facts, "When"));
            stmt.setString(6, factValue(facts, "Where"));
            stmt.setBoolean(7, pinned);
            stmt.setInt(8, a.id);

            if (stmt.executeUpdate() != 1) {
                throw new java.sql.SQLException("Announcement not found.");
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to update announcement: "
                    + e.getMessage(), e);
        }

        // Update the dashboard after successful database save
        fillPost(a, tag, validTitle, validSummary,
                validFullText, facts, pinned);

        refreshAll();
    }

    private void deleteAnnouncement(Announcement a) {

        String deleteAnnouncementSql
                = "DELETE FROM announcements WHERE announcement_id = ?";

        try (Connection conn = Db.get();
                PreparedStatement stmt = conn.prepareStatement(deleteAnnouncementSql)) {

            stmt.setInt(1, a.id);

            if (stmt.executeUpdate() != 1) {
                throw new java.sql.SQLException("Announcement not found.");
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to delete announcement: "
                    + e.getMessage(), e);
        }

        posts.remove(a);
        refreshAll();
    }

    /**
     * Finds a detail row such as "When" or "Where"; null when it is empty.
     * These are saved in the schedule and venue columns.
     */
    private static String factValue(String[][] facts, String label) {
        if (facts != null) {
            for (String[] f : facts) {
                if (label.equals(f[0]) && f[1] != null && !f[1].trim().isEmpty()) {
                    return f[1].trim();
                }
            }
        }
        return null;
    }

    private void fillPost(Announcement a, String tag, String title, String summary, String fullText,
            String[][] facts, boolean pinned) {
        a.tag = tag;
        a.title = need(title, "title");
        a.summary = need(summary, "short summary");
        a.fullText = need(fullText, "full announcement");
        a.facts = facts;
        a.pinned = pinned;
    }

    /**
     * Always start from the sign-in page (no test account), even when this
     * file is the one being run.
     */
    public static void main(String[] args) {
        Login.main(args);
    }
}