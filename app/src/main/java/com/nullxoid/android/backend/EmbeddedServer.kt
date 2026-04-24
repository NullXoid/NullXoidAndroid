package com.nullxoid.android.backend

import android.content.Context
import com.nullxoid.android.backend.engine.EchoEngine
import com.nullxoid.android.backend.engine.LlmEngine
import com.nullxoid.android.backend.routes.nullxoidRoutes
import com.nullxoid.android.backend.store.SQLiteStore
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Lightweight wrapper around a Ktor CIO server bound to 127.0.0.1.
 * Created by [BackendService] when the user toggles the embedded backend
 * on. The [engine] plug is where a real on-device LLM would swap in.
 */
class EmbeddedServer(
    context: Context,
    private val port: Int,
    val engine: LlmEngine = EchoEngine()
) {
    private val store = SQLiteStore(context)

    private val engineRef: io.ktor.server.engine.ApplicationEngine =
        embeddedServer(CIO, port = port, host = "127.0.0.1") {
            install(DefaultHeaders) {
                header("X-NullXoid-Embedded", "1")
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                })
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        buildJsonObject { put("detail", cause.message ?: "unknown error") }
                    )
                }
            }
            routing {
                nullxoidRoutes(store, engine)
            }
        }

    fun start() {
        engineRef.start(wait = false)
    }

    fun stop() {
        engineRef.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
    }

    val baseUrl: String get() = "http://127.0.0.1:$port"
}
