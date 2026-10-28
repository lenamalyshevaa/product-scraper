package com.example.priceparser.controller;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class BatchRequest {

    @NotEmpty(message = "список urls не должен быть пустым")
    @Size(max = 50, message = "не больше 50 ссылок за раз")
    private List<String> urls;

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }
}
