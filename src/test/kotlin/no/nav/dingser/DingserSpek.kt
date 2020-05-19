package no.nav.dingser

import io.ktor.server.testing.withTestApplication
import no.nav.dingser.config.Environment
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import org.jetbrains.spek.api.Spek

object DingserSpek : Spek({

    val mockServer = MockOAuth2Server(config = OAuth2Config(interactiveLogin = false))
    mockServer.start(port = 8888)

    val appconfig = AppConfiguration(
        applicationStatus = ApplicationStatus(
            running = true,
            initialized = false
        ),
        environment = Environment()
    )

    withTestApplication(moduleFunction = { setupHttpServer(appconfig.environment, appconfig.applicationStatus) }) {
    }
})
