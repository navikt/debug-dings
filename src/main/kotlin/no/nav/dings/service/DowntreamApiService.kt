package no.nav.dings.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.dings.config.Environment

@KtorExperimentalAPI
class DowntreamApiService(
    private val config: Environment.TokenX
) {

    var isOnPrem: Boolean = false

    fun audience() =
        when {
            isOnPrem -> {
                config.targetONPREMAudience
            }
            else -> {
                config.targetGCPAudience
            }
        }
}
