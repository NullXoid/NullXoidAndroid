package com.nullxoid.android.data.api

object BackendEndpoint {
    const val LOCAL_DEFAULT_URL = "http://localhost:8090"
    const val EMBEDDED_URL = "http://127.0.0.1:8090"
    const val PUBLIC_ECHOLABS_URL = "https://api.echolabs.diy/nullxoid"

    fun normalize(input: String, fallback: String = LOCAL_DEFAULT_URL): String {
        val raw = input.trim().trimEnd('/')
        if (raw.isBlank()) return fallback
        val withScheme = if ("://" in raw) raw else "${schemeFor(raw)}$raw"
        return withScheme.trimEnd('/')
    }

    fun resolve(baseUrl: String, path: String): String {
        val base = normalize(baseUrl).trimEnd('/')
        val suffix = if (path.startsWith('/')) path else "/$path"
        return base + suffix
    }

    private fun schemeFor(raw: String): String {
        val host = raw
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .trim('[', ']')
            .lowercase()

        return if (isLocalOrLanHost(host)) "http://" else "https://"
    }

    private fun isLocalOrLanHost(host: String): Boolean {
        if (host == "localhost" || host == "10.0.2.2") return true
        if (host.startsWith("127.")) return true
        if (host.startsWith("192.168.")) return true
        if (host.startsWith("10.")) return true
        val parts = host.split('.')
        val second = parts.getOrNull(1)?.toIntOrNull()
        return parts.firstOrNull() == "172" && second != null && second in 16..31
    }
}
