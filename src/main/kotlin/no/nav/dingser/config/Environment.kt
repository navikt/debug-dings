package no.nav.dingser.config

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dingser.token.utils.OauthServerConfigurationMetadata
import no.nav.dingser.token.utils.defaultHttpClient
import no.nav.dingser.token.utils.getOAuthServerConfigurationMetadata
import java.io.File
import java.io.FileNotFoundException

private val log = KotlinLogging.logger { }

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
        val wellKnownUrl: String = config.getOrElse(
            Key("idporten.wellknown", stringType),
            "https://oidc-ver2.difi.no/idporten-oidc-provider/.well-known/openid-configuration"
        ),
        val scope: String = config.getOrElse(Key("idporten.scope", stringType), "openid"),
        val clientId: String = config.getOrElse(Key("idporten.client.id", stringType), "client_id"),
        val clientSecret: String = config.getOrElse(Key("idporten.client.secret", stringType), "client_secret")
    )

    // TODO: should rename env var tokendings.sub to client.id/clientid
    data class TokenDings(
        val wellKnownUrl: String = config.getOrElse(
            Key("tokendings.wellknown", stringType),
            "https://tokendings.dev-gcp.nais.io/.well-known/oauth-authorization-server"
        ),
        val clientId: String = config.getOrElse(Key("tokendings.sub", stringType), ":plattformsikkerhet:dingser"),
        val audience: String = config.getOrElse(Key("tokendings.audience", stringType), ":plattformsikkerhet:dings-validate"),
        val jwksPrivate: String = "/var/run/secrets/jwks".readFile() ?: "jwks_private"
    ) {
        val metadata: OauthServerConfigurationMetadata =
            runBlocking {
                defaultHttpClient.getOAuthServerConfigurationMetadata(wellKnownUrl)
            }
    }
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
