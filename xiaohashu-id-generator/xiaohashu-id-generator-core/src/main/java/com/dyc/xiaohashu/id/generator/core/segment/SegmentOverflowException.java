package com.dyc.xiaohashu.id.generator.core.segment;

import com.dyc.xiaohashu.id.generator.core.GeneratorUnavailableException;

/**
 * Raised when a segment allocator cannot reserve another positive ID range.
 */
public class SegmentOverflowException extends GeneratorUnavailableException {

    public SegmentOverflowException(String message) {
        super(message);
    }
}
