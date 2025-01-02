package io.kestra.core.http.client;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import jakarta.annotation.Nullable;
import lombok.Getter;

@Getter
public class HttpClientResponseException extends HttpClientException {
    protected HttpRequest request;

    @Nullable
    protected HttpResponse<?> response;

    public HttpClientResponseException(String message, HttpResponse<?> response) {
        super(message);
        this.request = response.getRequest();
        this.response = response;
    }

    public HttpClientResponseException(String message, HttpResponse<?> response, final Throwable cause) {
        super(message, cause);
        this.request = response.getRequest();
        this.response = response;
    }
}
