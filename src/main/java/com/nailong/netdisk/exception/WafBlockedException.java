package com.nailong.netdisk.exception;

public class WafBlockedException extends RuntimeException {

    private final String secId;
    private final String userId;
    private final String riskName;
    private final boolean banned;
    private final long remainSeconds;
    private final String details;

    public WafBlockedException(String secId, String userId, String riskName) {
        super("Nailong Defender Warning! Hacker detected");
        this.secId = secId;
        this.userId = userId;
        this.riskName = riskName;
        this.banned = false;
        this.remainSeconds = 0;
        this.details = null;
    }

    public WafBlockedException(String secId, String userId, String riskName, boolean banned, long remainSeconds, String details) {
        super("Nailong Defender Warning! Hacker detected");
        this.secId = secId;
        this.userId = userId;
        this.riskName = riskName;
        this.banned = banned;
        this.remainSeconds = remainSeconds;
        this.details = details;
    }

    public String getSecId() {
        return secId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRiskName() {
        return riskName;
    }

    public boolean isBanned() {
        return banned;
    }

    public long getRemainSeconds() {
        return remainSeconds;
    }

    public String getDetails() {
        return details;
    }
}
