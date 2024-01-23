package com.dlwlram.sandbox.security;

//自定义Java安全管理器
public class MySecurityManager extends SecurityManager {
    @Override
    public void checkExec(String cmd) {
        super.checkExec(cmd);
    }

    public static void main(String[] args) {
        System.out.println(1);
        System.setSecurityManager(new MySecurityManager());
    }
}
