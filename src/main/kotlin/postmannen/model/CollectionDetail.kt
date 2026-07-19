package postmannen.model

import kotlinx.serialization.Serializable

@Serializable
data class CollectionDetail(
    val uid: String,
    val name: String,
    val items: List<CollectionNode>,
    val variables: List<CollectionVariable>
)
