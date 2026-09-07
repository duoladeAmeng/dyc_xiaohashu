package com.dyc.xiaohashu.id.generator.core.segment.grouped;

import java.util.Objects;

public final class GroupedAccessor {

    private static final ThreadLocal<GroupedKey> CURRENT = new ThreadLocal<>();

    private GroupedAccessor() {
    }

    public static void set(GroupedKey groupedKey) {
        CURRENT.set(groupedKey);
    }

    public static void setIfNotNever(GroupedKey groupedKey) {
        if (GroupedKey.NEVER.equals(groupedKey)) {
            return;
        }
        set(groupedKey);
    }

    public static GroupedKey get() {
        return CURRENT.get();
    }

    public static GroupedKey requiredGet() {
        return Objects.requireNonNull(get(), "The current thread has not set the GroupedKey.");
    }

    public static void clear() {
        CURRENT.remove();
    }
}
