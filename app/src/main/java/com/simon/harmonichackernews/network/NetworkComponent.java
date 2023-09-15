package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Looper;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.simon.harmonichackernews.BuildConfig;

import okhttp3.OkHttpClient;

public class NetworkComponent {
    private static volatile OkHttpClient okHttpClientInstance;
    private static RequestQueue requestQueueInstance;

    public static OkHttpClient getOkHttpClientInstance() {
        if (okHttpClientInstance == null) {
            synchronized (NetworkComponent.class) {
                if (okHttpClientInstance == null) {
                    okHttpClientInstance = new OkHttpClient();
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
