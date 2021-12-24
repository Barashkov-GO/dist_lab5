package ru.barashkov.distributed.lab5;

public class MessageGet {
    private final String url;
    private final Long responseTime;

    public MessageGet(String url, Long time){
        this.url = url;
        this.responseTime = time;
    }

    public String getUrl() {
        return this.url;
    }

    public Long getResponseTime() {
        return this.responseTime;
    }
}