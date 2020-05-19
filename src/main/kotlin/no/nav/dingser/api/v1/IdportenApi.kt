package no.nav.dingser.api.v1

import io.ktor.application.call
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
import mu.KotlinLogging
import no.nav.dingser.config.Environment
import no.nav.dingser.identityServerName
import no.nav.dingser.token.tokendings.TokenDingsService
import no.nav.dingser.token.utils.HandlerUtils
import no.nav.dingser.token.utils.OauthServerConfigurationMetadata
import no.nav.dingser.token.utils.TokenConfiguration

private val log = KotlinLogging.logger { }

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

private fun Routing.authCallback(tokenConfiguration: TokenConfiguration, environment: Environment) =
    route("/") {
        authenticate(identityServerName) {
            get("/oauth") {
                val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()

                call.respondText("Access Token = ${principal?.accessToken}")

                getNextApplicationResponse(tokenConfiguration, environment, principal)
            }
        }
    }

suspend fun getNextApplicationResponse(
    tokenConfiguration: TokenConfiguration,
    environment: Environment,
    principal: OAuthAccessTokenResponse.OAuth2?
) {
    val tokenDingsService = TokenDingsService(
        tokenConfiguration = tokenConfiguration,
        subjectToken = principal?.accessToken,
        environment = environment
    )

    // Try to get token from token dings.
    val handlerUtils = HandlerUtils()
    val jwsToken = tokenDingsService.createJws()
    val accessToken = tokenDingsService.bearerToken(jwsToken)
    log.info { "accessToken: $accessToken" }

   // val nextApp = "http://dings-validate/api/v1/token"
   // handlerUtils.tryRequest("Getting response from: ", nextApp) {
   //     handlerUtils.defaultHttpClient.get<OauthServerConfigurationMetadata> {
   //         header(HttpHeaders.Authorization, accessToken)
   //         url(nextApp)
   //     }
   // }
}
