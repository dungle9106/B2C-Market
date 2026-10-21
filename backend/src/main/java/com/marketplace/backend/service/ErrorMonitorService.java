package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.PageResponse;
import com.marketplace.backend.entity.AdminPermission;
import com.marketplace.backend.entity.ErrorEvent;
import com.marketplace.backend.exception.ApiException;
import com.marketplace.backend.repository.ErrorEventRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.security.CurrentUser;
import com.marketplace.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * "Giam sat loi": unexpected server errors (the 500s) grouped by fingerprint, so 1000 identical failures are
 * one line with a counter. A new kind of error, or one that comes back after being marked resolved, is
 * reported to the admins who manage the system.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ErrorMonitorService {

    private final ErrorEventRepository repository;
    private final UserRepository userRepository;
    private final AdminAuditService auditService;

    /** Called by the exception handler; returns the error's reference ("ERR-12"), shown to the user. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String record(Throwable ex, String method, String path) {
        try {
            Throwable root = rootCause(ex);
            String location = location(root);
            String fingerprint = sha(root.getClass().getName() + "|" + location);
            LocalDateTime now = LocalDateTime.now();
            ErrorEvent e = repository.findByFingerprint(fingerprint).orElse(null);
            boolean isNew = e == null;
            boolean reopened = false;
            if (isNew) {
                e = ErrorEvent.builder().fingerprint(fingerprint).exceptionType(cut(root.getClass().getName(), 200))
                        .location(cut(location, 300)).firstSeen(now).build();
            } else if (e.getStatus() == ErrorEvent.Status.RESOLVED) {
                e.setStatus(ErrorEvent.Status.OPEN);
                e.setReopenCount(e.getReopenCount() + 1);
                reopened = true;
            }
            e.setMessage(cut(root.getMessage(), 1000));
            e.setOccurrences(e.getOccurrences() + 1);
            e.setLastSeen(now);
            e.setLastMethod(method);
            e.setLastPath(cut(path, 300));
            e.setLastUserId(currentUserOrNull());
            e.setStackTrace(cut(stack(ex), 20_000));
            repository.save(e);
            if (isNew || reopened) {
                auditService.notifyAdmins(AdminPermission.SETTINGS, (isNew ? "Lỗi hệ thống mới: " : "Lỗi đã xử lý xuất hiện lại: ")
                        + simple(root.getClass().getName()), cut(method + " " + path + " – " + root.getMessage(), 200), "/admin/monitoring?error=" + e.getId());
            }
            return "ERR-" + e.getId();
        } catch (Exception failure) {
            // monitoring must never make things worse
            log.warn("Could not record an error: {}", failure.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String status, String q, int page, int size) {
        ErrorEvent.Status st = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                st = ErrorEvent.Status.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ApiException("Trạng thái không hợp lệ", HttpStatus.BAD_REQUEST);
            }
        }
        return PageResponse.of(repository.search(st, q == null ? "" : q.trim().toLowerCase(Locale.ROOT),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("lastSeen").descending())), e -> view(e, false));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        return AdminUserService.map("OPEN", repository.countByStatus(ErrorEvent.Status.OPEN),
                "RESOLVED", repository.countByStatus(ErrorEvent.Status.RESOLVED), "IGNORED", repository.countByStatus(ErrorEvent.Status.IGNORED));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return view(find(id), true);
    }

    /** status: RESOLVED | IGNORED | OPEN */
    public Map<String, Object> setStatus(Long id, String status, String note) {
        ErrorEvent e = find(id);
        ErrorEvent.Status st;
        try {
            st = ErrorEvent.Status.valueOf(status == null ? "" : status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Trạng thái không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        if (note != null && note.length() > 500) throw new ApiException("Ghi chú tối đa 500 ký tự", HttpStatus.BAD_REQUEST);
        e.setStatus(st);
        e.setNote(note == null || note.isBlank() ? e.getNote() : note.trim());
        if (st == ErrorEvent.Status.OPEN) {
            e.setResolvedAt(null);
            e.setResolvedByName(null);
        } else {
            e.setResolvedAt(LocalDateTime.now());
            e.setResolvedByName(userRepository.findById(CurrentUser.id()).map(u -> u.getFullName()).orElse(null));
        }
        auditService.log(st == ErrorEvent.Status.RESOLVED ? "RESOLVE_ERROR" : st == ErrorEvent.Status.IGNORED ? "IGNORE_ERROR" : "REOPEN_ERROR",
                "ERROR", e.getId(), simple(e.getExceptionType()), e.getNote());
        return view(e, true);
    }

    private Map<String, Object> view(ErrorEvent e, boolean full) {
        Map<String, Object> m = AdminUserService.map("id", e.getId(), "code", "ERR-" + e.getId(), "exceptionType", e.getExceptionType(),
                "simpleType", simple(e.getExceptionType()), "message", e.getMessage(), "location", e.getLocation(),
                "lastMethod", e.getLastMethod(), "lastPath", e.getLastPath(), "lastUserId", e.getLastUserId(),
                "occurrences", e.getOccurrences(), "firstSeen", e.getFirstSeen(), "lastSeen", e.getLastSeen(), "status", e.getStatus().name(),
                "resolvedAt", e.getResolvedAt(), "resolvedByName", e.getResolvedByName(), "note", e.getNote(), "reopenCount", e.getReopenCount());
        if (full) m.put("stackTrace", e.getStackTrace());
        return m;
    }

    // ==================== helpers ====================

    private static Throwable rootCause(Throwable t) {
        Throwable r = t;
        for (int i = 0; i < 10 && r.getCause() != null && r.getCause() != r; i++) r = r.getCause();
        return r;
    }

    // the first frame of our own code (else the top frame): stable across messages with ids / values in them
    private static String location(Throwable t) {
        StackTraceElement[] st = t.getStackTrace();
        for (StackTraceElement f : st) {
            if (f.getClassName().startsWith("com.marketplace.backend")) return simple(f.getClassName()) + "." + f.getMethodName() + ":" + f.getLineNumber();
        }
        return st.length > 0 ? simple(st[0].getClassName()) + "." + st[0].getMethodName() : "?";
    }

    private static String stack(Throwable t) {
        StringWriter w = new StringWriter();
        t.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    private static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    static String simple(String className) {
        return className == null ? null : className.substring(className.lastIndexOf('.') + 1);
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static Long currentUserOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal p ? p.getId() : null;
    }

    private ErrorEvent find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiException("Không tìm thấy lỗi", HttpStatus.NOT_FOUND));
    }
}
