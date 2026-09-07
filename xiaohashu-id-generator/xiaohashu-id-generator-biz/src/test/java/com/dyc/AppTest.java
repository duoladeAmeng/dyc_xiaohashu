package com.dyc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AppTest {

    @Test
    void mainClassShouldBeLoadable() {
        assertDoesNotThrow(() -> Class.forName(XiaohashuDistributedIdGeneratorBizApplication.class.getName()));
    }
}
