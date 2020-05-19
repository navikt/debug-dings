package no.nav.dingser.token.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

internal const val SCOPE = "scope"
internal const val BEARER = "Bearer"

private val log = KotlinLogging.logger { }

class TokenConfiguration(
    wellknownUrl: String
) {

    private val handlerUtils = HandlerUtils()

    val wellKnownMetadata: OauthServerConfigurationMetadata = runBlocking {
        handlerUtils.tryRequest("Getting WellKnown configuration from: ", wellknownUrl) {
            handlerUtils.defaultHttpClient.get<OauthServerConfigurationMetadata> {
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
    @JsonProperty(value = "authorization_endpoint", required = true) val authorizationEndpoint: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessTokenResponse(
    @JsonProperty(value = "access_token", required = true) val accessToken: String,
    @JsonProperty(value = "expires_in", required = true) val expiresIn: Int,
    @JsonProperty(value = "scope", required = true) val scope: String
)

class AccessToken(val token: String) {
    override fun toString(): String = token
}

class Jws(val token: String) {
    override fun toString(): String = token
}
