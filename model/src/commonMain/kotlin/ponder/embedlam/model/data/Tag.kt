package ponder.embedlam.model.data

import kabinet.db.TableId
import kabinet.utils.randomUuidString
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
data class Tag(
    val tagId: TagId,
    val label: String,
    val colorIndex: Int,
    val createdAt: Instant
)

@JvmInline
@Serializable
value class TagId(override val value: String) : TableId<String> {
    companion object { fun random() = TagId(randomUuidString()) }
}