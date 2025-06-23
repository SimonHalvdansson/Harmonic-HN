package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Looper;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.simon.harmonichackernews.BuildConfig;

import java.net.CookieManager;
import java.net.CookiePolicy;

import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class NetworkComponent {
    private static volatile OkHttpClient okHttpClientInstance;
    private static volatile OkHttpClient okHttpClientCookieInstance;

    private static RequestQueue requestQueueInstance;

    public static OkHttpClient getOkHttpClientInstance() {
        if (okHttpClientInstance == null) {
            synchronized (NetworkComponent.class) {
                if (okHttpClientInstance == null) {
                    // set up an in-memory cookie store
                    Interceptor userAgentInterceptor = chain -> {
                        Request original = chain.request();
                        Request withAgent = original.newBuilder()
                                .header("User-Agent",
                                        "Harmonic-HN-Android/" + BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_TYPE)
                                .build();
                        return chain.proceed(withAgent);
                    };

                    okHttpClientInstance = new OkHttpClient.Builder()
                            .addInterceptor(userAgentInterceptor)
                            .build();
                }
            }
        }
        return okHttpClientInstance;
    }

    public static OkHttpClient getOkHttpClientInstanceWithCookies() {
        if (okHttpClientCookieInstance == null) {
            synchronized (NetworkComponent.class) {
                if (okHttpClientCookieInstance == null) {
                    // set up an in-memory cookie store
                    CookieManager cookieManager = new CookieManager();
                    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

                    Interceptor userAgentInterceptor = chain -> {
                        Request original = chain.request();
                        Request withAgent = original.newBuilder()
                                .header("User-Agent",
                                        "Harmonic-HN-Android/" + BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_TYPE)
                                .build();
                        return chain.proceed(withAgent);
                    };

                    okHttpClientCookieInstance = new OkHttpClient.Builder()
                            .cookieJar(new JavaNetCookieJar(cookieManager))
                            .addInterceptor(userAgentInterceptor)
                            .build();
                }
            }
        }
        return okHttpClientCookieInstance;
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
