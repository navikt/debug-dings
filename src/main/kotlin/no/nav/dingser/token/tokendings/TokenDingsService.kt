package no.nav.dingser.token.tokendings

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.url
import io.ktor.http.parametersOf
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging
import no.nav.dingser.config.Environment
import no.nav.dingser.token.utils.AccessToken
import no.nav.dingser.token.utils.AccessTokenResponse
import no.nav.dingser.token.utils.HandlerUtils
import no.nav.dingser.token.utils.Jws
import no.nav.dingser.token.utils.TokenConfiguration
import java.time.Clock
import java.util.*

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
internal const val BEARER = "Bearer"

class TokenDingsService(
    private val environment: Environment,
    val tokenConfiguration: TokenConfiguration,
    private val handlerUtils: HandlerUtils = HandlerUtils()
) {

    private val jwkToRSA = JWKSet.parse(environment.tokenDings.jwksPrivate).keys[0].toRSAKey()

    @KtorExperimentalAPI
    fun createJws(): Jws {
        log.info { "Getting Keys with keyIDs: ${JWKSet.parse(environment.tokenDings.jwksPrivate).keys.map { it.keyID }}" }
        log.info { "Getting Apps own private key and generating JWT token for integration with TokenDings" }
        return Jws(
            JWTClaimsSet.Builder()
                .audience(tokenConfiguration.wellKnownMetadata.tokenEndpoint)
                .subject(environment.tokenDings.issuer)
                .issuer(environment.tokenDings.issuer)
                .issueTime(Date(Clock.systemUTC().millis()))
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(Date(Clock.systemUTC().millis() + 120000))
                .build()
                .sign(jwkToRSA)
                .serialize()
        )
    }

    private fun JWTClaimsSet.sign(rsaKey: RSAKey): SignedJWT =
        SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.keyID)
                .type(JOSEObjectType.JWT).build(),
            this
        ).apply {
            sign(RSASSASigner(rsaKey.toPrivateKey()))
        }

    @KtorExperimentalAPI
    suspend fun getToken(jwsToken: Jws, subjectToken: String?): AccessToken =
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

    fun bearerToken(accessToken: AccessToken) = "$BEARER $accessToken"

    @KtorExperimentalAPI
    suspend fun exchangeToken(principal: OAuthAccessTokenResponse.OAuth2?): String {
        // Try to exchange token with TokenDings
        val jwsToken = createJws()
        val accessToken = principal?.let {
            getToken(jwsToken, it.accessToken)
        }.also {
            it ?: throw IllegalStateException("Could not get User token from Login")
        }
        return bearerToken(accessToken!!)
    }
}
