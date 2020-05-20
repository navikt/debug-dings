package no.nav.dingser

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nimbusds.jwt.SignedJWT
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.dingser.config.Environment
import no.nav.dingser.config.encodeBase64
import no.nav.dingser.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_IDPORTEN
import no.nav.dingser.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS
import no.nav.dingser.mokk.configurationServerMokk
import no.nav.dingser.mokk.generateRsaKey
import no.nav.dingser.mokk.idportenStub
import no.nav.dingser.mokk.toJWKSet
import no.nav.dingser.mokk.tokenDingsStub
import no.nav.dingser.mokk.wellknownStub
import no.nav.dingser.token.tokendings.TokenDingsService
import no.nav.dingser.token.utils.AccessTokenResponse
import no.nav.dingser.token.utils.TokenConfiguration
import no.nav.dingser.token.utils.objectMapper
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.text.ParseException
import java.util.*
import kotlin.test.fail

object TokenDingsServiceSpek : Spek({

    // Mock DIFI and TokenDings server
    val server = WireMockServer(
        WireMockConfiguration.options().dynamicPort().notifier(Slf4jNotifier(true))
    ).also { it.start() }

    // Mock Wellknown
    val configurationServerMokk = configurationServerMokk(server.port())

    val rsaKey = generateRsaKey()

    // Setup environment for testing
    val environment = Environment(
        application = Environment.Application(
            profile = "TEST"
        ),
        idporten = Environment.Idporten(
            "http://localhost:${server.port()}$OAUTH_SERVER_WELL_KNOWN_PATH_IDPORTEN"
        ),
        tokenDings = Environment.TokenDings(
            issuer = "cluster:namespace:app1",
            audience = "cluster:namespace:app2",
            metadata = "http://localhost:${server.port()}$OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS",
            // clientId = "1010",
            // jwksPublic = objectMapper.writeValueAsString(
            //    Keys(listOf(objectMapper.readValue(rsaKey.first.toPublicJWK().toJSONString())))),
            jwksPrivate = toJWKSet(rsaKey.first, isPublic = false)!!.toJSONString()
            // privateKeyBase64 = Base64.getEncoder().encodeToString(rsaKey.second!!.encoded)
        )
    )

    println(toJWKSet(rsaKey.first, isPublic = false))

    // Mock TOKEN
    val accessTokenString = encodeBase64("client".toByteArray())
    val expiresIn = 200
    val mockScope = environment.idporten.scope
    val accessTokenResponse = AccessTokenResponse(
        accessToken = accessTokenString, expiresIn = expiresIn, scope = mockScope
    )

    // Setup server for wellknown, both DIFI and TokenDings are mocked here
    server.wellknownStub(OAUTH_SERVER_WELL_KNOWN_PATH_IDPORTEN, configurationServerMokk)
    server.wellknownStub(OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS, configurationServerMokk)
    server.idportenStub(HttpStatusCode.OK, objectMapper.writeValueAsString(accessTokenResponse))

    // Setup Test Classes, They are alike - using same issuer, in test, but nice to separate them for reading
    val tokenConfigTokenDings = TokenConfiguration(environment.tokenDings.metadata)

    val tokenDingsService = TokenDingsService(
        environment = environment,
        subjectToken = "",
        tokenConfiguration = tokenConfigTokenDings
    )

    describe("DIFI SERVICE, GET WellKnown Configuration and acquire Token") {
        context("Setup Stub to request Token from DIFI") {
            server.tokenDingsStub(HttpStatusCode.OK, objectMapper.writeValueAsString(accessTokenResponse))
            val jws = tokenDingsService.createJws()

            it("validate Created token") {
                try {
                    SignedJWT.parse(jws.token)
                } catch (e: ParseException) {
                    fail("Could not parse token")
                }
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
                    tokenDingsService.getToken(jws)
                }
                val bearerToken = tokenDingsService.bearerToken(tokenExchanged)
                bearerToken.substringAfter("Bearer").trim() `should be equal to` accessTokenResponse.accessToken
            }
        }
    }
    afterGroup {
        server.stop()
    }
})
