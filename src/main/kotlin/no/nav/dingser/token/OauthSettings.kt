package no.nav.dingser.token

import io.ktor.auth.OAuthServerSettings
import io.ktor.http.HttpMethod
import io.ktor.util.KtorExperimentalAPI
import no.nav.dingser.config.Environment
import no.nav.dingser.token.utils.TokenConfiguration

class OauthSettings(
    private val environment: Environment,
    var identityServerName: String = "IdentityServerDingser"
) {
    val difiConfiguration = TokenConfiguration(wellknownUrl = environment.idporten.wellKnownUrl)

    val tokenDingsConfiguration = TokenConfiguration(wellknownUrl = environment.tokenDings.wellKnownUrl)

    @KtorExperimentalAPI
    fun getOauthServerSettings() = OAuthServerSettings.OAuth2ServerSettings(
        name = identityServerName,
        authorizeUrl = difiConfiguration.wellKnownMetadata.authorizationEndpoint, // OAuth authorization endpoint
        accessTokenUrl = difiConfiguration.wellKnownMetadata.tokenEndpoint, // OAuth token endpoint
        clientId = environment.idporten.clientId,
        clientSecret = environment.idporten.clientSecret,
        // basic auth implementation is not "OAuth style" so falling back to post body
        accessTokenRequiresBasicAuth = false,
        requestMethod = HttpMethod.Post, // must POST to token endpoint
        defaultScopes = listOf(environment.idporten.scope), // what scopes to explicitly request
        // customise the authorization request with extra parameters
        authorizeUrlInterceptor = { this.parameters.append("response_mode", "query") }
    )
}
