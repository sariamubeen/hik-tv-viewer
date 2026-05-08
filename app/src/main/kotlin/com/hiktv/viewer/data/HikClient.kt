package com.hiktv.viewer.data

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object HikClient {

    fun newOkHttp(settings: Settings): OkHttpClient {
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
        val authenticator = DigestAuthenticator(Credentials(settings.username, settings.password))
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache))
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Returns parsed device-info XML body on success, throws on failure. */
    fun fetchDeviceInfo(settings: Settings): String {
        val client = newOkHttp(settings)
        val req = Request.Builder().url(settings.deviceInfoUrl()).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}: ${body.take(200)}")
            }
            return body
        }
    }
}
