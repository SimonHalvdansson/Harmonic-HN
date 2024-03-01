package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.simon.harmonichackernews.BuildConfig;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkComponent {
    private static volatile OkHttpClient okHttpClientInstance;
    private static RequestQueue requestQueueInstance;

    public static OkHttpClient getOkHttpClientInstance() {
        if (okHttpClientInstance == null) {
            synchronized (NetworkComponent.class) {
                if (okHttpClientInstance == null) {
                    Interceptor userAgentInterceptor = new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request originalRequest = chain.request();
                            Request requestWithUserAgent = originalRequest.newBuilder()
                                    .header("User-Agent", "Harmonic-HN-Android/" + BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_TYPE)
                                    .build();
                            return chain.proceed(requestWithUserAgent);
                        }
                    };

                    okHttpClientInstance = new OkHttpClient.Builder()
                            .addInterceptor(userAgentInterceptor)
                            .build();
                }
            }
        }
        return okHttpClientInstance;
    }

    public static RequestQueue getRequestQueueInstance(Context context) {
        if (BuildConfig.DEBUG && !Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("getRequestQueueInstance currently doesn't support multithreaded access");
        }

        if (requestQueueInstance == null) {
            requestQueueInstance = Volley.newRequestQueue(context.getApplicationContext(),
                    new VolleyOkHttp3StackInterceptors());
        }
        return requestQueueInstance;
    }
}
