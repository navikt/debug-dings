package no.nav.dingser

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
import kotlinx.coroutines.runBlocking
import no.nav.dingser.config.Environment
import no.nav.dingser.config.encodeBase64
import no.nav.dingser.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_IDPORTEN
import no.nav.dingser.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS
import no.nav.dingser.mokk.configurationServerMokk
import no.nav.dingser.mokk.generateRsaKey
import no.nav.dingser.mokk.toJWKSet
import no.nav.dingser.mokk.tokenDingsStub
import no.nav.dingser.mokk.wellknownStub
import no.nav.dingser.token.OauthSettings
import no.nav.dingser.token.tokendings.BEARER
import no.nav.dingser.token.tokendings.TokenDingsService
import no.nav.dingser.token.utils.AccessTokenResponse
import no.nav.dingser.token.utils.TokenConfiguration
import no.nav.dingser.token.utils.objectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.OAuth2Config
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.text.ParseException
import java.util.*
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
            issuer = "cluster:namespace:app1",
            audience = "cluster:namespace:app2",
            metadata = "http://localhost:${server.port()}$OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS",
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

    val tokenConfigTokenDings = TokenConfiguration(environment.tokenDings.metadata)

    val tokenDingsService = TokenDingsService(
        environment = environment,
        tokenConfiguration = tokenConfigTokenDings
    )

    describe("DIFI SERVICE, GET WellKnown Configuration and acquire Token") {
        context("Setup Stub to request Token from DIFI") {
            server.tokenDingsStub(HttpStatusCode.OK, objectMapper.writeValueAsString(accessTokenResponse))
            val jws = tokenDingsService.createJws()

            it("Validate Created token") {

                parseAndValidateSignatur(rsaKey = rsaKey.first, token = jws.token)

                val parsedToken = SignedJWT.parse(jws.token)
                val body = parsedToken.jwtClaimsSet
                val header = parsedToken.header

                header.algorithm.name shouldBeEqualTo rsaKey.first.algorithm.name
                header.keyID shouldBeEqualTo rsaKey.first.keyID
                body.audience[0] shouldBeEqualTo tokenConfigTokenDings.wellKnownMetadata.tokenEndpoint
                body.subject shouldBeEqualTo environment.tokenDings.issuer
                body.issuer shouldBeEqualTo environment.tokenDings.issuer
                body.expirationTime.after(Date()) shouldBeEqualTo true
            }

            it("Should return and serialize Token") {
                val tokenExchanged = runBlocking {
                    tokenDingsService.getToken(jws, subjectToken = "")
                }
                val bearerToken = tokenDingsService.bearerToken(tokenExchanged)
                bearerToken.substringAfter("Bearer").trim() `should be equal to` accessTokenResponse.accessToken
            }
        }
    }
    withTestApplication(moduleFunction = {
        setupHttpServer(
            environment = environment,
            applicationStatus = ApplicationStatus(),
            oauthSettings = OauthSettings(environment = environment, identityServerName = "identityTest"))
    }) {
        describe("Get a token from Authz Endpoint") {
            with(handleRequest(
                HttpMethod.Get, "/oauth"
            ) {
            }) {
                context("Get token from MockIdporten") {
                    assertEquals(302, response.status()?.value)
                    assertEquals(null, response.content)
                    assertEquals("http://localhost:8000/test/authorize?client_id=client_id&redirect_uri=http%3A%2F%2Flocalhost%3A8888%2Foauth&scope=openid&state=****&response_type=code&response_mode=query", Regex("state=(\\w+)").replace(response.headers["Location"].toString(), "state=****"))
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
