package postmannen.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Workspace round-trips through JSON`() {
        val workspace = Workspace(id = "ws-1", name = "Engineering", type = "team")
        val encoded = json.encodeToString(workspace)
        assertEquals(workspace, json.decodeFromString<Workspace>(encoded))
    }

    @Test
    fun `CollectionDetail with nested folder and request items round-trips`() {
        val detail = CollectionDetail(
            uid = "col-1-uid",
            name = "Auth API",
            items = listOf(
                CollectionNode.Folder(
                    "Users",
                    listOf(
                        CollectionNode.RequestItem(
                            name = "Login",
                            method = "POST",
                            url = "https://auth.example.com/login",
                            headers = listOf(RequestHeader("Content-Type", "application/json")),
                            body = "{\"user\":\"x\"}"
                        )
                    )
                ),
                CollectionNode.RequestItem(
                    name = "Health Check",
                    method = "GET",
                    url = "https://auth.example.com/health",
                    headers = emptyList(),
                    body = null
                )
            ),
            variables = listOf(CollectionVariable(key = "base_url", value = "https://x", enabled = true))
        )
        val encoded = json.encodeToString(detail)
        assertEquals(detail, json.decodeFromString<CollectionDetail>(encoded))
    }

    @Test
    fun `EnvironmentDetail round-trips`() {
        val detail = EnvironmentDetail(
            id = "env-1",
            uid = "env-1-uid",
            name = "Staging",
            values = listOf(EnvironmentValue(key = "BASE_URL", value = "https://staging", enabled = true, type = "default"))
        )
        val encoded = json.encodeToString(detail)
        assertEquals(detail, json.decodeFromString<EnvironmentDetail>(encoded))
    }
}
