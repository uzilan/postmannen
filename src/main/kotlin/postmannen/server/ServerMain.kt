package postmannen.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import postmannen.service.CachingPostmanApiService
import postmannen.service.ClaudeCliServiceImpl
import postmannen.service.PostmanApiServiceImpl
import kotlin.system.exitProcess

fun main() {
    val apiKey = System.getenv("POSTMAN_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println("POSTMAN_API_KEY environment variable is required.")
        exitProcess(1)
    }

    val service = CachingPostmanApiService(PostmanApiServiceImpl(apiKey))
    val claudeService = ClaudeCliServiceImpl(apiKey)

    embeddedServer(CIO, port = 8080) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        routing {
            workspaceRoutes(service)
            collectionRoutes(service)
            environmentRoutes(service)
            chatRoutes(claudeService)
        }
    }.start(wait = true)
}
