package com.dyc.xiaohashu.id.generator.jdbc.exception;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

public class SegmentNameMissingException extends IdGeneratorException {

    private final String namespace;
    private final String name;

    public SegmentNameMissingException(String namespace, String name) {
        super(String.format("Segment:[%s.%s] missing.", namespace, name));
        this.namespace = namespace;
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }
}
