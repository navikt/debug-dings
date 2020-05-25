package no.nav.dingser.api

import com.auth0.jwk.Jwk
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.application.call
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging
import no.nav.dingser.config.Environment
import java.security.interfaces.RSAPublicKey

private val log = KotlinLogging.logger { }

@KtorExperimentalAPI
fun Routing.login(
    environment: Environment
) {
    val idporten = environment.idporten
    authenticate(idporten.oauth2ServerSettings.name) {
        get("/oauth") {
            val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
            when (val decodedJWT = idporten.verify(principal)) {
                null -> call.respond(HttpStatusCode.InternalServerError, "no id_token found in tokenresponse")
                else -> {
                    call.response.cookies.append(environment.login.idTokenCookie, decodedJWT.token)
                    call.response.cookies.appendExpired(environment.login.redirectCookie)
                    call.respondRedirect(call.request.cookies[environment.login.redirectCookie] ?: environment.login.afterLoginUri)
                }
            }
        }
    }
}

fun Environment.Idporten.verify(tokenResponse: OAuthAccessTokenResponse.OAuth2?): DecodedJWT? =
    tokenResponse?.idToken()?.let {
        jwkProvider[JWT.decode(it).keyId].idTokenVerifier(
            clientId,
            metadata.issuer
        ).verify(it)
    }

fun OAuthAccessTokenResponse.OAuth2.idToken(): String? = extraParameters["id_token"]

fun Jwk.idTokenVerifier(clientId: String, issuer: String): JWTVerifier =
    JWT.require(this.RSA256())
        .withAudience(clientId)
        .withIssuer(issuer)
        .build()

internal fun Jwk.RSA256() = Algorithm.RSA256(publicKey as RSAPublicKey, null)
