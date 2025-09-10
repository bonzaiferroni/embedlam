package ponder.embedlam.ui

import kabinet.clients.OllamaClient
import kabinet.clients.OllamaModel
import kabinet.gemini.GeminiClient
import kabinet.gemini.generateEmbedding
import kabinet.model.LabeledEnum
import kabinet.utils.cosineDistances
import kabinet.utils.format
import kabinet.utils.normalize
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import ponder.embedlam.AppDb
import ponder.embedlam.AppClients
import ponder.embedlam.model.data.Block
import ponder.embedlam.model.data.BlockEmbedding
import ponder.embedlam.model.data.BlockEmbeddingId
import ponder.embedlam.model.data.BlockId
import ponder.embedlam.model.data.ModelId
import ponder.embedlam.model.data.Tag
import ponder.embedlam.model.data.TagId
import pondui.ui.core.ModelState
import pondui.ui.core.StateModel
import pondui.utils.FileDao
import kotlin.system.measureTimeMillis

class BlockFeedModel(
    private val blockDao: FileDao<Block> = AppDb.blockDao,
    private val blockEmbeddingDao: FileDao<BlockEmbedding> = AppDb.blockEmbeddingDao,
    private val tagDao: FileDao<Tag> = AppDb.tagDao,
    private val ollama: OllamaClient = OllamaClient(),
    private val geminiClient: GeminiClient = AppClients.gemini
) : StateModel<BlockFeedState>() {
    override val state = ModelState(BlockFeedState())

    private var textEmbeddings: Map<ModelId, FloatArray> = emptyMap()
    private var embedJob: Job? = null
    private var feedEmbeddings: Map<ModelId, Map<BlockId, BlockEmbedding>> = emptyMap()

    init {
        ioCollect(blockDao.items) { blocks ->
            setStateFromMain { it.copy(blocks = blocks) }
        }
        ioCollect(blockEmbeddingDao.items) { items ->
            feedEmbeddings = items.groupBy { it.modelId }.mapValues { (_, list) ->
                list.associateBy { it.blockId }
            }
        }
        ioCollect(tagDao.items) { tags ->
            setStateFromMain { it.copy(tags = tags)}
        }
    }

    fun setText(value: String) {
        setState { it.copy(text = value) }
        val text = value.takeIf { it.isNotEmpty() } ?: return
        embedJob?.cancel()
        embedJob = ioLaunch {
            delay(1000L)

            textEmbeddings = coroutineScope {
                embedModels.map { m -> async { ModelId(m.modelName) to embed(m, text) } }
                    .awaitAll()
                    .toMap()
            }

            val distances = textEmbeddings.entries.associate { (modelId, embedding) ->
                val blockEmbeddings = stateNow.blocks.takeIf { it.size == feedEmbeddings[modelId]?.size }
                    ?.mapNotNull { feedEmbeddings[modelId]?.get(it.blockId)?.embedding }
                val cosineSimilarities = blockEmbeddings?.let { cosineDistances(embedding, it) }
                val minMaxScales = cosineSimilarities?.minMaxScale()
                val distances = if (cosineSimilarities != null && minMaxScales != null) cosineSimilarities.mapIndexed { index, similarity ->
                    SemanticDistance(similarity, minMaxScales[index])
                } else { null }
                modelId to distances
            }
            setStateFromMain { it.copy(distances = distances) }
        }
    }

    private suspend fun embed(mode: EmbedMode, text: String): FloatArray {
        val embedding: FloatArray
        val millis = measureTimeMillis {
            when(mode) {
                GeminiEmbed -> {
                    embedding = geminiClient.generateEmbedding(text)?.normalize() ?: error("embedding not found")
                }
                is OllamaEmbed -> {
                    embedding = ollama.embed(text, mode.model)?.embeddings?.firstOrNull()?.normalize() ?: error("embedding not found")
                }
            }
        }

        val perChar = (millis / text.length.toFloat()).format(1)
        println("${mode.modelName} dims: ${embedding.size} millis: $millis perchar: $perChar")
        return embedding
    }

    fun setLabel(value: String) = setState { it.copy(label = value) }

    fun addBlock() {
        val text = stateNow.text.takeIf { it.isNotEmpty() } ?: return
        val label = stateNow.label.takeIf { it.isNotEmpty() } ?: "${text.take(50)}..."
        ioLaunch {
            val blockId = BlockId.random()
            val now = Clock.System.now()
            blockDao.create(
                Block(
                    blockId = blockId,
                    tagIds = stateNow.applyTags,
                    text = text,
                    label = label,
                    createdAt = now,
                )
            )
            textEmbeddings.entries.forEach { (modelId, embedding) ->
                blockEmbeddingDao.create(
                    BlockEmbedding(
                        blockEmbeddingId = BlockEmbeddingId.random(),
                        blockId = blockId,
                        modelId = modelId,
                        embedding = embedding,
                        createdAt = now
                    )
                )
            }
            setStateFromMain { it.copy(text = "", label = "") }
        }
    }

    fun refreshEmbeddings() {
        ioLaunch {
            val now = Clock.System.now()
            val blockEmbeddings = stateNow.blocks.flatMap { block ->
                embedModels.map { model ->
                    val modelId = ModelId(model.modelName)
                    val embedding = embed(model, block.text)
                    val blockEmbeddingId =
                        feedEmbeddings[modelId]?.get(block.blockId)?.blockEmbeddingId ?: BlockEmbeddingId.random()
                    BlockEmbedding(
                        blockEmbeddingId = blockEmbeddingId,
                        blockId = block.blockId,
                        modelId = modelId,
                        embedding = embedding,
                        createdAt = now
                    )
                }
            }
            blockEmbeddingDao.batchUpsert(blockEmbeddings)
        }
    }

    fun setValueType(value: ValueType) = setState { it.copy(valueType = value) }

    fun setTagLabel(value: String) = setState { it.copy(tagLabel = value) }

    fun createTag(maxColorIndex: Int) {
        val tagLabel = stateNow.tagLabel.takeIf { it.isNotEmpty() } ?: return
        val tagColorIndex = stateNow.tagColorIndex
        ioLaunch {
            val tagId = TagId.random()
            tagDao.create(Tag(
                tagId = tagId,
                label = tagLabel,
                colorIndex = tagColorIndex,
                createdAt = Clock.System.now()
            ))

            setStateFromMain { it.copy(
                tagColorIndex = tagColorIndex + 1 % maxColorIndex,
                tagLabel = "",
                applyTags = it.applyTags + tagId
            )}
        }
    }

    fun removeTag(tag: Tag) {
        ioLaunch {
            tagDao.delete(tag)
            val updatedBlocks = stateNow.blocks.mapNotNull {
                if (it.tagIds.contains(tag.tagId)) it.copy(tagIds = it.tagIds - tag.tagId) else null
            }
            blockDao.batchUpsert(updatedBlocks)

            setStateFromMain { it.copy(applyTags = it.applyTags - tag.tagId) }
        }
    }

    fun addTag(tag: Tag, block: Block) {
        ioLaunch { blockDao.update(block.copy(tagIds = block.tagIds + tag.tagId)) }
    }

    fun removeTag(tag: Tag, block: Block) {
        ioLaunch { blockDao.update(block.copy(tagIds = block.tagIds - tag.tagId)) }
    }
}

