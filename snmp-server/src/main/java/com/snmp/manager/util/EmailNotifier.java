package com.snmp.manager.util;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class EmailNotifier {

    private static final String CONFIG_RESOURCE = "email.properties";
    private static final String DEFAULT_SUBJECT = "[SNMP Alert] Trap Notification";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;

    private EmailNotifier(String host, int port, String username, String password, String fromAddress) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
    }

    public static EmailNotifier fromResource() throws IOException {
        return fromResource(CONFIG_RESOURCE);
    }

    public static EmailNotifier fromResource(String resource) throws IOException {
        Properties props = new Properties();
        try (InputStream in = EmailNotifier.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Email config not found on classpath: " + resource);
            }
            props.load(in);
        }
        String host = props.getProperty("mail.smtp.host", "localhost");
        int port = Integer.parseInt(props.getProperty("mail.smtp.port", "25"));
        String username = props.getProperty("mail.smtp.username", "");
        String password = props.getProperty("mail.smtp.password", "");
        String from = props.getProperty("mail.from.address", username);
        return new EmailNotifier(host, port, username, password, from);
    }

    public void send(String to, String subject, String body) throws MessagingException {
        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.host", host);
        mailProps.put("mail.smtp.port", String.valueOf(port));
        if (!username.isBlank()) {
            mailProps.put("mail.smtp.auth", "true");
            mailProps.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(mailProps, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (username.isBlank()) {
                    return null;
                }
                return new PasswordAuthentication(username, password);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(body);
        Transport.send(message);
    }

    public String getFromAddress() {
        return fromAddress;
    }
}
