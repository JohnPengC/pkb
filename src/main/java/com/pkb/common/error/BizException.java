package com.pkb.common.error;

/** 业务异常：message 直接面向用户展示（中文）。 */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
