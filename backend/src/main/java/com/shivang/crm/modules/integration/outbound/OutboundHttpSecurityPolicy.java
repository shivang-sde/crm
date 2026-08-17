package com.shivang.crm.modules.integration.outbound;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class OutboundHttpSecurityPolicy {

    private static final Set<Integer> ALLOWED_PORTS = Set.of(443);

    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Outbound URL is required");
        }
        URI uri = URI.create(rawUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only HTTPS outbound URLs are allowed");
        }
        if (uri.getUserInfo() != null || uri.getHost() == null) {
            throw new IllegalArgumentException("Outbound URL must contain a hostname without user info");
        }
        int port = uri.getPort() < 0 ? 443 : uri.getPort();
        if (!ALLOWED_PORTS.contains(port)) {
            throw new IllegalArgumentException("Outbound URL port is not allowed");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Outbound URL resolves to a restricted address");
                }
                if (address.getHostAddress().startsWith("169.254.")) {
                    throw new IllegalArgumentException("Cloud metadata addresses are not allowed");
                }
                if (isIpv6UniqueLocal(address) || isIpv6Documentation(address)) {
                    throw new IllegalArgumentException("Outbound URL resolves to a restricted IPv6 address");
                }
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Outbound hostname cannot be resolved", ex);
        }
        return uri;
    }

    private boolean isIpv6UniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private boolean isIpv6Documentation(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16
            && (bytes[0] & 0xFF) == 0x20
            && (bytes[1] & 0xFF) == 0x01
            && (bytes[2] & 0xFF) == 0x0D
            && (bytes[3] & 0xFF) == 0xB8;
    }
}