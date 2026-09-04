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

    fun newOkHttp(device: Device): OkHttpClient {
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
        val authenticator = DigestAuthenticator(Credentials(device.username, device.password))
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            // Was 15s: a single slow snapshot frame could stall a tile that long.
            .readTimeout(5, TimeUnit.SECONDS)
            .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache))
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Returns parsed device-info XML body on success, throws on failure. */
    fun fetchDeviceInfo(device: Device): String {
        val client = newOkHttp(device)
        val req = Request.Builder().url(device.deviceInfoUrl()).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}: ${body.take(200)}")
            }
            return body
        }
    }

    /**
     * GETs an arbitrary ISAPI path off [device]'s digest-authenticated client.
     * Returns the raw XML body. Throws on a transport failure or non-2xx/4xx
     * response; a 4xx with a `notSupport`/`invalidOperation` ResponseStatus body
     * (endpoint doesn't apply to this device type) is returned as-is for the
     * caller to interpret, not thrown.
     */
    fun get(device: Device, client: OkHttpClient, path: String): String {
        val req = Request.Builder().url("${device.httpBaseUrl()}$path").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful && resp.code !in 400..499) {
                throw RuntimeException("HTTP ${resp.code}: ${body.take(200)}")
            }
            return body
        }
    }
}
