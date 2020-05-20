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

const val identityServerName = "IdentityServerTest"

private val log = KotlinLogging.logger { }

@KtorExperimentalAPI
fun createHttpServer(environment: Environment, applicationStatus: ApplicationStatus): NettyApplicationEngine {
    return embeddedServer(Netty, port = environment.application.port, module = { setupHttpServer(environment = environment, applicationStatus = applicationStatus) })
}

@KtorExperimentalAPI
fun Application.setupHttpServer(environment: Environment, applicationStatus: ApplicationStatus) {

    log.info { "Application Profile running: ${environment.application.profile}" }

    val difiConfiguration = TokenConfiguration(
        wellknownUrl = environment.idporten.metadata
    )

    val tokenDingsConfiguration = TokenConfiguration(
        wellknownUrl = environment.tokenDings.metadata
    )

    log.info { "Setup Authentication with Idp: ${difiConfiguration.wellKnownMetadata.issuer}" }
    val clientSettings = getOauthServerSettings(
        environment = environment, configuration = difiConfiguration
    )

    log.info { "Installing Authentication Server Name: $identityServerName" }
    install(Authentication) {
        oauth(identityServerName) {
            // will handle the back channel requests to the token endpoint
            client = HttpClient(CIO)
            // client settings from before
            providerLookup = { clientSettings }
            // Where we receive the Authorization code
            urlProvider = { environment.application.redirectUrl }
        }
    }

    val logLevel = Level.INFO
    log.info { "Installing log level: $logLevel" }
    install(CallLogging) {
        level = logLevel
        filter { call -> call.request.path().startsWith("/") }
    }
    log.info { "Installing Api-Exception handler" }
    install(StatusPages) {
        exceptionHandler()
    }
    log.info { "Installing ObjectMapper" }
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

fun getOauthServerSettings(
    environment: Environment,
    configuration: TokenConfiguration
) = OAuthServerSettings.OAuth2ServerSettings(
    name = identityServerName,
    authorizeUrl = configuration.wellKnownMetadata.authorizationEndpoint, // OAuth authorization endpoint
    accessTokenUrl = configuration.wellKnownMetadata.tokenEndpoint, // OAuth token endpoint
    clientId = environment.idporten.clientId,
    clientSecret = environment.idporten.clientSecret,
    // basic auth implementation is not "OAuth style" so falling back to post body
    accessTokenRequiresBasicAuth = false,
    requestMethod = HttpMethod.Post, // must POST to token endpoint
    defaultScopes = listOf(environment.idporten.scope), // what scopes to explicitly request
    // customise the authorization request with extra parameters
    authorizeUrlInterceptor = { this.parameters.append("response_mode", "query") }
)
