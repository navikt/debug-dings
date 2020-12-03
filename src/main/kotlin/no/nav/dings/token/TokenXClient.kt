package no.nav.dings.token

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parametersOf

internal const val PARAMS_GRANT_TYPE = "grant_type"
internal const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange"
internal const val PARAMS_SUBJECT_TOKEN_TYPE = "subject_token_type"
internal const val SUBJECT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt"
internal const val PARAMS_SUBJECT_TOKEN = "subject_token"
internal const val PARAMS_AUDIENCE = "audience"
internal const val PARAMS_CLIENT_ASSERTION = "client_assertion"
internal const val PARAMS_CLIENT_ASSERTION_TYPE = "client_assertion_type"
internal const val CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
internal const val BEARER = "Bearer"

suspend fun HttpClient.tokenExchange(url: String, request: OAuth2TokenExchangeRequest) =
    withLog("token request to: ", url) {
        this.submitForm<AccessTokenResponse>(
            url = url,
            formParameters = parametersOf(
                PARAMS_CLIENT_ASSERTION to listOf(request.clientAssertion),
                PARAMS_CLIENT_ASSERTION_TYPE to listOf(request.clientAssertionType),
                PARAMS_GRANT_TYPE to listOf(request.grantType),
                PARAMS_SUBJECT_TOKEN to listOf(request.subjectToken),
                PARAMS_SUBJECT_TOKEN_TYPE to listOf(request.subjectTokenType),
                PARAMS_AUDIENCE to listOf(request.audience)
            )
        )
    }

data class OAuth2TokenExchangeRequest(
    val clientAssertion: String,
    val subjectToken: String,
    val audience: String,
    val subjectTokenType: String = SUBJECT_TOKEN_TYPE,
    val clientAssertionType: String = CLIENT_ASSERTION_TYPE,
    val grantType: String = GRANT_TYPE
)
