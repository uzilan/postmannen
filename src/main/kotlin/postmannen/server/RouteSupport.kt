package postmannen.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend inline fun <reified T : Any> ApplicationCall.respondResult(
    result: Result<T>,
    successStatus: HttpStatusCode = HttpStatusCode.OK
) {
    result.fold(
        onSuccess = { respond(successStatus, it) },
        onFailure = { e -> respond(HttpStatusCode.BadGateway, mapOf("error" to (e.message ?: "unknown error"))) }
    )
}

suspend fun ApplicationCall.respondUnitResult(result: Result<Unit>) {
    result.fold(
        onSuccess = { respond(HttpStatusCode.NoContent) },
        onFailure = { e -> respond(HttpStatusCode.BadGateway, mapOf("error" to (e.message ?: "unknown error"))) }
    )
}
