package com.marketplace.backend.service;

import com.marketplace.backend.entity.Order;
import com.marketplace.backend.entity.OrderStatus;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.repository.OrderRepository;
import com.marketplace.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * Turns account and order events into e-mails, after the change is committed (a rolled-back order sends
 * nothing), in a transaction of its own (a mail problem never affects the order).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventListener {

    private static final Set<String> ORDER_STAGES = Set.of("PLACED", "PAID", "PICKED_UP", "DELIVERED", "CANCELLED", "REFUNDED");

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final EmailService email;
    private final EmailAccountService accountMail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSecurity(SecurityEvent e) {
        try {
            User u = userRepository.findById(e.userId()).orElse(null);
            if (u == null || u.getDeletedAt() != null) return;
            if ("REGISTERED".equals(e.type())) {
                accountMail.sendVerification(u);
                return;
            }
            Object device = e.details().get("device"), ip = e.details().get("ip");
            EmailTemplates.Mail m = EmailTemplates.security(e.type(), u.getFullName(), device == null ? null : device.toString(),
                    ip == null ? null : ip.toString(), e.details().get("minutes"));
            if (m != null) email.queue(u.getEmail(), u.getId(), e.type(), m.subject(), absolute(m.html()));
        } catch (Exception ex) {
            log.warn("Could not queue the {} e-mail: {}", e.type(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrder(OrderStageEvent e) {
        if (!ORDER_STAGES.contains(e.stage())) return;
        try {
            Order o = orderRepository.findById(e.orderId()).orElse(null);
            if (o == null) return;
            // an online order waiting for payment gets its e-mail once it is paid
            if ("PLACED".equals(e.stage()) && o.getStatus() == OrderStatus.AWAITING_PAYMENT) return;
            User b = o.getBuyer();
            EmailTemplates.Mail m = EmailTemplates.order(e.stage(), b.getFullName(), o.getCode(), o.getTotal(), o.getShop().getName(),
                    e.detail(), frontendUrl + "/orders/" + o.getId());
            if (m != null) email.queue(b.getEmail(), b.getId(), "ORDER_" + e.stage(), m.subject(), m.html());
        } catch (Exception ex) {
            log.warn("Could not queue the order e-mail: {}", ex.getMessage());
        }
    }

    // links written as "/path" in the templates point to the shop's website
    private String absolute(String html) {
        return html.replace("href=\"/", "href=\"" + frontendUrl + "/");
    }
}
