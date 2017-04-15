package com.danielflower.crank4j.protocol;

import java.io.IOException;

public class HeadersBuilder {
    private final StringBuilder headers = new StringBuilder();

    public void appendHeader(String header, String value) throws IOException {
        headers.append(header).append(":").append(value).append("\n");
    }

    @Override
    public String toString() {
        return headers.toString();
    }
}
