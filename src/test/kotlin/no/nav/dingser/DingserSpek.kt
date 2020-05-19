package no.nav.dingser

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import org.jetbrains.spek.api.Spek

object DingserSpek : Spek({

    val mockServer = MockOAuth2Server(config = OAuth2Config(interactiveLogin = false))
    mockServer.start(port = 8888)
})
