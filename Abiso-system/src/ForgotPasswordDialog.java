import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Two-step pop-up: (1) enter email, we send a code; (2) enter the code and a
 * new password. Network and database work run off the screen thread.
 */
public class ForgotPasswordDialog extends JDialog {

    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);

    private final JTextField email = BaseDashboard.inputField();
    private final JTextField code = BaseDashboard.inputField();
    private final JPasswordField pass = new JPasswordField();
    private final JPasswordField confirm = new JPasswordField();
    private final JLabel error1 = errorLabel();
    private final JLabel error2 = errorLabel();
    private final JLabel sentTo = new JLabel();
    private final JButton send = BaseDashboard.primaryButton("Send code");
    private final JButton reset = BaseDashboard.primaryButton("Reset password");
    private final JButton resend = BaseDashboard.secondaryButton("Resend code");

    private Timer cooldown;
    private int secondsLeft;
    private String targetEmail = "";

    public ForgotPasswordDialog(Frame owner, String prefillEmail) {
        super(owner, "Forgot password", true);

        email.setText(prefillEmail == null ? "" : prefillEmail);
        BaseDashboard.restrictInput(code, BaseDashboard::isDigit, 6);
        for (JPasswordField f : new JPasswordField[]{pass, confirm}) {
            f.setFont(BaseDashboard.font(Font.PLAIN, 13));
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BaseDashboard.LINE),
                    new EmptyBorder(9, 10, 9, 10)));
        }

        body.setOpaque(false);
        body.add(stepOne(), "one");
        body.add(stepTwo(), "two");

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        root.setBorder(new EmptyBorder(28, 36, 30, 36));
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);

        send.addActionListener(e -> sendCode());
        resend.addActionListener(e -> sendCode());
        reset.addActionListener(e -> doReset());
        getRootPane().setDefaultButton(send);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // ------------------------------------------------------------ screens
    private JComponent stepOne() {
        return BaseDashboard.column(14,
                BaseDashboard.heading("Forgot your password?", 22, BaseDashboard.INK),
                BaseDashboard.wrap("Enter the email you registered with. "
                        + "We will send a 6-digit code to it.", 340, 13, BaseDashboard.MUTED),
                BaseDashboard.labeled("Email", email),
                error1,
                send);
    }

    private JComponent stepTwo() {
        sentTo.setFont(BaseDashboard.font(Font.PLAIN, 13));
        sentTo.setForeground(BaseDashboard.MUTED);
        return BaseDashboard.column(12,
                BaseDashboard.heading("Enter the code", 22, BaseDashboard.INK),
                sentTo,
                BaseDashboard.labeled("6-digit code", code),
                BaseDashboard.labeled("New password", pass),
                BaseDashboard.labeled("Confirm new password", confirm),
                error2,
                reset,
                resend);
    }

    private static JLabel errorLabel() {
        JLabel l = new JLabel(" ");
        l.setFont(BaseDashboard.font(Font.PLAIN, 12));
        l.setForeground(BaseDashboard.RED);
        return l;
    }

    private void showError(String msg) {
        String html = "<html><body style='width:340px'>" + BaseDashboard.esc(msg) + "</body></html>";
        error1.setText(html);
        error2.setText(html);
        pack();
    }

    private void clearError() {
        error1.setText(" ");
        error2.setText(" ");
    }

    private void busy(boolean b) {
        send.setEnabled(!b);
        reset.setEnabled(!b);
        if (b) {
            resend.setEnabled(false);
        } else if (secondsLeft <= 0) {
            resend.setEnabled(true);
        }
        setCursor(b ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    // ------------------------------------------------------------ actions
    private void sendCode() {
        String addr = email.getText().trim().toLowerCase();
        if (!addr.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            showError("Please enter a valid email address.");
            return;
        }
        clearError();
        busy(true);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    PasswordReset.requestOtp(addr);
                    return null;
                } catch (IllegalStateException e) {
                    return e.getMessage();
                } catch (Exception e) {
                    e.printStackTrace();
                    return "Could not send the email. Check your internet connection and try again.";
                }
            }

            @Override
            protected void done() {
                busy(false);
                String err;
                try {
                    err = get();
                } catch (Exception e) {
                    err = "Something went wrong. Please try again.";
                }
                if (err != null) {
                    showError(err);
                    return;
                }
                targetEmail = addr;
                sentTo.setText("<html><body style='width:340px'>If that email is registered, a code was sent to <b>"
                        + BaseDashboard.esc(addr) + "</b>. It expires in "
                        + PasswordReset.OTP_MINUTES + " minutes.</body></html>");
                cards.show(body, "two");
                getRootPane().setDefaultButton(reset);
                startCooldown();
                pack();
                code.requestFocusInWindow();
            }
        }.execute();
    }

    private void doReset() {
        String c = code.getText().trim();
        String p1 = new String(pass.getPassword());
        String p2 = new String(confirm.getPassword());

        if (c.length() != 6) {
            showError("Please enter the 6-digit code from your email.");
            return;
        }
        if (p1.length() < 8) {
            showError("Password must be at least 8 characters.");
            return;
        }
        if (!p1.equals(p2)) {
            showError("Passwords do not match.");
            return;
        }
        clearError();
        busy(true);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    PasswordReset.resetPassword(targetEmail, c, p1);
                    return null;
                } catch (IllegalArgumentException e) {
                    return e.getMessage();
                } catch (Exception e) {
                    e.printStackTrace();
                    return "Could not reset the password. Please try again.";
                }
            }

            @Override
            protected void done() {
                busy(false);
                String err;
                try {
                    err = get();
                } catch (Exception e) {
                    err = "Something went wrong. Please try again.";
                }
                if (err != null) {
                    showError(err);
                    return;
                }
                JOptionPane.showMessageDialog(ForgotPasswordDialog.this,
                        "Your password was changed. You can sign in now.",
                        "Password updated", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            }
        }.execute();
    }

    private void startCooldown() {
        if (cooldown != null) {
            cooldown.stop();
        }
        secondsLeft = PasswordReset.RESEND_SECONDS;
        resend.setEnabled(false);
        resend.setText("Resend code (" + secondsLeft + "s)");
        cooldown = new Timer(1000, e -> {
            secondsLeft--;
            if (secondsLeft <= 0) {
                cooldown.stop();
                resend.setText("Resend code");
                resend.setEnabled(true);
            } else {
                resend.setText("Resend code (" + secondsLeft + "s)");
            }
        });
        cooldown.start();
    }
}