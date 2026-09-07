package com.dyc.xiaohashu.id.generator.core.machine;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

/**
 * Raised when all configured machine IDs are already occupied.
 */
public class MachineIdOverflowException extends IdGeneratorException {

    public MachineIdOverflowException(String message) {
        super(message);
    }
}
