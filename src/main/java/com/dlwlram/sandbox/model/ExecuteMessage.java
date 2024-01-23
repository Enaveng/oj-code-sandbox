package com.dlwlram.sandbox.model;


import lombok.Data;

@Data
public class ExecuteMessage {
    private Integer exitValue;

    private String successMessage;

    private String errorMessage;

    private Long time;

    private Long memory;

}
