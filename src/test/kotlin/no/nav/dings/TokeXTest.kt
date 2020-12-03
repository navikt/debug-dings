package no.nav.dings

import com.nimbusds.jwt.SignedJWT
import io.ktor.http.HttpMethod
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.dings.token.Authentication
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals

@KtorExperimentalAPI
class TokeXTest {

    @Test
    fun `Generated tokenX token should be valid and have correct set of claims`() {
        withMockOAuth2Server {
            val env = setupTestEnvironment(this)
            val tokenXClient = Authentication(env.tokenX)
            val jwt = tokenXClient.clientAssertion(env.tokenX.metadata.tokenEndpoint)

            parseAndValidateSignatur(rsaKey = rsaKey.first, token = jwt)

            val parsedToken = SignedJWT.parse(jwt)
            val body = parsedToken.jwtClaimsSet
            val header = parsedToken.header

            header.algorithm.name shouldBeEqualTo rsaKey.first.algorithm.name
            header.keyID shouldBeEqualTo rsaKey.first.keyID
            body.audience[0] shouldBeEqualTo env.tokenX.metadata.tokenEndpoint
            body.subject shouldBeEqualTo env.tokenX.clientId
            body.issuer shouldBeEqualTo env.tokenX.clientId
            body.expirationTime.after(Date()) shouldBeEqualTo true
        }
    }

    @Test
    fun `A call to configured authorizeEndpoint should be redirected from Idp with 302 response status and Location header`() {
        withMockOAuth2Server {
            val mockOAuth2Server = this
            val env = setupTestEnvironment(mockOAuth2Server)
            withTestApplication(
                moduleFunction = {
                    setupHttpServer(
                        environment = env,
                        applicationStatus = ApplicationStatus()
                    )
                }
            ) {
                with(
                    handleRequest(
                        HttpMethod.Get,
                        "/oauth2/callback"
                    ) {
                    }
                ) {
                    assertEquals(302, response.status()?.value)
                    assertEquals(null, response.content)
                    println(response.headers.allValues())
                    assertEquals(
                        "${mockOAuth2Server.baseUrl()}idporten/authorize" +
                            "?client_id=client_id&redirect_uri=http%3A%2F%2Flocalhost%3A8888%2Foauth2%2Fcallback&scope=openid" +
                            "&state=****&response_type=code&response_mode=query",
                        Regex("state=(\\w+)").replace(response.headers["Location"].toString(), "state=****")
                    )
                }
            }
        }
    }

    fun `another one`() {
        withMockOAuth2Server {
            val mockOAuth2Server = this
            val env = setupTestEnvironment(mockOAuth2Server)
            withTestApplication(
                moduleFunction = {
                    setupHttpServer(
                        environment = env,
                        applicationStatus = ApplicationStatus()
                    )
                }
            ) {
                with(
                    handleRequest(
                        HttpMethod.Get,
                        "/oauth2/callback"
                    ) {
                    }
                ) {
                    assertEquals(302, response.status()?.value)
                    assertEquals(null, response.content)
                    println(response.headers.allValues())
                    assertEquals(
                        "${mockOAuth2Server.baseUrl()}idporten/authorize" +
                            "?client_id=client_id&redirect_uri=http%3A%2F%2Flocalhost%3A8888%2Foauth%2Fcallback&scope=openid" +
                            "&state=****&response_type=code&response_mode=query",
                        Regex("state=(\\w+)").replace(response.headers["Location"].toString(), "state=****")
                    )
                }
            }
        }
    }
}
