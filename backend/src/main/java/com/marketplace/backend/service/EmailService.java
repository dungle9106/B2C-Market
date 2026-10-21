package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.EmailMessage;
import com.marketplace.backend.entity.PlatformSetting;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.EmailMessageRepository;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.security.SecretCipher;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Outgoing e-mail. queue() only stores the message (so an SMTP outage never breaks a sign-up or an order);
 * a background job sends what is due every few seconds, retrying 1, 5 and 30 minutes later before giving up.
 * SMTP settings come from the admin page (password encrypted), falling back to MAIL_* environment variables.
 * With no SMTP server at all, messages are kept as LOGGED and can be read in the admin outbox.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmailService {

    static final String HOST = "mail.host", PORT = "mail.port", USERNAME = "mail.username", PASSWORD = "mail.password",
            FROM = "mail.from", FROM_NAME = "mail.fromName", STARTTLS = "mail.starttls", SSL = "mail.ssl";
    private static final int[] RETRY_MINUTES = {1, 5, 30};

    private final EmailMessageRepository repository;
    private final PlatformSettingRepository settings;
    private final SecretCipher cipher;
    private final AdminAuditService auditService;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Value("${app.messaging.rabbit:false}")
    private boolean rabbit;
    @Value("${MAIL_HOST:}")
    private String envHost;
    @Value("${MAIL_PORT:587}")
    private int envPort;
    @Value("${MAIL_USERNAME:}")
    private String envUser;
    @Value("${MAIL_PASSWORD:}")
    private String envPassword;
    @Value("${MAIL_FROM:no-reply@shoppi.local}")
    private String envFrom;

    private volatile String senderKey;
    private volatile JavaMailSenderImpl sender;

    record Smtp(String host, int port, String username, String password, String from, String fromName, boolean starttls, boolean ssl) {
        boolean configured() {
            return host != null && !host.isBlank();
        }
    }

    // ==================== sending ====================

    /** Stores an e-mail to be sent shortly (never throws because of SMTP). */
    public EmailMessage queue(String to, Long userId, String type, String subject, String html) {
        EmailMessage m = repository.save(EmailMessage.builder().toAddress(to).toUserId(userId).type(type).subject(cut(subject, 250)).html(html)
                .createdAt(LocalDateTime.now()).nextAttemptAt(LocalDateTime.now()).build());
        // with RabbitMQ on, the id goes to the "shoppi.email" queue after the commit (messaging.EmailQueuePublisher)
        events.publishEvent(new com.marketplace.backend.messaging.EmailQueuedEvent(m.getId()));
        return m;
    }

    /** Sends one queued e-mail now (RabbitMQ consumer); nothing if it was already sent or someone else is sending it. */
    public void sendNow(Long id) {
        if (repository.claim(id, LocalDateTime.now(), LocalDateTime.now().plusMinutes(2)) == 0) return;
        repository.findById(id).ifPresent(m -> process(smtp(), m));
    }

    /** Sends what is due (runs every 5 seconds). */
    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    public void sendDue() {
        // with RabbitMQ the consumer sends at once: the scheduler only picks up what it missed (broker down) and retries
        LocalDateTime now = LocalDateTime.now();
        List<EmailMessage> due = repository.due(rabbit ? now.minusSeconds(30) : now, PageRequest.of(0, 20));
        if (due.isEmpty()) return;
        Smtp smtp = smtp();
        for (EmailMessage m : due) {
            if (repository.claim(m.getId(), now, now.plusMinutes(2)) == 0) continue;
            process(smtp, m);
        }
    }

    private void process(Smtp smtp, EmailMessage m) {
        if (!smtp.configured()) {
            m.setStatus(EmailMessage.Status.LOGGED);
            m.setLastError("Chưa cấu hình máy chủ SMTP: email được lưu lại, chưa gửi đi");
            return;
        }
        try {
            deliver(smtp, m.getToAddress(), m.getSubject(), m.getHtml());
            m.setStatus(EmailMessage.Status.SENT);
            m.setSentAt(LocalDateTime.now());
            m.setLastError(null);
        } catch (Exception e) {
            m.setAttempts(m.getAttempts() + 1);
            m.setLastError(cut(e.getClass().getSimpleName() + ": " + e.getMessage(), 500));
            if (m.getAttempts() > RETRY_MINUTES.length) {
                m.setStatus(EmailMessage.Status.FAILED);
                log.warn("E-mail #{} to {} failed for good: {}", m.getId(), m.getToAddress(), e.getMessage());
            } else {
                m.setNextAttemptAt(LocalDateTime.now().plusMinutes(RETRY_MINUTES[m.getAttempts() - 1]));
            }
        }
    }

    private void deliver(Smtp smtp, String to, String subject, String html) throws Exception {
        JavaMailSenderImpl s = sender(smtp);
        MimeMessage msg = s.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
        h.setFrom(new InternetAddress(smtp.from(), smtp.fromName(), StandardCharsets.UTF_8.name()));
        h.setTo(to);
        h.setSubject(subject);
        h.setText(html, true);
        s.send(msg);
    }

    // one mail sender per SMTP configuration
    private JavaMailSenderImpl sender(Smtp smtp) {
        String key = smtp.toString();
        if (sender == null || !key.equals(senderKey)) {
            JavaMailSenderImpl s = new JavaMailSenderImpl();
            s.setHost(smtp.host());
            s.setPort(smtp.port());
            if (smtp.username() != null && !smtp.username().isBlank()) {
                s.setUsername(smtp.username());
                s.setPassword(smtp.password());
            }
            Properties p = s.getJavaMailProperties();
            p.put("mail.smtp.auth", String.valueOf(smtp.username() != null && !smtp.username().isBlank()));
            p.put("mail.smtp.starttls.enable", String.valueOf(smtp.starttls()));
            p.put("mail.smtp.ssl.enable", String.valueOf(smtp.ssl()));
            p.put("mail.smtp.connectiontimeout", "10000");
            p.put("mail.smtp.timeout", "15000");
            p.put("mail.smtp.writetimeout", "15000");
            // a fixed EHLO name: looking up the machine's own name can take seconds on some networks
            p.put("mail.smtp.localhost", "shoppi.local");
            sender = s;
            senderKey = key;
        }
        return sender;
    }

    // ==================== admin: settings ====================

    @Transactional(readOnly = true)
    public Map<String, Object> config() {
        Smtp s = smtp();
        String stored = get(PASSWORD);
        return AdminUserService.map("host", s.host(), "port", s.port(), "username", s.username(), "from", s.from(), "fromName", s.fromName(),
                "starttls", s.starttls(), "ssl", s.ssl(), "configured", s.configured(),
                "source", get(HOST) != null ? "ADMIN" : envHost != null && !envHost.isBlank() ? "ENV" : "NONE",
                "passwordSet", stored != null || (envPassword != null && !envPassword.isBlank()),
                "stats", stats());
    }

    /** body: { host, port, username, password?, clearPassword?, from, fromName, starttls, ssl } */
    public Map<String, Object> update(Map<String, Object> body) {
        String host = str(body.get("host"));
        if (host != null && !host.matches("[A-Za-z0-9.\\-]{1,200}")) throw bad("Máy chủ SMTP không hợp lệ");
        int port = body.get("port") instanceof Number n ? n.intValue() : 587;
        if (port < 1 || port > 65535) throw bad("Cổng SMTP không hợp lệ");
        String from = str(body.get("from"));
        if (host != null && (from == null || !from.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) throw bad("Địa chỉ gửi không hợp lệ");
        put(HOST, host);
        put(PORT, String.valueOf(port));
        put(USERNAME, str(body.get("username")));
        put(FROM, from);
        put(FROM_NAME, str(body.get("fromName")));
        put(STARTTLS, String.valueOf(!Boolean.FALSE.equals(body.get("starttls"))));
        put(SSL, String.valueOf(Boolean.TRUE.equals(body.get("ssl"))));
        if (Boolean.TRUE.equals(body.get("clearPassword"))) put(PASSWORD, null);
        else if (str(body.get("password")) != null) put(PASSWORD, cipher.encrypt(str(body.get("password"))));
        auditService.log("UPDATE_EMAIL_CONFIG", "SETTING", null, "Máy chủ email", host == null ? "Tắt gửi email (chỉ lưu)" : host + ":" + port);
        return config();
    }

    /** Sends at once (not queued) so the admin sees the SMTP error right away. */
    public Map<String, Object> sendTest(String to) {
        if (to == null || !to.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw bad("Địa chỉ nhận không hợp lệ");
        EmailTemplates.Mail mail = EmailTemplates.test();
        Smtp smtp = smtp();
        EmailMessage m = queue(to, null, "TEST", mail.subject(), mail.html());
        if (!smtp.configured()) {
            m.setStatus(EmailMessage.Status.LOGGED);
            return AdminUserService.map("ok", false, "message", "Chưa cấu hình SMTP: email được lưu trong hộp thư thử nghiệm");
        }
        try {
            deliver(smtp, to, mail.subject(), mail.html());
            m.setStatus(EmailMessage.Status.SENT);
            m.setSentAt(LocalDateTime.now());
            return AdminUserService.map("ok", true, "message", "Đã gửi email thử đến " + to);
        } catch (Exception e) {
            m.setStatus(EmailMessage.Status.FAILED);
            m.setAttempts(1);
            m.setLastError(cut(e.getMessage(), 500));
            return AdminUserService.map("ok", false, "message", "Không gửi được: " + cut(e.getMessage(), 300));
        }
    }

    // ==================== admin: outbox ====================

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, String type, String q, int page, int size) {
        EmailMessage.Status st = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                st = EmailMessage.Status.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw bad("Trạng thái không hợp lệ");
            }
        }
        return PageResponse.of(repository.search(st, type == null ? "" : type.trim().toUpperCase(Locale.ROOT), q == null ? "" : q.trim().toLowerCase(Locale.ROOT),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id").descending())), m -> view(m, false));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return view(find(id), true);
    }

    /** Puts a failed / logged e-mail back in the queue (e.g. once SMTP is configured). */
    public Map<String, Object> retry(Long id) {
        EmailMessage m = find(id);
        if (m.getStatus() == EmailMessage.Status.SENT || m.getStatus() == EmailMessage.Status.QUEUED) throw bad("Email này không cần gửi lại");
        m.setStatus(EmailMessage.Status.QUEUED);
        m.setAttempts(0);
        m.setNextAttemptAt(LocalDateTime.now());
        return view(m, false);
    }

    private Map<String, Object> stats() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (EmailMessage.Status s : EmailMessage.Status.values()) m.put(s.name(), 0L);
        repository.countsSince(LocalDateTime.now().minusDays(7)).forEach(r -> m.put(((EmailMessage.Status) r[0]).name(), ((Number) r[1]).longValue()));
        return new LinkedHashMap<>(m);
    }

    private static Map<String, Object> view(EmailMessage m, boolean full) {
        Map<String, Object> v = AdminUserService.map("id", m.getId(), "to", m.getToAddress(), "userId", m.getToUserId(), "type", m.getType(),
                "subject", m.getSubject(), "status", m.getStatus().name(), "attempts", m.getAttempts(), "lastError", m.getLastError(),
                "createdAt", m.getCreatedAt(), "sentAt", m.getSentAt(), "nextAttemptAt", m.getNextAttemptAt());
        if (full) v.put("html", m.getHtml());
        return v;
    }

    // ==================== helpers ====================

    Smtp smtp() {
        boolean admin = get(HOST) != null;
        String pw = admin ? cipher.decrypt(get(PASSWORD)) : envPassword;
        return new Smtp(admin ? get(HOST) : envHost, admin ? Integer.parseInt(Objects.requireNonNullElse(get(PORT), "587")) : envPort,
                admin ? get(USERNAME) : envUser, pw, admin && get(FROM) != null ? get(FROM) : envFrom,
                Objects.requireNonNullElse(get(FROM_NAME), "Shoppi"), !"false".equals(get(STARTTLS)), "true".equals(get(SSL)));
    }

    private String get(String key) {
        return settings.findById(key).map(PlatformSetting::getValue).orElse(null);
    }

    private void put(String key, String value) {
        if (value == null) {
            settings.findById(key).ifPresent(settings::delete);
            return;
        }
        PlatformSetting s = settings.findById(key).orElseGet(PlatformSetting::new);
        s.setKey(key);
        s.setValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        settings.save(s);
    }

    private EmailMessage find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiException("Không tìm thấy email", HttpStatus.NOT_FOUND));
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static ApiException bad(String m) {
        return new ApiException(m, HttpStatus.BAD_REQUEST);
    }
}
