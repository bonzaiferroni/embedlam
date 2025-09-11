package ponder.embedlam.ui

import kabinet.clients.OllamaClient
import kabinet.clients.OllamaModel
import kabinet.gemini.GeminiClient
import kabinet.gemini.generateEmbedding
import kabinet.model.LabeledEnum
import kabinet.utils.averageAndNormalize
import kabinet.utils.cosineDistances
import kabinet.utils.format
import kabinet.utils.normalize
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import ponder.embedlam.AppDb
import ponder.embedlam.AppClients
import ponder.embedlam.model.data.Block
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
    private val tagDao: FileDao<Tag> = AppDb.tagDao,
    private val ollama: OllamaClient = OllamaClient(),
    private val geminiClient: GeminiClient = AppClients.gemini
) : StateModel<BlockFeedState>() {
    override val state = ModelState(BlockFeedState())

    private var textEmbeddings: Map<ModelId, FloatArray> = emptyMap()
    private var embedJob: Job? = null
    private var allBlocks: List<Block> = emptyList()

    init {
        ioCollect(blockDao.items) { blocks ->
            allBlocks = blocks
            setStateFromMain { it.copy(blocks = filterAndSortBlocks()) }
        }
        ioCollect(tagDao.items) { tags ->
            setStateFromMain { it.copy(tags = tags.sortedByDescending { it.createdAt }) }
        }
    }

    private fun filterAndSortBlocks() = allBlocks.sortedByDescending { it.createdAt }.let { blocks ->
        stateNow.filterTags.takeIf { it.isNotEmpty() }?.let { filterTags ->
            blocks.filter { block -> block.tagIds.any { it in filterTags } }
                .sortedBy { block -> filterTags.indexOfFirst { it in block.tagIds } }
        } ?: blocks
    }

    fun setText(value: String) {
        setState { it.copy(text = value) }
        val text = value.takeIf { it.isNotEmpty() } ?: return
        embedJob?.cancel()
        embedJob = ioLaunch {
            delay(1000L)

            setStateFromMain { it.copy(isGenerating = true) }

            textEmbeddings = embedModels.map { model -> async { model.modelId to embed(model, text) } }
                .awaitAll()
                .toMap()
            geminiCache.clear()

            val blockDistances =
                findDistances(stateNow.blocks, { it.label }) { item, modelId -> item.embeddings[modelId] }
            val tagDistances =
                findDistances(stateNow.tags, { it.label }) { item, modelId -> item.avgEmbeddings[modelId] }

            setStateFromMain {
                it.copy(
                    blockDistances = blockDistances,
                    tagDistances = tagDistances,
                    isGenerating = false
                )
            }
        }
    }

    private suspend fun embed(model: EmbedModel, text: String): FloatArray {
        val embedding: FloatArray
        val millis = measureTimeMillis {
            when (model) {
                is GeminiEmbedModel -> {
                    embedding = geminiCache.embed(text) {
                        geminiClient.generateEmbedding(text)?.normalize()
                            ?: error("embedding not found")
                    }.take(model.dimensions).toFloatArray()
                }

                is OllamaEmbedModel -> {
                    embedding = ollama.embed(text, model.model)?.embeddings?.firstOrNull()?.normalize()
                        ?: error("embedding not found")
                }
            }
        }

        val perChar = (millis / text.length.toFloat()).format(1)
        println("${model.modelName} dims: ${embedding.size} millis: $millis perchar: $perChar")
        return embedding
    }

    private fun <T> findDistances(
        items: List<T>,
        provideLabel: (T) -> String,
        provideEmbeddings: (T, ModelId) -> FloatArray?
    ): Map<ModelId, List<SemanticDistance>?> {
        return textEmbeddings.entries.associate { (modelId, embedding) ->
            val pairs = items.mapNotNull {
                provideEmbeddings(it, modelId)?.let { e -> Pair(provideLabel(it), e) }
            }.takeIf { it.isNotEmpty() }
            if (pairs == null) {
                return@associate modelId to null
            }
            val labels = pairs.map { it.first }
            val embeddings = pairs.map { it.second }
            val cosineSimilarities = cosineDistances(embedding, embeddings)
            val minMaxScales = cosineSimilarities.minMaxScale()
            modelId to cosineSimilarities.mapIndexed { index, similarity ->
                SemanticDistance(labels[index], similarity, minMaxScales[index])
            }
        }
    }

    fun setLabel(value: String) = setState { it.copy(textLabel = value) }

    fun addBlock() {
        val text = stateNow.text.takeIf { text -> text.isNotEmpty() && allBlocks.none { it.text == text } } ?: return
        val label = stateNow.textLabel.takeIf { it.isNotEmpty() }?.incrementingLabel()
            ?: stateNow.applyTags.takeIf { it.isNotEmpty() }
                ?.joinToString(" ") { tagId -> stateNow.tags.first { it.tagId == tagId }.label }?.let { $$"$$it $x" }
                ?.incrementingLabel()
            ?: "${text.take(50)}..."
        ioLaunch {
            val blockId = BlockId.random()
            val now = Clock.System.now()
            blockDao.create(
                Block(
                    blockId = blockId,
                    tagIds = stateNow.applyTags,
                    text = text,
                    label = label,
                    embeddings = textEmbeddings,
                    createdAt = now,
                )
            )
            refreshTagEmbeddings(stateNow.applyTags)
            setStateFromMain {
                it.copy(
                    text = "",
                    textLabel = stateNow.textLabel.takeIf { l -> l.contains($$"$x") } ?: "")
            }
        }
    }

    private fun String.incrementingLabel(): String {
        if (!this.contains($$"$x")) return this
        var index = 1
        while (true) {
            return this.replace($$"$x", (index++).toString())
                .takeIf { label -> allBlocks.none { it.label.equals(label, true) } }
                ?: continue
        }
    }

    fun refreshEmbeddings() {
        ioLaunch {
            val blocks = allBlocks.map { block ->
                val embeddings = embedModels.associate { model ->
                    model.modelId to (block.embeddings[model.modelId] ?: embed(model, block.text))
                }
                block.copy(embeddings = embeddings)
            }
            blockDao.batchUpsert(blocks)
            refreshTagEmbeddings(stateNow.tags.map { it.tagId }.toSet())
        }
    }

    fun setValueType(value: ValueType) = setState { it.copy(valueType = value) }

    fun setTagLabel(value: String) = setState { it.copy(tagLabel = value) }

    fun createTag(maxColorIndex: Int) {
        val tagLabel = stateNow.tagLabel.takeIf { it.isNotEmpty() } ?: return
        val tagColorIndex = stateNow.tagColorIndex
        ioLaunch {
            val tagId = TagId.random()
            tagDao.create(
                Tag(
                    tagId = tagId,
                    label = tagLabel,
                    colorIndex = tagColorIndex,
                    avgEmbeddings = emptyMap(),
                    createdAt = Clock.System.now()
                )
            )

            setStateFromMain {
                it.copy(
                    tagColorIndex = tagColorIndex + 1 % maxColorIndex,
                    tagLabel = "",
                    applyTags = it.applyTags + tagId
                )
            }
        }
    }

    fun removeTag(tag: Tag) {
        ioLaunch {
            tagDao.delete(tag)
            val updatedBlocks = allBlocks.mapNotNull {
                if (it.tagIds.contains(tag.tagId)) it.copy(tagIds = it.tagIds - tag.tagId) else null
            }
            blockDao.batchUpsert(updatedBlocks)

            setStateFromMain { it.copy(applyTags = it.applyTags - tag.tagId) }
        }
    }

    fun addTag(tag: Tag, block: Block) {
        ioLaunch {
            blockDao.update(block.copy(tagIds = block.tagIds + tag.tagId))
            refreshTagEmbeddings(setOf(tag.tagId))
        }
    }

    fun removeTag(tag: Tag, block: Block) {
        ioLaunch {
            blockDao.update(block.copy(tagIds = block.tagIds - tag.tagId))
            refreshTagEmbeddings(setOf(tag.tagId))
        }
    }

    private fun refreshTagEmbeddings(tagIds: Set<TagId>) {
        ioLaunch {
            val tags = tagIds.map { tagId ->
                val avgEmbeddings = embedModels.mapNotNull { model ->
                    val embeddings = allBlocks.filter { it.tagIds.contains(tagId) }
                        .mapNotNull { it.embeddings[model.modelId] }
                        .takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    model.modelId to embeddings.averageAndNormalize()
                }.toMap()
                stateNow.tags.first { it.tagId == tagId }.copy(avgEmbeddings = avgEmbeddings)
            }
            tagDao.batchUpsert(tags)
            println("finished refresh of tag embeddings")
        }
    }

    fun addApplyTag(tag: Tag) = setState { it.copy(applyTags = it.applyTags + tag.tagId) }

    fun removeApplyTag(tag: Tag) = setState { it.copy(applyTags = it.applyTags - tag.tagId) }

    fun addFilterTag(tag: Tag) {
        setState { it.copy(filterTags = it.filterTags + tag.tagId) }
        setState { it.copy(blocks = filterAndSortBlocks()) }
    }

    fun removeFilterTag(tag: Tag) {
        setState { it.copy(filterTags = it.filterTags - tag.tagId) }
        setState { it.copy(blocks = filterAndSortBlocks()) }
    }
}

