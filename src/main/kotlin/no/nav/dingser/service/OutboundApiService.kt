package no.nav.dingser.service

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.util.KtorExperimentalAPI
import no.nav.dingser.token.utils.defaultHttpClient
import no.nav.dingser.token.utils.withLog

class OutboundApiService(
    private val accessToken: String
) {

    private val outboundApp = "http://dings-validate/hello"

    @KtorExperimentalAPI
    suspend fun getResponse() =
        withLog("Getting response from: ", outboundApp) {
            defaultHttpClient.get<String> {
                header(HttpHeaders.Authorization, accessToken)
                url(outboundApp)
            }
        }
}
