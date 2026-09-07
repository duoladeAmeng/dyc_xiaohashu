package com.dyc.xiaohashu.id.generator.core.machine;

public class GuardianState {

    public static final GuardianState INITIAL = new GuardianState(0, null);

    private final long guardAt;
    private final Throwable error;

    public GuardianState(long guardAt, Throwable error) {
        this.guardAt = guardAt;
        this.error = error;
    }

    public long getGuardAt() {
        return guardAt;
    }

    public Throwable getError() {
        return error;
    }

    public boolean isFailed() {
        return error != null;
    }

    public static GuardianState success(long guardAt) {
        return new GuardianState(guardAt, null);
    }

    public static GuardianState failed(long guardAt, Throwable error) {
        return new GuardianState(guardAt, error);
    }
}
