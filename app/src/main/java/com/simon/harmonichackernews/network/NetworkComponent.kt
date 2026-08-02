package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Looper
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.simon.harmonichackernews.BuildConfig
import java.net.CookieManager
import java.net.CookiePolicy
import kotlin.concurrent.Volatile
import okhttp3.Interceptor
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request

object NetworkComponent {
    val USER_AGENT: String =
        "Harmonic-HN-Android/" + BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_TYPE

    @Volatile
    var okHttpClientInstance: OkHttpClient? = null
        get() {
            if (field == null) {
                synchronized(NetworkComponent::class.java) {
                    if (field == null) {
                        // set up an in-memory cookie store
                        val userAgentInterceptor =
                            Interceptor { chain: Interceptor.Chain? ->
                                val original = chain!!.request()
                                val withAgent = original.newBuilder()
                                    .header("User-Agent", USER_AGENT)
                                    .build()
                                chain.proceed(withAgent)
                            }

                        field = OkHttpClient.Builder()
                            .addInterceptor(userAgentInterceptor)
                            .build()
                    }
                }
            }
            return field
        }
        private set

    @Volatile
    private var okHttpClientCookieInstance: OkHttpClient? = null

    private var requestQueueInstance: RequestQueue? = null

    val okHttpClientInstanceWithCookies: OkHttpClient?
        get() {
            if (okHttpClientCookieInstance == null) {
                synchronized(NetworkComponent::class.java) {
                    if (okHttpClientCookieInstance == null) {
                        // set up an in-memory cookie store
                        val cookieManager = CookieManager()
                        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)

                        val userAgentInterceptor =
                            Interceptor { chain: Interceptor.Chain? ->
                                val original = chain!!.request()
                                val withAgent = original.newBuilder()
                                    .header("User-Agent", USER_AGENT)
                                    .build()
                                chain.proceed(withAgent)
                            }

                        okHttpClientCookieInstance = OkHttpClient.Builder()
                            .cookieJar(JavaNetCookieJar(cookieManager))
                            .addInterceptor(userAgentInterceptor)
                            .build()
                    }
                }
            }
            return okHttpClientCookieInstance
        }

    fun resetOkHttpClientCookieInstance() {
        synchronized(NetworkComponent::class.java) {
            okHttpClientCookieInstance = null
        }
    }

    fun getRequestQueueInstance(context: Context): RequestQueue {
        check(
            !(BuildConfig.DEBUG && !Looper.getMainLooper().isCurrentThread())
        ) { "getRequestQueueInstance currently doesn't support multithreaded access" }

        if (requestQueueInstance == null) {
            requestQueueInstance = Volley.newRequestQueue(
                context.getApplicationContext(),
                VolleyOkHttp3StackInterceptors()
            )
        }
        return requestQueueInstance!!
    }

    fun removeCachedStoryResponses(context: Context?, storyId: Int) {
        if (context == null || storyId <= 0) {
            return
        }

        val requestQueue = getRequestQueueInstance(context)
        requestQueue.getCache().remove("https://hn.algolia.com/api/v1/items/" + storyId)
        requestQueue.getCache().remove(
            "https://hacker-news.firebaseio.com/v0/item/" + storyId + ".json"
        )
    }
}
