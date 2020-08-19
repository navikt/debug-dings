package no.nav.dings

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.dings.config.Environment
import no.nav.dings.config.encodeBase64
import no.nav.dings.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_IDPORTEN
import no.nav.dings.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS
import no.nav.dings.mokk.configurationServerMokk
import no.nav.dings.mokk.generateRsaKey
import no.nav.dings.mokk.toJWKSet
import no.nav.dings.mokk.tokenDingsStub
import no.nav.dings.mokk.wellknownStub
import no.nav.dings.token.tokendings.BEARER
import no.nav.dings.token.tokendings.TokenDingsService
import no.nav.dings.token.AccessTokenResponse
import no.nav.dings.token.utils.objectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.text.ParseException
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.fail

@KtorExperimentalAPI
object TokenDingsServiceSpek : Spek({

    val server = WireMockServer(
        WireMockConfiguration.options().dynamicPort().notifier(Slf4jNotifier(true))
    ).also { it.start() }

    // Mock Wellknown
    val configurationServerMokk = configurationServerMokk(server.port())
    server.wellknownStub(OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS, configurationServerMokk)

    // Difi Server
    val DIFI_PORT = 8000
    val APP_PORT = 8888
    MockOAuth2Server(OAuth2Config(interactiveLogin = false)).start(DIFI_PORT)

    val rsaKey = generateRsaKey()

    // Setup environment for testing
    val environment = Environment(
        application = Environment.Application(
            profile = "TEST",
            port = APP_PORT,
            redirectUrl = "http://localhost:$APP_PORT/oauth"
        ),
        idporten = Environment.Idporten(
            "http://localhost:$DIFI_PORT/test$OAUTH_SERVER_WELL_KNOWN_PATH_IDPORTEN"
        ),
        tokenDings = Environment.TokenDings(
            clientId = "cluster:namespace:app1",
            gcpAudience = "cluster:namespace:app2",
            wellKnownUrl = "http://localhost:${server.port()}$OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS",
            // jwksPublic = objectMapper.writeValueAsString(
            //    Keys(listOf(objectMapper.readValue(rsaKey.first.toPublicJWK().toJSONString())))),
            jwksPrivate = toJWKSet(rsaKey.first, isPublic = false)!!.toJSONString()
            // privateKeyBase64 = Base64.getEncoder().encodeToString(rsaKey.second!!.encoded)
        )
    )

    // Mock TOKEN
    val accessTokenString = encodeBase64("client".toByteArray())
    val expiresIn = 200
    val accessTokenResponse = AccessTokenResponse(
        accessToken = accessTokenString,
        issuedTokenType = "urn:ietf:params:oauth:token-type:access_token",
        expiresIn = expiresIn,
        tokenType = BEARER
    )

    val tokenDingsService = TokenDingsService(environment.tokenDings)

    describe("DIFI SERVICE, GET WellKnown Configuration and acquire Token") {
        context("Setup Stub to request Token from DIFI") {
            server.tokenDingsStub(HttpStatusCode.OK, objectMapper.writeValueAsString(accessTokenResponse))
            val jws = tokenDingsService.clientAssertion()

            it("Validate Created token") {

                parseAndValidateSignatur(rsaKey = rsaKey.first, token = jws)

                val parsedToken = SignedJWT.parse(jws)
                val body = parsedToken.jwtClaimsSet
                val header = parsedToken.header

                header.algorithm.name shouldBeEqualTo rsaKey.first.algorithm.name
                header.keyID shouldBeEqualTo rsaKey.first.keyID
                body.audience[0] shouldBeEqualTo environment.tokenDings.metadata.tokenEndpoint
                body.subject shouldBeEqualTo environment.tokenDings.clientId
                body.issuer shouldBeEqualTo environment.tokenDings.clientId
                body.expirationTime.after(Date()) shouldBeEqualTo true
            }
        }
    }
    withTestApplication(moduleFunction = {
        setupHttpServer(
            environment = environment,
            applicationStatus = ApplicationStatus()
        )
    }) {
        describe("Check redirect from idp provider") {
            with(handleRequest(
                HttpMethod.Get, "/oauth"
            ) {
            }) {
                context("Get right url") {
                    assertEquals(302, response.status()?.value)
                    assertEquals(null, response.content)
                    assertEquals(
                        "http://localhost:8000/test/authorize" +
                            "?client_id=client_id&redirect_uri=http%3A%2F%2Flocalhost%3A8888%2Foauth&scope=openid" +
                            "&state=****&response_type=code&response_mode=query",
                        Regex("state=(\\w+)").replace(response.headers["Location"].toString(), "state=****")
                    )
                }
            }
        }
    }
    afterGroup {
        server.stop()
    }
})

internal fun parseAndValidateSignatur(rsaKey: RSAKey, token: String) {
    val signedJwt = try {
        SignedJWT.parse(token)
    } catch (e: ParseException) {
        fail("Could not parse token: ${e.message}")
    }
    try {
        signedJwt.verify(RSASSAVerifier(rsaKey))
    } catch (e: Exception) {
        fail("Could not validate signature: ${e.message}")
    }
}
