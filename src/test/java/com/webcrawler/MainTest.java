package com.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MainTest {

    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setup() {
        errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void teardown() {
        System.setErr(originalErr);
    }

    @Test
    void shouldReturnErrorCodeWhenNoArguments() {
        String[] args = {};

        int result = Main.run(args);

        assertEquals(1, result);
        assertEquals(
            "Usage: webcrawler <start-url>\n",
            errContent.toString()
        );
    }

    @Test
    void shouldReturnSuccessWhenUrlProvided() {
        String[] args = {"http://example.com"};

        int result = Main.run(args);

        assertEquals(0, result);
    }

}