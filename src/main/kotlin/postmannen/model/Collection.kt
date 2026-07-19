package postmannen.model

import kotlinx.serialization.Serializable

@Serializable
data class Collection(val id: String, val name: String, val uid: String)
