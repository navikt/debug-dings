package no.nav.dings

import no.nav.dings.config.Environment

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
            Environment.TokenX(
                wellKnownUrl = "http://localhost:8080/.well-known/oauth-authorization-server"
            )
        ),
        ApplicationStatus()
    ).start(wait = true)
}
