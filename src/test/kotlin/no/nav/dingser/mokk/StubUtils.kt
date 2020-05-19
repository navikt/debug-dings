package no.nav.dingser.mokk

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import no.nav.dingser.config.APPLICATION_JSON
import no.nav.dingser.token.utils.objectMapper

internal const val TOKEN_PATH = "/token"

fun WireMockServer.idportenStub(status: HttpStatusCode, body: String): StubMapping =
    stubFor(
        WireMock.post(WireMock.urlEqualTo(TOKEN_PATH))
            .withRequestBody(WireMock.matching("(grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=).*"))
            .withHeader(
                HttpHeaders.ContentType,
                WireMock.equalTo(ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8).toString())
            )
            .willReturn(
                WireMock.aResponse()
                    .withStatus(status.value)
                    .withHeader(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                    .withBody(body)
            )
    )

fun WireMockServer.tokenDingsStub(status: HttpStatusCode, body: String): StubMapping =
    stubFor(
        WireMock.post(WireMock.urlEqualTo(TOKEN_PATH))
            .withRequestBody(WireMock.matching(".*(client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type).*"))
            .withHeader(
                HttpHeaders.ContentType,
                WireMock.equalTo(ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8).toString())
            )
            .willReturn(
                WireMock.aResponse()
                    .withStatus(status.value)
                    .withHeader(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                    .withBody(body)
            )
    )

fun WireMockServer.wellknownStub(path: String, configurationServerMokk: ConfigurationServerMokk): StubMapping =
    stubFor(
        WireMock.get(WireMock.urlEqualTo(path))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HttpStatusCode.OK.value)
                    .withHeader(HttpHeaders.ContentType, APPLICATION_JSON.toString())
                    .withBody(objectMapper.writeValueAsString(configurationServerMokk))
            )
    )

internal fun generateRsaKey(keyId: String = UUID.randomUUID().toString(), keySize: Int = 2048): Pair<RSAKey, PrivateKey?> {
    var privateKey: PrivateKey?
    return Pair(KeyPairGenerator.getInstance("RSA").apply { initialize(keySize) }.generateKeyPair()
        .let {
            privateKey = it.private
            RSAKey.Builder(it.public as RSAPublicKey)
                .privateKey(it.private as RSAPrivateKey)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build()
        }, privateKey)
}
