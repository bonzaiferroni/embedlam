package ponder.embedlam.model.data

import kabinet.db.TableId
import kabinet.utils.randomUuidString
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
data class Block(
    val blockId: BlockId,
    val tagIds: Set<TagId>,
    val label: String,
    val text: String,
    // val embeddings: Map<ModelId, FloatArray>,
    val createdAt: Instant
)

@JvmInline @Serializable
value class BlockId(override val value: String): TableId<String> {
    companion object { fun random() = BlockId(randomUuidString()) }
}