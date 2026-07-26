package com.snmp.manager.util;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Sends SMS notifications via Twilio when a trap action is triggered.
 */
public class SmsNotifier {

    private static final String CONFIG_RESOURCE = "sms.properties";
    private static final String DEFAULT_TEMPLATE = "[SNMP Alert] Trap: %s | Severity: %s | Node: %s";

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    private SmsNotifier(String accountSid, String authToken, String fromNumber) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    public static SmsNotifier fromResource() throws IOException {
        return fromResource(CONFIG_RESOURCE);
    }

    public static SmsNotifier fromResource(String resource) throws IOException {
        Properties props = new Properties();
        try (InputStream in = SmsNotifier.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("SMS config not found on classpath: " + resource);
            }
            props.load(in);
        }
        String sid = props.getProperty("twilio.account.sid");
        String token = props.getProperty("twilio.auth.token");
        String from = props.getProperty("twilio.from.number");
        return new SmsNotifier(sid, token, from);
    }

    public void send(String to, String body) {
        Twilio.init(accountSid, authToken);
        Message.creator(
                new PhoneNumber(to),
                new PhoneNumber(fromNumber),
                body
        ).create();
    }

    public String getFromNumber() {
        return fromNumber;
    }
}
