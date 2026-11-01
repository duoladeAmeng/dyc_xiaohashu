package com.dyc.core;

import com.dyc.core.common.Result;

public interface IDGen {
    Result get(String key);
    boolean init();
}
