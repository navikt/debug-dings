package no.nav.dingser

import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dingser.config.Environment

private val log = KotlinLogging.logger { }

data class ApplicationStatus(var running: Boolean = true, var initialized: Boolean = false)
data class AppConfiguration(
    val applicationStatus: ApplicationStatus = ApplicationStatus(
        running = true,
        initialized = false
    ),
    val environment: Environment = Environment()
)

fun main() = startServer()

fun startServer() {
    runBlocking {
        val applicationStatus = AppConfiguration().applicationStatus
        val environment = AppConfiguration().environment
        val dingserServer = createHttpServer(environment, applicationStatus)

        DefaultExports.initialize()
        Runtime.getRuntime().addShutdownHook(Thread {
            Thread {
                log.info { "Shutdown hook called, shutting down gracefully" }
                applicationStatus.initialized = false
                applicationStatus.running = false
                dingserServer.stop(1, 5)
            }
        })
        dingserServer.start(wait = true)
    }
}
