package com.dlwlram.sandbox.utils;

import com.dlwlram.sandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 封装编译以及运行程序的工具类
 *
 * 使用spring的工具类来统计代码的运行时间
 *
 */
public class RunProcessUtils {
    public static ExecuteMessage RunProcessAndGetMessage(Process compileProcess, String opType) {
        //创建返回对象
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            //等待命令执行完毕之后得到一个返回码
            int exitValue = compileProcess.waitFor();
            if (exitValue == 0) { //表示执行成功
                System.out.println("程序" + opType + "成功");
                //分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
                //逐条读取并使用StringBuilder进行字符串拼接
                StringBuilder stringBuilder = new StringBuilder();
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    stringBuilder.append(compileOutputLine);
                }
                executeMessage.setExitValue(exitValue);
                executeMessage.setSuccessMessage(stringBuilder.toString());
            } else {
                System.out.println("程序" + opType + "失败"+ "错误码为" + exitValue);
                //分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()));
                //逐条读取并使用StringBuilder进行字符串拼接
                StringBuilder stringBuilder = new StringBuilder();
                String compileOutputLine;
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    stringBuilder.append(compileOutputLine);
                }
                //分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
                //逐条读取并使用StringBuilder进行字符串拼接
                StringBuilder errorStringBuilder = new StringBuilder();
                String compileErrorOutputLine;
                while ((compileErrorOutputLine = errorBufferedReader.readLine()) != null) {
                    errorStringBuilder.append(compileErrorOutputLine);
                }
                executeMessage.setExitValue(exitValue);
                executeMessage.setSuccessMessage(stringBuilder.toString());
                executeMessage.setErrorMessage(errorStringBuilder.toString());
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
                stopWatch.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}