package ru.barashkov.distributed.lab5;

public class MessageGet {
    private String url;

    public MessageGet(String url){
        this.url = url;
    }

    public String getUrl() {
        return this.url;
    }
}