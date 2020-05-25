package no.nav.dingser

import io.ktor.http.HttpStatusCode

class HttpException(
    val httpStatusCode: HttpStatusCode,
    message: String,
    throwable: Throwable? = null
) : RuntimeException(message, throwable)
