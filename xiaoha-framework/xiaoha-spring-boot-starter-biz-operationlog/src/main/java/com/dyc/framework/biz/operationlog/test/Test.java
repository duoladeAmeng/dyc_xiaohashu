package com.dyc.framework.biz.operationlog.test;

import com.dyc.framework.biz.operationlog.aspect.ApiOperationLog;

public class Test {
    public static void main(String[] args) {
        new Test(). t1();
    }

    @ApiOperationLog(description = "sssssssss")
    public  void t1(){
        System.out.println("ssssss");
    }
}
