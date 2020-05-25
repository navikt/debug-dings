package no.nav.dingser

import no.nav.dingser.config.Environment

fun main() {
    createHttpServer(
        Environment(
            Environment.Application(
                port = 8282,
                redirectUrl = "http://localhost:8282/oauth"
            ),
            Environment.Login(),
            Environment.Idporten(
                wellKnownUrl = "http://localhost:1111/mock1/.well-known/openid-configuration"
            ),
            Environment.TokenDings(
                wellKnownUrl = "http://localhost:8080/.well-known/oauth-authorization-server"
            )
        ),
        ApplicationStatus()
    ).start(wait = true)
}