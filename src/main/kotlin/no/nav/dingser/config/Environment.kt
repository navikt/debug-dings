package no.nav.dingser.config

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.auth.OAuthServerSettings
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dingser.token.OauthServerConfigurationMetadata
import no.nav.dingser.token.utils.defaultHttpClient
import no.nav.dingser.token.utils.getOAuthServerConfigurationMetadata
import no.nav.dingser.token.utils.objectMapper
import no.nav.security.token.support.core.configuration.ProxyAwareResourceRetriever
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger { }

private val config: Configuration =
    systemProperties() overriding
        EnvironmentVariables()

data class Environment(
    val application: Application = Application(),
    val login: Login = Login(),
    val idporten: Idporten = Idporten(),
    val tokenDings: TokenDings = TokenDings(),
    val downstreamApi: DownstreamApi = DownstreamApi()
) {
    data class Application(
        val profile: String = config.getOrElse(Key("application.profile", stringType), "TEST"),
        val port: Int = config.getOrElse(Key("application.port", intType), 8080),
        val redirectUrl: String = config.getOrElse(Key("application.redirect.url", stringType), "http://localhost:8080/oauth")
    )

    data class Login(
        val idTokenCookie: String = "id_token",
        val redirectCookie: String = "redirect_uri",
        val afterLoginUri: String = "/debugger"
    )

    data class Idporten(
        val wellKnownUrl: String = config.getOrElse(
            Key("idporten.wellknown", stringType),
            "https://oidc-ver2.difi.no/idporten-oidc-provider/.well-known/openid-configuration"
        ),
        val scope: String = config.getOrElse(Key("idporten.scope", stringType), "openid"),
        val clientId: String = config.getOrElse(Key("idporten.client.id", stringType), "client_id"),
        val clientSecret: String = config.getOrElse(Key("idporten.client.secret", stringType), "client_secret")
    ) {

        val testData: OauthServerConfigurationMetadata = runBlocking {
            objectMapper.run { readValue<OauthServerConfigurationMetadata>(ProxyAwareResourceRetriever().retrieveResource(URL(wellKnownUrl)).content) }
        }.also { log.info { this } }

        val metadata: OauthServerConfigurationMetadata =
            runBlocking {
                defaultHttpClient.getOAuthServerConfigurationMetadata(wellKnownUrl)
            }
        val jwkProvider: JwkProvider = JwkProviderBuilder(URL(metadata.jwksUri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

        val oauth2ServerSettings = OAuthServerSettings.OAuth2ServerSettings(
            name = "IdPorten",
            authorizeUrl = metadata.authorizationEndpoint,
            accessTokenUrl = metadata.tokenEndpoint,
            clientId = clientId,
            clientSecret = clientSecret,
            accessTokenRequiresBasicAuth = false,
            requestMethod = HttpMethod.Post, // must POST to token endpoint
            defaultScopes = listOf(scope),
            // customise the authorization request with extra parameters
            authorizeUrlInterceptor = { this.parameters.append("response_mode", "query") }
        )
    }

    data class TokenDings(
        val wellKnownUrl: String = config.getOrElse(
            Key("tokendings.wellknown", stringType),
            "https://tokendings.dev-gcp.nais.io/.well-known/oauth-authorization-server"
        ),
        val clientId: String = config.getOrElse(Key("nais.client.id", stringType), "cluster:namespace:app1"),
        val audience: String = config.getOrElse(Key("client.audience", stringType), "dev-gcp:plattformsikkerhet:dings-validate"),
        val jwksPrivate: String = "/var/run/secrets/jwks".readFile() ?: JWKSet(generateRsaKey()).toJSONObject(false).toJSONString()
    ) {
        val metadata: OauthServerConfigurationMetadata =
            runBlocking {
                defaultHttpClient.getOAuthServerConfigurationMetadata(wellKnownUrl)
            }
    }

    data class DownstreamApi(
        val url: String = config.getOrElse(Key("downstream.api.url", stringType), "https://dings-validate.dev-gcp.nais.io/hello")
    )
}

internal fun String.readFile(): String? =
    try {
        File(this).readText(Charsets.UTF_8)
    } catch (err: FileNotFoundException) {
        null
    }

internal fun generateRsaKey(keyId: String = UUID.randomUUID().toString(), keySize: Int = 2048): RSAKey =
    KeyPairGenerator.getInstance("RSA").apply { initialize(keySize) }.generateKeyPair()
        .let {
            RSAKey.Builder(it.public as RSAPublicKey)
                .privateKey(it.private as RSAPrivateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .build()
        }

enum class Profile {
    TEST, NON_PROD, PROD
}