data class BlockFeedState(
    val blocks: List<Block> = emptyList(),
    val text: String = "",
    val textLabel: String = "",
    val blockDistances: Map<ModelId, List<SemanticDistance>?> = emptyMap(),
    val tagDistances: Map<ModelId, List<SemanticDistance>?> = emptyMap(),
    val valueType: ValueType = ValueType.Distance,
    val tags: List<Tag> = emptyList(),
    val applyTags: Set<TagId> = emptySet(),
    val tagLabel: String = "",
    val tagColorIndex: Int = (0..6).random(),
    val filterTags: Set<TagId> = emptySet(),
    val isGenerating: Boolean = false,
)

enum class ValueType(override val label: String) : LabeledEnum<ValueType> {
    Distance("Distance"),
    DistanceScaled("Distance Scaled"),
    Similarity("Similarity"),
    SimilarityScaled("Similarity Scaled")
}

val embedModels = listOf(
    OllamaEmbedModel(OllamaModel.NomicEmbed, "nomic"),
    OllamaEmbedModel(OllamaModel.MxbaiEmbed, "mxbai"),
    // OllamaEmbedModel(OllamaModel.GraniteEmbed, "gran"),
    OllamaEmbedModel(OllamaModel.MiniLm, "mini"),
    OllamaEmbedModel(OllamaModel.GemmaEmbed, "gemma"),
    // OllamaEmbedModel(OllamaModel.Qwen3Embed06B, "qwen3"),
    OllamaEmbedModel(OllamaModel.BgeM3, "bgem3"),
    // OllamaEmbedModel(OllamaModel.SnowflakeArctic, "snow"),
    GeminiEmbedModel(3072),
    GeminiEmbedModel(768)
)

sealed interface EmbedModel {
    val modelName: String
    val modelId get() = ModelId(modelName)
    val label: String
}

data class OllamaEmbedModel(
    val model: OllamaModel,
    override val label: String = model.apiLabel
) : EmbedModel {
    override val modelName get() = model.apiLabel
}

data class GeminiEmbedModel(
    val dimensions: Int,
) : EmbedModel {
    val endpointModel = "gemini-embed-001"
    override val modelName get() = "$endpointModel-$dimensions"
    override val label get() = "g-$dimensions"
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
    val label: String,
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

class EmbeddingCache {
    private val lock = Mutex()
    private val cache: MutableMap<String, FloatArray> = mutableMapOf()

    suspend fun embed(text: String, block: suspend () -> FloatArray) = lock.withLock {
        cache[text] ?: block().also { cache[text] = it }
    }

    fun clear() = cache.clear()
}

private val geminiCache = EmbeddingCache()