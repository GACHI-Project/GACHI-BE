package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ClientIpExtractor {
  private static final Pattern IPV4_LITERAL_PATTERN =
      Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
  private static final Pattern IPV6_LITERAL_PATTERN = Pattern.compile("^(?=.*:)[0-9a-fA-F:]+$");

  private final AuthProperties authProperties;

  public String extractClientIp(HttpServletRequest request) {
    String remoteAddr = normalizeIp(request.getRemoteAddr());
    if (!isTrustedProxy(remoteAddr)) {
      return remoteAddr;
    }

    // nginx가 X-Forwarded-For를 append하는 구성일 수 있으므로 위조 영향이 적은 X-Real-IP를 우선 사용한다.
    String realIp = normalizeIp(request.getHeader("X-Real-IP"));
    if (StringUtils.hasText(realIp)) {
      return realIp;
    }

    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedFor)) {
      String clientIp = extractClientIpFromForwardedFor(forwardedFor);
      if (StringUtils.hasText(clientIp)) {
        return clientIp;
      }
    }
    return remoteAddr;
  }

  private String normalizeIp(String rawIp) {
    return StringUtils.hasText(rawIp) ? rawIp.trim() : "";
  }

  private boolean isTrustedProxy(String remoteAddr) {
    if (!StringUtils.hasText(remoteAddr)) {
      return false;
    }
    List<String> trustedProxies = authProperties.getRateLimit().getTrustedProxies();
    if (trustedProxies == null || trustedProxies.isEmpty()) {
      return false;
    }
    return trustedProxies.stream().anyMatch(proxy -> matchesTrustedProxy(remoteAddr, proxy));
  }

  private boolean matchesTrustedProxy(String remoteAddr, String trustedProxy) {
    String normalizedTrustedProxy = normalizeIp(trustedProxy);
    if (!StringUtils.hasText(normalizedTrustedProxy)) {
      return false;
    }
    if (normalizedTrustedProxy.contains("/")) {
      return isInCidr(remoteAddr, normalizedTrustedProxy);
    }
    if (isIpLiteral(remoteAddr) && isIpLiteral(normalizedTrustedProxy)) {
      return isSameIpLiteral(remoteAddr, normalizedTrustedProxy);
    }
    return normalizedTrustedProxy.equals(remoteAddr);
  }

  private boolean isInCidr(String remoteAddr, String cidr) {
    String[] split = cidr.split("/");
    if (split.length != 2) {
      return false;
    }
    if (!isIpLiteral(remoteAddr) || !isIpLiteral(split[0])) {
      return false;
    }
    try {
      InetAddress remoteAddress = InetAddress.getByName(remoteAddr);
      InetAddress networkAddress = InetAddress.getByName(split[0]);
      int prefixLength = Integer.parseInt(split[1]);
      byte[] remoteBytes = remoteAddress.getAddress();
      byte[] networkBytes = networkAddress.getAddress();
      if (remoteBytes.length != networkBytes.length || prefixLength < 0) {
        return false;
      }
      if (prefixLength > remoteBytes.length * 8) {
        return false;
      }

      int fullBytes = prefixLength / 8;
      int remainingBits = prefixLength % 8;
      for (int i = 0; i < fullBytes; i++) {
        if (remoteBytes[i] != networkBytes[i]) {
          return false;
        }
      }
      if (remainingBits == 0) {
        return true;
      }

      int mask = (-1) << (8 - remainingBits);
      return (remoteBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    } catch (UnknownHostException | NumberFormatException e) {
      return false;
    }
  }

  private boolean isIpLiteral(String value) {
    String normalized = normalizeIp(value);
    return IPV4_LITERAL_PATTERN.matcher(normalized).matches()
        || IPV6_LITERAL_PATTERN.matcher(normalized).matches();
  }

  private boolean isSameIpLiteral(String left, String right) {
    try {
      InetAddress leftAddress = InetAddress.getByName(left);
      InetAddress rightAddress = InetAddress.getByName(right);
      return leftAddress.equals(rightAddress);
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private String extractClientIpFromForwardedFor(String forwardedFor) {
    String[] split = forwardedFor.split(",");
    for (int i = split.length - 1; i >= 0; i--) {
      String candidate = normalizeIp(split[i]);
      if (StringUtils.hasText(candidate) && !isTrustedProxy(candidate)) {
        return candidate;
      }
    }
    return "";
  }
}
