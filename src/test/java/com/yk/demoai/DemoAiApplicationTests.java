package com.yk.demoai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DemoAiApplicationTests {

    @Test
    void shouldExposeMainMethod() {
        assertDoesNotThrow(() -> DemoAiApplication.class.getDeclaredMethod("main", String[].class));
    }
}