data class BlockFeedState(
    val blocks: List<Block> = emptyList(),
    val text: String = "",
    val label: String = "",
    val distances: Map<ModelId, List<SemanticDistance>?> = emptyMap(),
    val valueType: ValueType = ValueType.Distance,
    val tags: List<Tag> = emptyList(),
    val applyTags: Set<TagId> = emptySet(),
    val tagLabel: String = "",
    val tagColorIndex: Int = 0,
)

enum class ValueType(override val label: String): LabeledEnum<ValueType> {
    Distance("Distance"),
    DistanceScaled("Distance Scaled"),
    Similarity("Similarity"),
    SimilarityScaled("Similarity Scaled")
}

val embedModels = listOf(
    OllamaEmbed(OllamaModel.NomicEmbed),
    OllamaEmbed(OllamaModel.MxbaiEmbed),
    OllamaEmbed(OllamaModel.GemmaEmbed),
    GeminiEmbed
)

sealed interface EmbedMode {
    val modelName: String
}

data class OllamaEmbed(
    val model: OllamaModel
): EmbedMode {
    override val modelName get() = model.apiLabel
}

object GeminiEmbed: EmbedMode {
    override val modelName = "gemini-embed-001"
}

fun List<Float>.minMaxScale(targetMin: Float = 0f, targetMax: Float = 1f): List<Float> {
    if (isEmpty()) return this
    var min = Float.POSITIVE_INFINITY
    var max = Float.NEGATIVE_INFINITY
    for (v in this) {
        if (v < min) min = v
        if (v > max) max = v
    }
    val srcRange = max - min
    val dstRange = targetMax - targetMin
    if (srcRange == 0f || dstRange == 0f) return List<Float>(size) { targetMin }
    return List<Float>(size) { ((this[it] - min) / srcRange) * dstRange + targetMin }
}

data class SemanticDistance(
    val distance: Float,
    val distanceScaled: Float,
) {
    val similarity get() = 1 - distance
    val similarityScaled get() = 1 - distanceScaled

    fun getValue(valueType: ValueType) = when (valueType) {
        ValueType.Distance -> distance
        ValueType.DistanceScaled -> distanceScaled
        ValueType.Similarity -> similarity
        ValueType.SimilarityScaled -> similarityScaled
    }
}