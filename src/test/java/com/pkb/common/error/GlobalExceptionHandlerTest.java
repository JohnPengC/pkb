package com.pkb.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void bizExceptionReturns400WithMessage() {
        var resp = handler.biz(new BizException("该内容已导入过"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).containsEntry("message", "该内容已导入过");
    }

    @Test
    void unknownExceptionHidesDetails() {
        var resp = handler.unknown(new RuntimeException("数据库连接串 jdbc:..."));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().get("message")).doesNotContain("jdbc");
    }
}
