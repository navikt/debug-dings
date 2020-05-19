package no.nav.dingser

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.server.testing.withTestApplication
import no.nav.dingser.config.Environment
import no.nav.dingser.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS
import no.nav.dingser.mokk.configurationServerMokk
import no.nav.dingser.mokk.wellknownStub
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import org.jetbrains.spek.api.Spek

private const val MOCK_PORT = 8888

// TODO
object DingserSpek : Spek({

    // Mock TokenDings server
    val server = WireMockServer(
        WireMockConfiguration.options().dynamicPort().notifier(Slf4jNotifier(true))
    ).also { it.start() }

    // Mock Difi Server
    val mockServer = MockOAuth2Server(config = OAuth2Config(interactiveLogin = false))
    mockServer.start(port = MOCK_PORT)

    val appconfig = AppConfiguration(
        applicationStatus = ApplicationStatus(
            running = true,
            initialized = false
        ),
        environment = Environment(
            application = Environment.Application(),
            idporten = Environment.Idporten(
                metadata = "http://localhost:$MOCK_PORT/test/.well-known/openid-configuration"
            ),
            tokenDings = Environment.TokenDings(
                metadata = "http://localhost:${server.port()}$OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS"
            )
        )
    )

    // Mock Wellknown TokenDings
    val configurationServerMokk = configurationServerMokk(server.port())
    server.wellknownStub(OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS, configurationServerMokk)

    withTestApplication(moduleFunction = { setupHttpServer(appconfig.environment, appconfig.applicationStatus) }) {
    }
})
