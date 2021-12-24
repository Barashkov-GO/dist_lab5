public class MessageSet {
    private String url;
    private Long responseTime;

    public MessageSet(String url. Long time){
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