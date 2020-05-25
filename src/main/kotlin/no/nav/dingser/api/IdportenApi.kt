package no.nav.dingser.api

import io.ktor.application.call
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dingser.config.Environment
import no.nav.dingser.service.OutboundApiService
import no.nav.dingser.token.OauthSettings
import no.nav.dingser.token.tokendings.TokenDingsService

private val log = KotlinLogging.logger { }

@KtorExperimentalAPI
fun Routing.idporten(
    oauthSettings: OauthSettings,
    environment: Environment
) {
    generateToken()
    authCallback(oauthSettings, environment)
}

private fun Routing.generateToken() =
    route("/") {
        get {
            call.respondText("""Click <a href="/oauth">here</a> to get tokens""", ContentType.Text.Html)
        }
    }

@KtorExperimentalAPI
private fun Routing.authCallback(oauthSettings: OauthSettings, environment: Environment) =
    route("/oauth") {
        authenticate(oauthSettings.identityServerName) {
            get {
                val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                call.respondText("Access Token = ${principal?.accessToken}")
                val tokenDingsService = TokenDingsService(environment.tokenDings)
                val exchangedToken = tokenDingsService.exchangeToken(principal)
                log.info { runBlocking { OutboundApiService(exchangedToken).getResponse() } }
            }
        }
    }
