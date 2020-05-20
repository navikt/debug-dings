package no.nav.dingser.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class Keys(
    val keys: List<Jwks>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Jwks(
    val kty: String,
    val e: String,
    val use: String,
    val kid: String,
    val alg: String,
    val n: String
)
