package com.dyc.xiaohashu.id.generator.core.segment.grouped;

import com.dyc.xiaohashu.id.generator.core.segment.IdSegment;
import com.dyc.xiaohashu.id.generator.core.segment.Clock;

import java.util.Objects;

public final class GroupedKey {

    public static final GroupedKey NEVER = new GroupedKey("", IdSegment.TIME_TO_LIVE_FOREVER);

    private final String key;
    private final long ttlAt;

    public GroupedKey(String key, long ttlAt) {
        this.key = key;
        this.ttlAt = ttlAt;
    }

    public String getKey() {
        return key;
    }

    public long getTtlAt() {
        return ttlAt;
    }

    public long ttl() {
        return ttlAt - Clock.CACHE.secondTime();
    }

    public static GroupedKey forever(String key) {
        return new GroupedKey(key, IdSegment.TIME_TO_LIVE_FOREVER);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GroupedKey that = (GroupedKey) o;
        return ttlAt == that.ttlAt && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, ttlAt);
    }

    @Override
    public String toString() {
        return "GroupedKey{key='" + key + "', ttlAt=" + ttlAt + '}';
    }
}
