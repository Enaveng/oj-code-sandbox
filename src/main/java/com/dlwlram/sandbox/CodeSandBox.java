package com.dlwlram.sandbox;


import com.dlwlram.sandbox.model.ExecuteCodeRequest;
import com.dlwlram.sandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱职责:
 * 得到一组输入用例 将代码编译运行之后返回给判题服务模块
 */

//定义代码沙箱的接口
public interface CodeSandBox {
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
