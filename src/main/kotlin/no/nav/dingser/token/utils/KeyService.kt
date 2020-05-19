package no.nav.dingser.token.utils

import com.fasterxml.jackson.module.kotlin.readValue
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import mu.KotlinLogging
import no.nav.dingser.models.Keys

private val log = KotlinLogging.logger { }

internal fun base64ToPrivateKey(privateBase64: String): PrivateKey? {
    log.info { "From base64 key to PrivateKey" }
    val keyBytes: ByteArray = Base64.getDecoder().decode(privateBase64)
    val keySpec = PKCS8EncodedKeySpec(keyBytes)
    val fact: KeyFactory = KeyFactory.getInstance("RSA")
    return fact.generatePrivate(keySpec)
}

internal fun getKeys(keys: String) = objectMapper.readValue<Keys>(keys)
