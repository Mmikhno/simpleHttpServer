package ru.netology;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Request {
    private String method;
    String path;
    private String body;
    private URIBuilder uriBuilder;
    private Map<String, String> queryParams = new HashMap<>();
    private Map<String, String> headers;

    public Request() {
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

    public String getPostParam(String name) {
        var postParams = getPostParams().stream().filter(o -> o[0].equals(name)).findFirst().get()[1];
        return postParams;
    }

    public List<String[]> getPostParams() {
        List<String[]> postParams = Arrays.stream(body.split("&"))
                .map(o -> o.split("="))
                .filter(o -> o.length > 1)
                .collect(Collectors.toList());
        return postParams;
    }

    public String getPostParamsAsString() {
        StringBuilder sb = new StringBuilder();
        for (String[] item : getPostParams()) {
            sb.append(item[0] + ": " + item[1] + "\n");
        }
        return sb.toString();
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

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeaderByName(String name) {
        String newName = name.replace(":", "").trim();
        return headers.get(newName);
    }

    public String getHeadersAsString() {
        StringBuilder sb = new StringBuilder();
        for (var item : headers.entrySet()) {
            sb = sb.append(item.getKey() + ": " + item.getValue() + "\n");
        }
        return sb.toString();
    }

    private Request(RequestBuilder builder) {
        this.method = builder.method;
        this.uriBuilder = builder.uriBuilder;
        this.path = builder.path;
        this.body = builder.body;
        this.headers = builder.headers;
    }

    public static class RequestBuilder {
        private String method;
        private String path;
        private String body;
        private URIBuilder uriBuilder;
        private Map<String, String> headers;

        public RequestBuilder() {
        }

        public RequestBuilder setMethod(String method) {
            this.method = method;
            return this;
        }

        public RequestBuilder setPath(String path) {
            this.path = path;
            return this;
        }

        public RequestBuilder setBody(String body) {
            this.body = body;
            return this;
        }

        public RequestBuilder setHeaders(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Request build() {
            try {
                uriBuilder = new URIBuilder(path);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            return new Request(this);
        }
    }
}

