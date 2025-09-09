package ponder.embedlam.model.data

import kabinet.db.TableId
import kabinet.utils.randomUuidString
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Suppress("ArrayInDataClass")
@Serializable
data class BlockEmbedding(
    val blockEmbeddingId: BlockEmbeddingId,
    val blockId: BlockId,
    val modelId: ModelId,
    val embedding: FloatArray,
    val createdAt: Instant,
)

@JvmInline @Serializable
value class BlockEmbeddingId(override val value: String): TableId<String> {
    companion object {
        fun random() = BlockEmbeddingId(randomUuidString())
    }
}

@JvmInline @Serializable
value class ModelId(override val value: String): TableId<String>