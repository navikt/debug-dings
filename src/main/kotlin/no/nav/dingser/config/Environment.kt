package no.nav.dingser.config

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import java.io.File
import java.io.FileNotFoundException

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
        val port: Int = config.getOrElse(Key("application.port", intType), 8080),
        val redirectUrl: String = config.getOrElse(Key("application.redirect.url", stringType), "http://localhost:8080/oauth")
    )

    data class Idporten(
        val metadata: String = config.getOrElse(Key("idporten.wellknown", stringType), "https://oidc-ver2.difi.no/idporten-oidc-provider/.well-known/openid-configuration"),
        val scope: String = config.getOrElse(Key("idporten.scope", stringType), "openid"),
        val clientId: String = config.getOrElse(Key("idporten.client.id", stringType), "client_id"),
        val clientSecret: String = config.getOrElse(Key("idporten.client.secret", stringType), "client_secret")
    )

    data class TokenDings(
        val metadata: String = config.getOrElse(Key("tokendings.wellknown", stringType), "https://tokendings.dev-gcp.nais.io/.well-known/oauth-authorization-server"),
        val issuer: String = config.getOrElse(Key("tokendings.sub", stringType), ":plattformsikkerhet:dingser"),
        val audience: String = config.getOrElse(Key("tokendings.audience", stringType), "dev-gcp:plattformsikkerhet:dings-validate"),
        val jwksPrivate: String = "/var/run/secrets/jwks".readFile() ?: "jwks_private"
    )
}

internal fun String.readFile(): String? =
    try {
        File(this).readText(Charsets.UTF_8)
    } catch (err: FileNotFoundException) {
        null
    }

enum class Profile {
    TEST, NON_PROD, PROD
}
