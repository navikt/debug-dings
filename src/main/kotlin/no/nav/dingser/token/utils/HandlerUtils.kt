package no.nav.dingser.token.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

class HandlerUtils {

    internal suspend fun <T> tryRequest(
        callName: String,
        path: String,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            log.warn { "Error from request url: $path " }
            throw IllegalStateException(e).also {
                log.error { "$callName - Error Message: ${e.message}" }
            }
        }
    }

    @KtorExperimentalAPI
    internal val defaultHttpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer { objectMapper }
        }
    }
}

internal val objectMapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .configure(SerializationFeature.INDENT_OUTPUT, true)
