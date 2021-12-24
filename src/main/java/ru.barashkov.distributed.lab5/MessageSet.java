package ru.barashkov.distributed.lab5;

public class MessageSet {
    private final String url;
    private final Long responseTime;

    public MessageSet(String url, Long time){
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