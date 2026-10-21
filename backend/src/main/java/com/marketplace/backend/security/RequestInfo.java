package com.marketplace.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Who is calling: IP address, user agent, a readable device name and a device key (to notice new devices). */
public record RequestInfo(String ip, String userAgent, String deviceLabel, String deviceKey) {

    public static RequestInfo current() {
        HttpServletRequest r = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
        if (r == null) return new RequestInfo("?", "", "Không rõ thiết bị", sha("?"));
        // X-Forwarded-For only counts when it was set by our own reverse proxy (loopback / private network);
        // from anywhere else it could be forged to dodge the per-IP limits
        String fwd = r.getHeader("X-Forwarded-For");
        String remote = r.getRemoteAddr();
        String ip = fwd != null && !fwd.isBlank() && trustedProxy(remote) ? fwd.split(",")[0].trim() : remote;
        String ua = r.getHeader("User-Agent") == null ? "" : r.getHeader("User-Agent");
        String label = browser(ua) + " trên " + os(ua);
        // same browser + OS from the same network = the same device
        return new RequestInfo(cut(ip, 64), cut(ua, 300), label, sha(label + "|" + network(ip)));
    }

    static boolean trustedProxy(String addr) {
        try {
            var a = java.net.InetAddress.getByName(addr);
            return a.isLoopbackAddress() || a.isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    static String browser(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("CocCoc")) return "Cốc Cốc";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/")) return "Safari";
        if (ua.toLowerCase().contains("python")) return "Python";
        if (ua.toLowerCase().contains("curl")) return "curl";
        return ua.isBlank() ? "Trình duyệt không rõ" : "Ứng dụng khác";
    }

    static String os(String ua) {
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Mac OS")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return "hệ điều hành không rõ";
    }

    // the /24 network of an IPv4 address (a home router changing the last byte is still the same place)
    private static String network(String ip) {
        int i = ip.lastIndexOf('.');
        return ip.contains(":") || i < 0 ? ip : ip.substring(0, i);
    }

    static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
