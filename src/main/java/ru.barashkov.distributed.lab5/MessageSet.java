package ru.barashkov.distributed.lab5;

public class MessageSet {
    private final String url;
    private final Integer responseTime;

    public MessageSet(String url, Integer time){
        this.url = url;
        this.responseTime = time;
    }

    public String getUrl() {
        return this.url;
    }

    public Integer getResponseTime() {
        return this.responseTime;
    }
}