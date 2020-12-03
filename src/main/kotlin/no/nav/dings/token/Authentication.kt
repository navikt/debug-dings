package no.nav.dings.token

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging
import no.nav.dings.config.ClientProperties
import no.nav.dings.config.Environment
import java.time.Instant
import java.util.Date
import java.util.UUID

private val log = KotlinLogging.logger { }

class Authentication(
    private val clientProperties: ClientProperties
) {

    private val rsaKey = RSAKey.parse(clientProperties.privateJwk)

    @KtorExperimentalAPI
    fun clientAssertion(audience: String): String {
        log.info { "Getting Keys with keyID: ${rsaKey.keyID}" }
        log.info { "Getting Apps own private key and generating JWT token for integration with: $audience" }
        return clientAssertion(clientProperties, audience, rsaKey)
    }
}

@KtorExperimentalAPI
private fun clientAssertion(clientProperties: ClientProperties, audience: String, rsaKey: RSAKey): String {
    val now = Date.from(Instant.now())
    return JWTClaimsSet.Builder()
        .configurableClaims(clientProperties, now)
        .issuer(clientProperties.clientId)
        .audience(audience)
        .issueTime(now)
        .expirationTime(Date.from(Instant.now().plusSeconds(120)))
        .jwtID(UUID.randomUUID().toString())
        .build()
        .sign(rsaKey)
        .serialize()
}

@KtorExperimentalAPI
private fun JWTClaimsSet.Builder.configurableClaims(clientProperties: ClientProperties, now: Date): JWTClaimsSet.Builder {
    if (clientProperties is Environment.TokenX) {
        return this.subject(clientProperties.clientId).notBeforeTime(now)
    }
    return this
}

internal fun JWTClaimsSet.sign(rsaKey: RSAKey): SignedJWT =
    SignedJWT(
        JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaKey.keyID)
            .type(JOSEObjectType.JWT).build(),
        this
    ).apply {
        sign(RSASSASigner(rsaKey.toPrivateKey()))
    }
