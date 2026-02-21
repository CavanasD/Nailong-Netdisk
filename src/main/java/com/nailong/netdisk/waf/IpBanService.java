package com.nailong.netdisk.waf;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpBanService {

    private static final long FAIL_WINDOW_MS = 5L * 60 * 1000;
    private static final long BAN_MS = 5L * 60 * 1000;
    private static final int FAIL_THRESHOLD = 3;

    private static final class Counter {
        private long firstTs;
        private int count;

        private Counter(long firstTs, int count) {
            this.firstTs = firstTs;
            this.count = count;
        }
    }

    private final ConcurrentHashMap<String, Counter> captchaFailCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> bannedUntilByIp = new ConcurrentHashMap<>();

    public BanStatus getBanStatus(String ip) {
        if (ip == null || ip.isBlank()) {
            return BanStatus.notBanned();
        }
        Long until = bannedUntilByIp.get(ip);
        if (until == null) {
            return BanStatus.notBanned();
        }
        long now = System.currentTimeMillis();
        long remainingMs = until - now;
        if (remainingMs <= 0) {
            bannedUntilByIp.remove(ip, until);
            return BanStatus.notBanned();
        }
        long remainingSeconds = Math.max(1, (remainingMs + 999) / 1000);
        return BanStatus.banned(remainingSeconds);
    }

    public boolean isBanned(String ip) {
        return getBanStatus(ip).banned();
    }

    /**
     * Record a captcha failure for an (ip, username) pair.
     * If failures reach threshold within window, bans the IP.
     * @return current fail count within window
     */
    public int recordCaptchaFailure(String ip, String username) {
        String key = key(ip, username);
        long now = System.currentTimeMillis();

        Counter updated = captchaFailCounters.compute(key, (k, v) -> {
            if (v == null) {
                return new Counter(now, 1);
            }
            if (now - v.firstTs > FAIL_WINDOW_MS) {
                v.firstTs = now;
                v.count = 1;
                return v;
            }
            v.count += 1;
            return v;
        });

        if (updated.count >= FAIL_THRESHOLD) {
            banIp(ip);
        }

        return updated.count;
    }

    public void clearCaptchaFailures(String ip, String username) {
        captchaFailCounters.remove(key(ip, username));
    }

    public void banIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        bannedUntilByIp.put(ip, System.currentTimeMillis() + BAN_MS);
    }

    private String key(String ip, String username) {
        return Objects.toString(ip, "") + "|" + Objects.toString(username, "");
    }

    public record BanStatus(boolean banned, long remainingSeconds) {
        public static BanStatus banned(long remainingSeconds) {
            return new BanStatus(true, remainingSeconds);
        }

        public static BanStatus notBanned() {
            return new BanStatus(false, 0);
        }
    }
}
