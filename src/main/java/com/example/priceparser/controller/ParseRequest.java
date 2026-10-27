package com.example.priceparser.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ParseRequest {

    @NotBlank(message = "url не должен быть пустым")
    @Pattern(regexp = "https?://\\S+", message = "url должен начинаться с http:// или https:// и быть без пробелов")
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
