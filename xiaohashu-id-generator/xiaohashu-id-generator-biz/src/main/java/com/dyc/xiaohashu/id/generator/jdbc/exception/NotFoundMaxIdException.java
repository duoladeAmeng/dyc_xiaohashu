package com.dyc.xiaohashu.id.generator.jdbc.exception;

import com.dyc.xiaohashu.id.generator.core.IdGeneratorException;

public class NotFoundMaxIdException extends IdGeneratorException {

    public NotFoundMaxIdException(String namespacedName) {
        super(String.format("Not found max id:[%s].", namespacedName));
    }
}
