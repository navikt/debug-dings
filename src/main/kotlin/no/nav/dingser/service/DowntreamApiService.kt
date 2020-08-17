package no.nav.dingser.service

import no.nav.dingser.config.Environment

class DowntreamApiService(
    private val config: Environment.TokenDings
) {

    var isOnPrem: Boolean = false

    fun audience() =
        when {
            isOnPrem -> {
                config.onpremAudience
            }
            else -> {
                config.gcpAudience
            }
        }
}
