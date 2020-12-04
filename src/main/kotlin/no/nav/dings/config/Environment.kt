package no.nav.dings.config

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.ktor.auth.OAuthServerSettings
import io.ktor.http.HttpMethod
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import no.nav.dings.token.Authentication
import no.nav.dings.token.OauthServerConfigurationMetadata
import no.nav.dings.token.defaultHttpClient
import no.nav.dings.token.getOAuthServerConfigurationMetadata
import java.net.URL
import java.util.concurrent.TimeUnit

private val config: Configuration =
    systemProperties() overriding
        EnvironmentVariables()

@KtorExperimentalAPI
data class Environment(
    val application: Application = Application(),
    val login: Login = Login(),
    val idporten: Idporten = Idporten(),
    val tokenX: TokenX = TokenX(),
    val downstreamApi: DownstreamApi = DownstreamApi()
) {

    data class Application(
        val profile: String = config.getOrElse(Key("application.profile", stringType), "TEST"),
        val port: Int = config.getOrElse(Key("application.port", intType), 8080),
        val redirectUrl: String = config.getOrElse(Key("idporten.redirect.uri", stringType), "http://localhost:$port/oauth2/callback")
    )

    data class Login(
        val idTokenCookie: String = "id_token",
        val redirectCookie: String = "redirect_uri",
        val afterLoginUri: String = "/debugger"
    )

    data class TokenX(
        override val wellKnownUrl: String = config[Key("token.x.well.known.url", stringType)],
        override val clientId: String = config[Key("token.x.client.id", stringType)],
        override val privateJwk: String = config[Key("token.x.private.jwk", stringType)],
        val targetGCPAudience: String = config.getOrElse(Key("client.gcp.audience", stringType), "dev-gcp:plattformsikkerhet:api-dings"),
        val targetONPREMAudience: String = config.getOrElse(Key("client.onprem.audience", stringType), "dev-fss:plattformsikkerhet:api-dings")
    ) : ClientProperties {

        val metadata: OauthServerConfigurationMetadata =
            runBlocking {
                defaultHttpClient.getOAuthServerConfigurationMetadata(wellKnownUrl)
            }
    }

    data class Idporten(
        override val wellKnownUrl: String = config[Key("idporten.well.known.url", stringType)],
        val scope: String = config.getOrElse(Key("idporten.scope", stringType), "openid"),
        override val clientId: String = config[Key("idporten.client.id", stringType)],
        override val privateJwk: String = config[Key("idporten.client.jwk", stringType)]
    ) : ClientProperties {

        val metadata: OauthServerConfigurationMetadata =
            runBlocking {
                defaultHttpClient.getOAuthServerConfigurationMetadata(wellKnownUrl)
            }
        val jwkProvider: JwkProvider = JwkProviderBuilder(URL(metadata.jwksUri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

        private val clientAssertion = Authentication(this).clientAssertion(metadata.issuer)

        val oauth2ServerSettings = OAuthServerSettings.OAuth2ServerSettings(
            name = "IdPorten",
            authorizeUrl = metadata.authorizationEndpoint,
            accessTokenUrl = metadata.tokenEndpoint,
            clientId = clientId,
            clientSecret = clientAssertion,
            accessTokenRequiresBasicAuth = false,
            requestMethod = HttpMethod.Post, // must POST to token endpoint
            defaultScopes = listOf(scope),
            // customise the authorization request with extra parameters
            authorizeUrlInterceptor = {
                this.parameters.append("response_mode", "query")
            }
        )
    }

    data class DownstreamApi(
        val gcpApiUrl: String = config.getOrElse(Key("downstream.gcp.api.url", stringType), "https://api-dings.dev-gcp.nais.io/hello"),
        val onpremApiUrl: String = config.getOrElse(Key("downstream.onprem.api.url", stringType), "https://api-dings.dev-fss-pub.nais.io/hello")
    )
}

interface ClientProperties {
    val wellKnownUrl: String
    val clientId: String
    val privateJwk: String
}
