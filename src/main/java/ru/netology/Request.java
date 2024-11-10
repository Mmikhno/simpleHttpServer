package ru.netology;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Request {
    private String method;
    String path;
    private String body;
    private URIBuilder uriBuilder;
    private Map<String, String> queryParams = new HashMap<>();

    public Request(String method, String path) {
        this.method = method;
        this.path = path;
        try {
            this.uriBuilder = new URIBuilder(path);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public Request(String method, String path, String body) {
        this.method = method;
        this.path = path;
        try {
            this.uriBuilder = new URIBuilder(path);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        this.body = body;
    }

    public String getMethod() {
        return this.method;
    }

    public Map<String, String> getQueryParams() {
        List<NameValuePair> params = uriBuilder.getQueryParams();
        for (var item : params) {
            String[] param = item.toString().split("=");
            queryParams.put(param[0], param[1]);
        }
        return queryParams;
    }

    public String getQueryParam(String name) {
        return getQueryParams().get(name);
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return uriBuilder.getPath();
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}

