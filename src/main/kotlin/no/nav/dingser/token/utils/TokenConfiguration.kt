package no.nav.dingser.token.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

class TokenConfiguration(
    val wellknownUrl: String
) {

    @KtorExperimentalAPI
    val wellKnownMetadata: OauthServerConfigurationMetadata = runBlocking {
        withLog("Getting WellKnown configuration from: ", wellknownUrl) {
            defaultHttpClient.get<OauthServerConfigurationMetadata> {
                url(wellknownUrl)
                accept(ContentType.Application.Json)
            }.also { log.info { "Got WellKnown config from: $it" } }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class OauthServerConfigurationMetadata(
    @JsonProperty(value = "issuer", required = true) val issuer: String,
    @JsonProperty(value = "token_endpoint", required = true) val tokenEndpoint: String,
    @JsonProperty(value = "jwks_uri", required = true) val jwksUri: String,
    @JsonProperty(value = "authorization_endpoint", required = false) var authorizationEndpoint: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessTokenResponse(
    @JsonProperty(value = "access_token", required = true) val accessToken: String,
    @JsonProperty(value = "issued_token_type", required = true) val issuedTokenType: String,
    @JsonProperty(value = "token_type", required = true) val tokenType: String,
    @JsonProperty(value = "expires_in", required = true) val expiresIn: Int
)

class AccessToken(val token: String) {
    override fun toString(): String = token
}

class ClientAssertion(val token: String) {
    override fun toString(): String = token
}
