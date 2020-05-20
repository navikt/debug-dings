package no.nav.dingser.service

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import no.nav.dingser.token.utils.HandlerUtils

class OutboundApiService(
    private val accessToken: String,
    private val handlerUtils: HandlerUtils = HandlerUtils()
) {

    private val outboundApp = "http://dings-validate/api/v1/token"

    suspend fun getResponse() =
        handlerUtils.tryRequest("Getting response from: ", outboundApp) {
            handlerUtils.defaultHttpClient.get<ApiResponse> {
                header(HttpHeaders.Authorization, accessToken)
                url(outboundApp)
            }
        }
}

data class ApiResponse(val keys: String, val result: String)
