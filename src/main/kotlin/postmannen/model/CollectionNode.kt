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
    data class RequestItem(override val name: String) : CollectionNode()
}
