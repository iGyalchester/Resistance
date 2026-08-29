package com.resistance.intake.imap;

import com.resistance.intake.mail.MimeText;
import com.resistance.intake.service.InboundEmail;
import com.resistance.intake.service.IntakeService;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Zero-provider inbound path: polls an ordinary IMAP mailbox (e.g. a Gmail
 * address with an app password) for unread mail and feeds each message into
 * the intake flow. Disabled unless intake.imap.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "intake.imap.enabled", havingValue = "true")
public class ImapPollingService {

    private static final Logger log = LoggerFactory.getLogger(ImapPollingService.class);

    private final IntakeService intakeService;
    private final String host;
    private final String username;
    private final String password;

    public ImapPollingService(IntakeService intakeService,
                              @Value("${intake.imap.host}") String host,
                              @Value("${intake.imap.username}") String username,
                              @Value("${intake.imap.password}") String password) {
        this.intakeService = intakeService;
        this.host = host;
        this.username = username;
        this.password = password;
    }

    @Scheduled(fixedDelayString = "${intake.imap.poll-ms:60000}")
    public void poll() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");

        try (Store store = Session.getInstance(props).getStore("imaps")) {
            store.connect(host, username, password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            try {
                Message[] unread = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                for (Message message : unread) {
                    try {
                        intakeService.process(toInboundEmail(message));
                    } catch (Exception e) {
                        log.error("Failed to process message '{}'", message.getSubject(), e);
                    } finally {
                        message.setFlag(Flags.Flag.SEEN, true);
                    }
                }
                if (unread.length > 0) {
                    log.info("Processed {} inbound message(s) from IMAP", unread.length);
                }
            } finally {
                inbox.close(false);
            }
        } catch (Exception e) {
            log.error("IMAP poll against {} failed", host, e);
        }
    }

    private InboundEmail toInboundEmail(Message message) throws Exception {
        String fromAddress = null;
        String fromName = null;
        if (message.getFrom() != null && message.getFrom().length > 0
                && message.getFrom()[0] instanceof InternetAddress internetAddress) {
            fromAddress = internetAddress.getAddress();
            fromName = internetAddress.getPersonal();
        }
        return new InboundEmail(fromAddress, fromName, message.getSubject(), MimeText.extract(message));
    }
}
