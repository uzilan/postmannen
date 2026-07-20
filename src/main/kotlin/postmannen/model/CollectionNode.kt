package postmannen.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class CollectionNode {
    abstract val name: String

    @Serializable
    @SerialName("folder")
    data class Folder(override val name: String, val children: List<CollectionNode>) : CollectionNode()

    @Serializable
    @SerialName("item")
    data class RequestItem(
        override val name: String,
        val method: String,
        val url: String,
        val headers: List<RequestHeader>,
        val body: String?
    ) : CollectionNode()
}

@Serializable
data class RequestHeader(val key: String, val value: String)
