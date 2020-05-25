package no.nav.dingser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import freemarker.cache.ClassTemplateLoader
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.oauth
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.freemarker.FreeMarker
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.request.path
import io.ktor.routing.Routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging
import no.nav.dingser.api.debuggerApi
import no.nav.dingser.api.exceptionHandler
import no.nav.dingser.api.idporten
import no.nav.dingser.api.selfTest
import no.nav.dingser.config.Environment
import no.nav.dingser.token.OauthSettings
import org.slf4j.event.Level

private val log = KotlinLogging.logger { }

@KtorExperimentalAPI
fun createHttpServer(environment: Environment, applicationStatus: ApplicationStatus, oauthSettings: OauthSettings): NettyApplicationEngine {
    return embeddedServer(Netty, port = environment.application.port, module = {
        setupHttpServer(
            environment = environment,
            applicationStatus = applicationStatus,
            oauthSettings = oauthSettings
        )
    })
}

@KtorExperimentalAPI
fun Application.setupHttpServer(environment: Environment, applicationStatus: ApplicationStatus, oauthSettings: OauthSettings) {

    log.info { "Application Profile running: ${environment.application.profile}" }
    log.info { "Setup Authentication with Idp: ${oauthSettings.difiConfiguration.wellKnownMetadata.issuer}" }
    log.info { "Installing Authentication Server Name: ${oauthSettings.identityServerName}" }
    install(Authentication) {
        oauth(oauthSettings.identityServerName) {
            // will handle the back channel requests to the token endpoint
            client = HttpClient(CIO)
            // client settings from before
            providerLookup = { oauthSettings.getOauthServerSettings() }
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
        register(ContentType.Application.Json, JacksonConverter(Jackson.defaultMapper))
    }
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
    log.info { "Installing routes" }
    install(Routing) {
        selfTest(readySelfTestCheck = { applicationStatus.initialized }, aLiveSelfTestCheck = { applicationStatus.running })
        debuggerApi(oauthSettings, environment.tokenDings)
        idporten(oauthSettings, environment)
    }
    applicationStatus.initialized = true
    log.info { "Application is up and running" }
}

object Jackson {
    val defaultMapper: ObjectMapper = jacksonObjectMapper()

    init {
        defaultMapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    }
}
