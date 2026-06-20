package com.cyrain.chatscope.registration;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP and its coarse network block from an inbound request.
 *
 * <p>Honors {@code X-Forwarded-For} / {@code X-Real-IP} set by the reverse proxy, then derives the
 * abuse-relevant network: IPv4 {@code /24}, IPv6 {@code /64}.
 */
record ClientNetwork(String ip, String cidr) {

    static ClientNetwork from(HttpServletRequest request) {
        String ip = firstForwarded(request.getHeader("X-Forwarded-For"));
        if (ip == null) {
            ip = blankToNull(request.getHeader("X-Real-IP"));
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return new ClientNetwork(ip, cidrOf(ip));
    }

    private static String firstForwarded(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.split(",")[0].trim();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String cidrOf(String ip) {
        if (ip == null) {
            return null;
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < 4 && i < parts.length; i++) {
                prefix.append(parts[i].isEmpty() ? "0" : parts[i]).append(':');
            }
            return prefix + ":/64";
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        return ip.substring(0, lastDot) + ".0/24";
    }
}
