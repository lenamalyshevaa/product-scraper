package com.example.priceparser.model;

public class ParseError {

    private String url;
    private String message;

    public ParseError(String url, String message) {
        this.url = url;
        this.message = message;
    }

    public String getUrl() {
        return url;
    }

    public String getMessage() {
        return message;
    }
}
