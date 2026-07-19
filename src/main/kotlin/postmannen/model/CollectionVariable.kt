package postmannen.model

import kotlinx.serialization.Serializable

@Serializable
data class CollectionVariable(val key: String, val value: String, val enabled: Boolean)
