package com.dlwlram.sandbox.security;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Permission;

public class TestSecurityManager extends SecurityManager{

    //开启所有权限校验
    @Override
    public void checkPermission(Permission perm) {
        super.checkPermission(perm);
    }

    public static void main(String[] args) {
        System.setSecurityManager(new TestSecurityManager());
        ResourceUtil.readStr("testCode/TimeOut.java", StandardCharsets.UTF_8);
    }
}
