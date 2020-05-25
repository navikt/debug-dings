package no.nav.dingser.api

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import no.nav.dingser.HttpException
import no.nav.dingser.Jackson.defaultMapper
import no.nav.dingser.authentication.idTokenPrincipal
import no.nav.dingser.config.Environment
import no.nav.dingser.token.tokendings.OAuth2TokenExchangeRequest
import no.nav.dingser.token.tokendings.TokenDingsService
import no.nav.dingser.token.tokendings.tokenExchange
import no.nav.dingser.token.utils.defaultHttpClient
import java.net.URI

internal fun Routing.debuggerApi(config: Environment.TokenDings) {
    val tokenDingsService = TokenDingsService(config)
    authenticate("cookie") {
        route("/debugger") {
            get {
                val principal = checkNotNull(call.idTokenPrincipal())
                val subjectToken: String = authCache.getIfPresent(principal.decodedJWT.subject)?.accessToken ?: "error: could not get accesstoken from cache"
                call.respond(
                    FreeMarkerContent(
                        "debugger.ftl",
                        mapOf(
                            "tokendings_url" to config.metadata.tokenEndpoint,
                            "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                            "client_assertion" to tokenDingsService.clientAssertion(),
                            "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
                            "audience" to config.audience,
                            "subject_token_type" to "urn:ietf:params:oauth:token-type:jwt",
                            "subject_token" to subjectToken
                        )
                    )
                )
            }

            post {
                val parameters = call.receiveParameters()
                val url = parameters.require("tokendings_url")
                val tokenRequest = parameters.toTokenExchangeRequest()
                val tokenResponse = defaultHttpClient.tokenExchange(
                    url,
                    tokenRequest
                )
                call.respond(
                    FreeMarkerContent(
                        "tokenresponse.ftl",
                        mapOf(
                            "token_request" to tokenRequest.toFormattedTokenRequest(url),
                            "token_response" to defaultMapper.writeValueAsString(tokenResponse),
                            "api_url" to "URL for the API to call",
                            "bearer_token" to tokenResponse.accessToken
                        )
                    )
                )
            }

            post("/call") {
                val parameters = call.receiveParameters()
                val url = URI(parameters.require("api_url"))
                val bearerToken = parameters.require("bearer_token")
                val formattedRequest: String =
                    """
                GET ${url.path} HTTP/1.1
                Host: ${url.host}
                Authorization: Bearer $bearerToken
                """.trimIndent()

                val response: String = defaultHttpClient.get(url.toString()) {
                    header("Authorization", "Bearer $bearerToken")
                }

                call.respond(
                    FreeMarkerContent(
                        "apiresponse.ftl",
                        mapOf(
                            "api_request" to formattedRequest,
                            "api_response" to response
                        )
                    )
                )
            }
        }
    }
}

fun Parameters.require(name: String): String =
    this[name] ?: throw HttpException(HttpStatusCode.BadRequest, "missing required param $name")

fun OAuth2TokenExchangeRequest.toFormattedTokenRequest(url: String) {
    val uri = URI(url)
    "POST ${uri.path} HTTP/1.1\n" +
        "Host: ${uri.host}\n"
    "Content-Type: application/x-www-form-urlencoded\n\n" +
        """
    client_assertion_type=$clientAssertionType&
    client_assertion=$clientAssertion&
    grant_type=$grantType&
    audience=$audience&
    subject_token_type=$subjectTokenType&
    subject_token=$subjectToken&
    """.trimIndent()
}

fun Parameters.toTokenExchangeRequest(): OAuth2TokenExchangeRequest =
    OAuth2TokenExchangeRequest(
        this["client_assertion"]!!,
        this["subject_token"]!!,
        this["audience"]!!
    )
