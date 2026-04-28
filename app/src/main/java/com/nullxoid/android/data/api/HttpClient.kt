package com.nullxoid.android.data.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * In-memory cookie jar, keyed by host. Matches the desktop bridge's
 * QNetworkCookieJar semantics (session cookies persisted for the app
 * lifetime; cleared on logout via [clear]).
 */
class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val bucket = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { cookie ->
            bucket.removeAll { it.name == cookie.name && it.path == cookie.path }
            bucket.add(cookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val bucket = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        bucket.removeAll { it.expiresAt < now }
        return bucket.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = store.clear()

    @Synchronized
    fun csrfToken(host: String): String? =
        store[host]?.firstOrNull { it.name.equals("nx_csrf", ignoreCase = true) }?.value
}

/**
 * Attaches X-CSRF-Token (from the nx_csrf cookie) to every
 * mutating request. Mirrors NullXoidBackendBridge::attachJsonRequestHeaders.
 */
class CsrfInterceptor(private val cookieJar: InMemoryCookieJar) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val method = original.method.uppercase()
        val needsCsrf = method != "GET" && method != "HEAD" && method != "OPTIONS"
        if (!needsCsrf) return chain.proceed(original)

        val token = cookieJar.csrfToken(original.url.host) ?: return chain.proceed(original)
        val patched = original.newBuilder()
            .header("X-CSRF-Token", token)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        return chain.proceed(patched)
    }
}

object HttpClient {
    val cookieJar = InMemoryCookieJar()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(CsrfInterceptor(cookieJar))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // no read timeout — SSE streams
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
