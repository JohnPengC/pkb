package com.pkb.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsRendererTest {

    @Test
    void disabledRendererReturnsEmpty() {
        JsRenderer renderer = new JsRenderer(false);
        assertThat(renderer.render("http://localhost/anything")).isEmpty();
        assertThat(renderer.isEnabled()).isFalse();
    }

    @Test
    void enabledFlagIsExposed() {
        assertThat(new JsRenderer(true).isEnabled()).isTrue();
    }
}
