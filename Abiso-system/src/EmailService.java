import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Sends email through Gmail SMTP. The Gmail address and App Password are read
 * from mail.properties so they are not written inside the code.
 */
public final class EmailService {

    private EmailService() {
    }

    private static Properties loadConfig() throws MessagingException {
        Properties cfg = new Properties();
        try (FileInputStream in = new FileInputStream("mail.properties")) {
            cfg.load(in);
        } catch (IOException e) {
            throw new MessagingException(
                    "mail.properties not found in the project folder.", e);
        }
        return cfg;
    }

    public static void send(String to, String subject, String body)
            throws MessagingException {

        Properties cfg = loadConfig();
        final String user = cfg.getProperty("mail.user", "").trim();
        final String pass = cfg.getProperty("mail.password", "").replace(" ", "");

        if (user.isEmpty() || pass.isEmpty()) {
            throw new MessagingException("mail.user / mail.password is empty.");
        }

        Properties p = new Properties();
        p.put("mail.smtp.host", "smtp.gmail.com");
        p.put("mail.smtp.port", "587");
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");

        Session session = Session.getInstance(p, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        });

        MimeMessage msg = new MimeMessage(session);
        try {
            msg.setFrom(new InternetAddress(user, "Abiso"));
        } catch (java.io.UnsupportedEncodingException e) {
            msg.setFrom(new InternetAddress(user));
        }
        msg.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        msg.setSubject(subject, "UTF-8");
        msg.setText(body, "UTF-8");
        Transport.send(msg);
    }
}