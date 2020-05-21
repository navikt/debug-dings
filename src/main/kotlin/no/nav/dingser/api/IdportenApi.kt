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
import mu.KotlinLogging
import no.nav.dingser.config.Environment
import no.nav.dingser.token.identityServerName
import no.nav.dingser.token.tokendings.TokenDingsService
import no.nav.dingser.token.utils.TokenConfiguration

private val log = KotlinLogging.logger { }

@KtorExperimentalAPI
fun Routing.idporten(
    tokenConfiguration: TokenConfiguration,
    environment: Environment
) {
    generateToken()
    authCallback(tokenConfiguration, environment)
}

private fun Routing.generateToken() =
    get("/") {
        call.respondText("""Click <a href="/oauth">here</a> to get tokens""", ContentType.Text.Html)
    }

@KtorExperimentalAPI
private fun Routing.authCallback(tokenConfiguration: TokenConfiguration, environment: Environment) =
    route("/") {
        authenticate(identityServerName) {
            get("/oauth") {
                val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()

                call.respondText("Access Token = ${principal?.accessToken}")

                val tokenDingsService = TokenDingsService(
                    tokenConfiguration = tokenConfiguration,
                    environment = environment
                )

                val exchangedToken = tokenDingsService.exchangeToken(principal)
                // log.info { runBlocking {  OutboundApiService(exchangedToken).getResponse() } }
            }
        }
    }
