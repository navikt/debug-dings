package no.nav.dingser.config

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

private val config: Configuration =
    systemProperties() overriding
        EnvironmentVariables()

data class Environment(
    val application: Application = Application(),
    val idporten: Idporten = Idporten(),
    val tokenDings: TokenDings = TokenDings()
) {
    data class Application(
        val profile: String = config.getOrElse(Key("application.profile", stringType), "TEST"),
        val appPort: Int = config.getOrElse(Key("application.port", intType), 8080)
    )

    data class Idporten(
        val metadata: String = config.getOrElse(Key("idporten.wellknown", stringType), "https://oidc-ver2.difi.no/idporten-oidc-provider/.well-known/openid-configuration"),
        val scope: String = config.getOrElse(Key("idporten.scope", stringType), "openid"),
        val clientId: String = config.getOrElse(Key("idporten.client.id", stringType), "client_id"),
        val clientSecret: String = config.getOrElse(Key("idporten.client.secret", stringType), "client_secret")
    )

    data class TokenDings(
        val metadata: String = config.getOrElse(Key("tokendings.wellknown", stringType), "https://tokendings.dev-gcp.nais.io/.well-known/oauth-authorization-server"),
        val issuer: String = config.getOrElse(Key("tokendings.sub", stringType), "dev-gcp:plattformsikkerhet:dingser"),
        val audience: String = config.getOrElse(Key("tokendings.audience", stringType), "dev-gcp:plattformsikkerhet:dings-validate"),

        val clientId: String = config.getOrElse(Key("tokendings.client.id", stringType), "client_id"),
        val jwksPublic: String = config.getOrElse(Key("tokendings.jwks.public", stringType), "jwks_public"),
        val jwksPrivate: String = config.getOrElse(Key("tokendings.jwks.private", stringType), "jwks_private"),
        val privateKeyBase64: String = config.getOrElse(Key("tokendings.private.key.base64", stringType), "RSA_PRIVATE_KEY")
    )
}

enum class Profile {
    TEST, NON_PROD, PROD
}
