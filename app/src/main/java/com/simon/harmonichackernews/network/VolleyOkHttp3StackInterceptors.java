package com.simon.harmonichackernews.network;


import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.toolbox.BaseHttpStack;
import com.android.volley.toolbox.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class VolleyOkHttp3StackInterceptors extends BaseHttpStack {

    private static final RequestBody EMPTY_REQUEST = RequestBody.create(new byte[0]);

    public VolleyOkHttp3StackInterceptors() {
    }


    private static void setConnectionParametersForRequest(okhttp3.Request.Builder builder, Request<?> request)
            throws AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                // Ensure backwards compatibility. Volley assumes a request with a null body is a GET.
                byte[] postBody = request.getBody();
                if (postBody != null) {
                    builder.post(RequestBody.create(postBody, MediaType.parse(request.getBodyContentType())));
                }
                break;
            case Request.Method.GET:
                builder.get();
                break;
            case Request.Method.DELETE:
                builder.delete(createRequestBody(request));
                break;
            case Request.Method.POST:
                builder.post(createRequestBody(request));
                break;
            case Request.Method.PUT:
                builder.put(createRequestBody(request));
                break;
            case Request.Method.HEAD:
                builder.head();
                break;
            case Request.Method.OPTIONS:
                builder.method("OPTIONS", null);
                break;
            case Request.Method.TRACE:
                builder.method("TRACE", null);
                break;
            case Request.Method.PATCH:
                builder.patch(createRequestBody(request));
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    private static RequestBody createRequestBody(Request<?> r) throws AuthFailureError {
        final byte[] body = r.getBody();
        if (body == null) {
            // For POST, PUT and PATCH requests Volley's HurlStack doesn't add body when it's null.
            // However OkHttp requires non-null RequestBody for those methods, so use an empty body.
            return EMPTY_REQUEST;
        }
        return RequestBody.create(body, MediaType.parse(r.getBodyContentType()));
    }

    @Override
    public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError {
        OkHttpClient.Builder clientBuilder = NetworkComponent.getOkHttpClientInstance().newBuilder();
        int timeoutMs = request.getTimeoutMs();

        clientBuilder.connectTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        clientBuilder.readTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        clientBuilder.writeTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        okhttp3.Request.Builder okHttpRequestBuilder = new okhttp3.Request.Builder();
        okHttpRequestBuilder.url(request.getUrl());

        Map<String, String> headers = request.getHeaders();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            okHttpRequestBuilder.addHeader(header.getKey(), header.getValue());
        }
        for (Map.Entry<String, String> header : additionalHeaders.entrySet()) {
            okHttpRequestBuilder.addHeader(header.getKey(), header.getValue());
        }

        setConnectionParametersForRequest(okHttpRequestBuilder, request);

        OkHttpClient client = clientBuilder.build();
        okhttp3.Request okHttpRequest = okHttpRequestBuilder.build();
        Call okHttpCall = client.newCall(okHttpRequest);
        // TODO: close response. Note that it is not as simple as adding try-with-resources because
        //  that would close the response before Volley has had a chance to consume it. At the same
        //  time, not closing the response is also wrong because it will not be closed at all.
        //  Volley closes only input stream created from response body's which is not the same as
        //  closing the response.
        Response okHttpResponse = okHttpCall.execute();


        int code = okHttpResponse.code();
        ResponseBody body = okHttpResponse.body();
        InputStream content = body == null ? null : body.byteStream();
        int contentLength = body == null ? 0 : (int) body.contentLength();
        List<Header> responseHeaders = mapHeaders(okHttpResponse.headers());
        //okHttpResponse.close();
        return new HttpResponse(code, responseHeaders, contentLength, content);
    }

    private List<Header> mapHeaders(Headers responseHeaders) {
        List<Header> headers = new ArrayList<>();
        for (int i = 0, len = responseHeaders.size(); i < len; i++) {
            final String name = responseHeaders.name(i), value = responseHeaders.value(i);
            headers.add(new Header(name, value));
        }
        return headers;
    }
}