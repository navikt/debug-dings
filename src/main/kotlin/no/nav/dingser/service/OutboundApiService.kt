package no.nav.dingser.service

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.util.KtorExperimentalAPI
import no.nav.dingser.token.utils.HandlerUtils

class OutboundApiService(
    private val accessToken: String,
    private val handlerUtils: HandlerUtils = HandlerUtils()
) {

    private val outboundApp = "http://dings-validate/hello"

    @KtorExperimentalAPI
    suspend fun getResponse() =
        handlerUtils.tryRequest("Getting response from: ", outboundApp) {
            handlerUtils.defaultHttpClient.get<String> {
                header(HttpHeaders.Authorization, accessToken)
                url(outboundApp)
            }
        }
}
