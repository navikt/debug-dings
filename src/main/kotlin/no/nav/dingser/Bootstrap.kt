package no.nav.dingser

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.OAuthServerSettings
import io.ktor.auth.oauth
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.HttpMethod
import io.ktor.jackson.jackson
import io.ktor.request.path
import io.ktor.routing.Routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging
import no.nav.dingser.api.v1.exceptionHandler
import no.nav.dingser.api.v1.idporten
import no.nav.dingser.api.v1.selfTest
import no.nav.dingser.config.Environment
import no.nav.dingser.token.utils.TokenConfiguration
import org.slf4j.event.Level

const val OAUTH_SERVER_WELL_KNOWN_PATH_IDPORTEN = "/.well-known/openid-configuration"
const val OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS = "/.well-known/oauth-authorization-server"

const val identityServerName = "IdentityServerTest"

private val log = KotlinLogging.logger { }

@KtorExperimentalAPI
fun createHttpServer(environment: Environment, applicationStatus: ApplicationStatus): NettyApplicationEngine {
    return embeddedServer(Netty, port = environment.application.appPort, module = { setupHttpServer(environment = environment, applicationStatus = applicationStatus) })
}

@KtorExperimentalAPI
fun Application.setupHttpServer(environment: Environment, applicationStatus: ApplicationStatus) {

    val difiConfiguration = TokenConfiguration(
        wellknownUrl = environment.idporten.metadata
    )

    // val difiConfiguration = TokenConfiguration(
    //     issuer = "http://localhost:8888/youssef/.well-known/openid-configuration",
    // )

    val tokenDingsConfiguration = TokenConfiguration(
        wellknownUrl = environment.tokenDings.metadata
    )

    // val endUserService = EndUserService()
    log.info { "Setup Authentication with Idp: ${difiConfiguration.wellKnownMetadata.issuer}" }
    log.info { "Client: ${environment.idporten.clientId}" }
    val clientSettings = OAuthServerSettings.OAuth2ServerSettings(
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

    install(Authentication) {
        oauth(identityServerName) {
            // will handle the back channel requests to the token endpoint
            client = HttpClient(CIO)
            // client settings from before
            providerLookup = { clientSettings }
            // Where we receive the Authorization code
            urlProvider = { "http://localhost:8080/oauth" }
        }
    }

    install(DefaultHeaders) {
    }
    log.info { "Installing log level: INFO" }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }
    log.info { "Installing Api-Exception handler" }
    install(StatusPages) {
        exceptionHandler()
    }

    install(ContentNegotiation) {
        jackson {
            configure(SerializationFeature.INDENT_OUTPUT, true)
        }
    }
    log.info { "Installing routes" }
    install(Routing) {

        selfTest(readySelfTestCheck = { applicationStatus.initialized }, aLiveSelfTestCheck = { applicationStatus.running })

        idporten(tokenDingsConfiguration, environment)
    }
    applicationStatus.initialized = true
    log.info { "Application is up and running" }
}
