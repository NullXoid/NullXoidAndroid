package com.nullxoid.android.data.api

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cookie jar keyed by host. Cookies are kept in memory and mirrored into
 * app-private storage after [attach] so hosted login survives app restarts.
 */
class PersistentCookieJar : CookieJar {
    private val prefsName = "nullxoid_cookies"
    private val cookiesKey = "cookies"
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()
    @Volatile
    private var appContext: Context? = null

    @Synchronized
    fun attach(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        loadPersisted()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val bucket = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { cookie ->
            bucket.removeAll { it.name == cookie.name && it.path == cookie.path }
            bucket.add(cookie)
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val bucket = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val changed = bucket.removeAll { it.expiresAt < now }
        if (changed) persist()
        return bucket.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        store.clear()
        preferences()?.edit()?.remove(cookiesKey)?.apply()
    }

    @Synchronized
    fun csrfToken(host: String): String? =
        store[host]?.firstOrNull { it.name.equals("nx_csrf", ignoreCase = true) }?.value

    private fun preferences() =
        appContext?.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun loadPersisted() {
        val raw = preferences()?.getString(cookiesKey, null).orEmpty()
        if (raw.isBlank()) return
        runCatching {
            val now = System.currentTimeMillis()
            val items = JSONArray(raw)
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val host = item.optString("host").takeIf { it.isNotBlank() } ?: continue
                val cookie = item.toCookie() ?: continue
                if (cookie.expiresAt < now) continue
                store.getOrPut(host) { mutableListOf() }.add(cookie)
            }
        }
    }

    private fun persist() {
        val items = JSONArray()
        val now = System.currentTimeMillis()
        store.forEach { (host, cookies) ->
            cookies.filter { it.expiresAt >= now }.forEach { cookie ->
                items.put(cookie.toJson(host))
            }
        }
        preferences()?.edit()?.putString(cookiesKey, items.toString())?.apply()
    }

    private fun Cookie.toJson(host: String) = JSONObject()
        .put("host", host)
        .put("name", name)
        .put("value", value)
        .put("expiresAt", expiresAt)
        .put("domain", domain)
        .put("path", path)
        .put("secure", secure)
        .put("httpOnly", httpOnly)
        .put("hostOnly", hostOnly)
        .put("persistent", persistent)

    private fun JSONObject.toCookie(): Cookie? = runCatching {
        val builder = Cookie.Builder()
            .name(getString("name"))
            .value(getString("value"))
            .path(optString("path", "/").ifBlank { "/" })
        val domain = getString("domain")
        if (optBoolean("hostOnly", false)) {
            builder.hostOnlyDomain(domain)
        } else {
            builder.domain(domain)
        }
        if (optBoolean("secure", false)) builder.secure()
        if (optBoolean("httpOnly", false)) builder.httpOnly()
        if (optBoolean("persistent", false)) builder.expiresAt(getLong("expiresAt"))
        builder.build()
    }.getOrNull()
}

/**
 * Attaches X-CSRF-Token (from the nx_csrf cookie) to every
 * mutating request. Mirrors NullXoidBackendBridge::attachJsonRequestHeaders.
 */
class CsrfInterceptor(private val cookieJar: PersistentCookieJar) : Interceptor {
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
    val cookieJar = PersistentCookieJar()

    fun init(context: Context) {
        cookieJar.attach(context)
    }

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
