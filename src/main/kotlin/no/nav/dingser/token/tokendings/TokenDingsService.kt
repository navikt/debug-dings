package no.nav.dingser.token.tokendings

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.url
import io.ktor.http.parametersOf
import java.security.PrivateKey
import java.time.Clock
import java.util.Date
import java.util.UUID
import mu.KotlinLogging
import no.nav.dingser.config.Environment
import no.nav.dingser.token.utils.AccessToken
import no.nav.dingser.token.utils.AccessTokenResponse
import no.nav.dingser.token.utils.BEARER
import no.nav.dingser.token.utils.HandlerUtils
import no.nav.dingser.token.utils.Jws
import no.nav.dingser.token.utils.SCOPE
import no.nav.dingser.token.utils.TokenConfiguration
import no.nav.dingser.token.utils.base64ToPrivateKey
import no.nav.dingser.token.utils.getKeys

private val log = KotlinLogging.logger { }

internal const val PARAMS_GRANT_TYPE = "grant_type"
internal const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange"
internal const val PARAMS_SUBJECT_TOKEN_TYPE = "subject_token_type"
internal const val SUBJECT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt"
internal const val PARAMS_SUBJECT_TOKEN = "subject_token"
internal const val PARAMS_AUDIENCE = "audience"
internal const val PARAMS_CLIENT_ASSERTION = "client_assertion"
internal const val PARAMS_CLIENT_ASSERTION_TYPE = "client_assertion_type"
internal const val CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"

class TokenDingsService(
    private val environment: Environment,
    private val subjectToken: String?,
    val tokenConfiguration: TokenConfiguration,
    private val handlerUtils: HandlerUtils = HandlerUtils()

) {

    private fun createJws(): Jws {
        log.info { "Getting Apps own private key and generating JWT token for integration with TokenDings" }
        val keys = getKeys(environment.tokenDings.jwksPublic).keys
        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keys.map { it.kid }.single()).build(),
            JWTClaimsSet.Builder()
                .audience(tokenConfiguration.wellKnownMetadata.tokenEndpoint)
                .issuer(environment.tokenDings.issuer)
                .issueTime(Date(Clock.systemUTC().millis()))
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(Date(Clock.systemUTC().millis() + 120000))
                .claim(SCOPE, environment.idporten.scope)
                .build()
        ).run {
            sign(RSASSASigner(base64ToPrivateKey(environment.tokenDings.privateKeyBase64) as PrivateKey))
            Jws(serialize())
        }
    }

    private suspend fun getToken(jwsToken: Jws): AccessToken =
        handlerUtils.tryRequest("Making a Formdata Url-encoded Token request for TokenDings", tokenConfiguration.wellKnownMetadata.tokenEndpoint) {
            val response = handlerUtils.defaultHttpClient.submitForm<AccessTokenResponse>(
                parametersOf(
                    PARAMS_CLIENT_ASSERTION to listOf(jwsToken.token),
                    PARAMS_CLIENT_ASSERTION_TYPE to listOf(CLIENT_ASSERTION_TYPE),
                    PARAMS_GRANT_TYPE to listOf(GRANT_TYPE),
                    PARAMS_SUBJECT_TOKEN to listOf(subjectToken!!),
                    PARAMS_SUBJECT_TOKEN_TYPE to listOf(SUBJECT_TOKEN_TYPE),
                    PARAMS_AUDIENCE to listOf(environment.tokenDings.audience))
            ) {
                url(tokenConfiguration.wellKnownMetadata.tokenEndpoint)
            }
            AccessToken(response.accessToken)
        }

    suspend fun bearerToken(): String {
        val jwsToken = createJws()
        return "$BEARER ${getToken(jwsToken)}"
    }
}
