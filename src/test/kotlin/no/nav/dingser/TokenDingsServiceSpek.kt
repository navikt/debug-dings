package no.nav.dingser

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpStatusCode
import java.util.Base64
import kotlinx.coroutines.runBlocking
import no.nav.dingser.config.Environment
import no.nav.dingser.config.encodeBase64
import no.nav.dingser.models.Keys
import no.nav.dingser.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_IDPORTEN
import no.nav.dingser.mokk.OAUTH_SERVER_WELL_KNOWN_PATH_TOKENDINGS
import no.nav.dingser.mokk.configurationServerMokk
import no.nav.dingser.mokk.generateRsaKey
import no.nav.dingser.mokk.idportenStub
import no.nav.dingser.mokk.tokenDingsStub
import no.nav.dingser.mokk.wellknownStub
import no.nav.dingser.token.tokendings.TokenDingsService
import no.nav.dingser.token.utils.AccessTokenResponse
import no.nav.dingser.token.utils.TokenConfiguration
import no.nav.dingser.token.utils.objectMapper
import org.amshove.kluent.`should be equal to`
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

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
        tokenDings = Environment.TokenDings(
            issuer = "http://localhost:${server.port()}",
            clientId = "1010",
            jwksPublic = objectMapper.writeValueAsString(
                Keys(listOf(objectMapper.readValue(rsaKey.first.toPublicJWK().toJSONString())))),
            // jwksPrivate =
            privateKeyBase64 = Base64.getEncoder().encodeToString(rsaKey.second!!.encoded)

        )
    )

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
    val tokenConfigMaskinporten = TokenConfiguration(environment.idporten.metadata)
    val tokenConfigTokenDings = TokenConfiguration(environment.tokenDings.metadata)

    val tokenDingsService = TokenDingsService(
        environment = environment,
        subjectToken = "",
        tokenConfiguration = tokenConfigTokenDings
    )

    describe("DIFI SERVICE, GET WellKnown Configuration and acquire Token") {
        context("Setup Stub to request Token from DIFI") {
            server.tokenDingsStub(HttpStatusCode.OK, objectMapper.writeValueAsString(accessTokenResponse))
            it("Should return and serialize Token") {
                val result = runBlocking {
                    tokenDingsService.bearerToken(tokenDingsService.createJws())
                }
                result.substringAfter("Bearer").trim() `should be equal to` accessTokenResponse.accessToken
            }
        }
    }
    afterGroup {
        server.stop()
    }
})
