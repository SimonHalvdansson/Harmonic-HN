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

object NetworkComponent {
    val USER_AGENT: String =
        "Harmonic-HN-Android/" + BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_TYPE

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()
        chain.proceed(request)
    }

    val okHttpClientInstance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .build()
    }

    @Volatile
    private var okHttpClientCookieInstance: OkHttpClient? = null

    private var requestQueueInstance: RequestQueue? = null

    val okHttpClientInstanceWithCookies: OkHttpClient
        get() {
            okHttpClientCookieInstance?.let { return it }
            return synchronized(NetworkComponent::class.java) {
                okHttpClientCookieInstance ?: createCookieClient().also {
                    okHttpClientCookieInstance = it
                }
            }
        }

    private fun createCookieClient(): OkHttpClient {
        val cookieManager = CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }
        return OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .addInterceptor(userAgentInterceptor)
            .build()
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

        return requestQueueInstance ?: Volley.newRequestQueue(
            context.applicationContext,
            VolleyOkHttp3StackInterceptors()
        ).also { requestQueueInstance = it }
    }

    fun removeCachedStoryResponses(context: Context?, storyId: Int) {
        if (context == null || storyId <= 0) {
            return
        }

        val requestQueue = getRequestQueueInstance(context)
        requestQueue.cache.remove("https://hn.algolia.com/api/v1/items/$storyId")
        requestQueue.cache.remove(
            "https://hacker-news.firebaseio.com/v0/item/" + storyId + ".json"
        )
    }
}
