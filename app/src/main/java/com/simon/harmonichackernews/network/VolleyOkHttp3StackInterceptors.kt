package com.simon.harmonichackernews.network

import com.android.volley.AuthFailureError
import com.android.volley.Header
import com.android.volley.Request
import com.android.volley.toolbox.BaseHttpStack
import com.android.volley.toolbox.HttpResponse
import com.simon.harmonichackernews.network.NetworkComponent.okHttpClientInstance
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody

class VolleyOkHttp3StackInterceptors : BaseHttpStack() {
    @Throws(IOException::class, AuthFailureError::class)
    override fun executeRequest(
        request: Request<*>,
        additionalHeaders: MutableMap<String?, String?>
    ): HttpResponse {
        val clientBuilder = okHttpClientInstance!!.newBuilder()
        val timeoutMs = request.getTimeoutMs()

        clientBuilder.connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        clientBuilder.readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        clientBuilder.writeTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)

        val okHttpRequestBuilder = okhttp3.Request.Builder()
        okHttpRequestBuilder.url(request.getUrl())

        val headers = request.getHeaders()
        for (header in headers.entries) {
            okHttpRequestBuilder.addHeader(header.key!!, header.value!!)
        }
        for (header in additionalHeaders.entries) {
            okHttpRequestBuilder.addHeader(header.key!!, header.value!!)
        }

        setConnectionParametersForRequest(okHttpRequestBuilder, request)

        val client = clientBuilder.build()
        val okHttpRequest = okHttpRequestBuilder.build()
        val okHttpCall = client.newCall(okHttpRequest)
        // TODO: close response. Note that it is not as simple as adding try-with-resources because
        //  that would close the response before Volley has had a chance to consume it. At the same
        //  time, not closing the response is also wrong because it will not be closed at all.
        //  Volley closes only input stream created from response body's which is not the same as
        //  closing the response.
        val okHttpResponse = okHttpCall.execute()


        val code = okHttpResponse.code
        val body = okHttpResponse.body
        val content: InputStream = (if (body == null) null else body.byteStream())!!
        val contentLength = if (body == null) 0 else body.contentLength().toInt()
        val responseHeaders = mapHeaders(okHttpResponse.headers)
        //okHttpResponse.close();
        return HttpResponse(code, responseHeaders, contentLength, content)
    }

    private fun mapHeaders(responseHeaders: Headers): MutableList<Header?> {
        val headers: MutableList<Header?> = ArrayList<Header?>()
        var i = 0
        val len = responseHeaders.size
        while (i < len) {
            val name = responseHeaders.name(i)
            val value = responseHeaders.value(i)
            headers.add(Header(name, value))
            i++
        }
        return headers
    }

    companion object {
        private val EMPTY_REQUEST = ByteArray(0).toRequestBody()

        @Throws(AuthFailureError::class)
        private fun setConnectionParametersForRequest(
            builder: okhttp3.Request.Builder,
            request: Request<*>
        ) {
            when (request.getMethod()) {
                Request.Method.DEPRECATED_GET_OR_POST -> {
                    // Ensure backwards compatibility. Volley assumes a request with a null body is a GET.
                    val postBody = request.getBody()
                    if (postBody != null) {
                        builder.post(postBody.toRequestBody(request.getBodyContentType().toMediaTypeOrNull()))
                    }
                }

                Request.Method.GET -> builder.get()
                Request.Method.DELETE -> builder.delete(createRequestBody(request))
                Request.Method.POST -> builder.post(createRequestBody(request))
                Request.Method.PUT -> builder.put(createRequestBody(request))
                Request.Method.HEAD -> builder.head()
                Request.Method.OPTIONS -> builder.method("OPTIONS", null)
                Request.Method.TRACE -> builder.method("TRACE", null)
                Request.Method.PATCH -> builder.patch(createRequestBody(request))
                else -> throw IllegalStateException("Unknown method type.")
            }
        }

        @Throws(AuthFailureError::class)
        private fun createRequestBody(r: Request<*>): RequestBody {
            val body = r.getBody()
            if (body == null) {
                // For POST, PUT and PATCH requests Volley's HurlStack doesn't add body when it's null.
                // However OkHttp requires non-null RequestBody for those methods, so use an empty body.
                return EMPTY_REQUEST
            }
            return body.toRequestBody(r.getBodyContentType().toMediaTypeOrNull())
        }
    }
}
