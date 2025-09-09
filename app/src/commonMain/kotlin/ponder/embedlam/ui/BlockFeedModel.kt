package ponder.embedlam.ui

import kabinet.clients.OllamaClient
import kabinet.clients.OllamaModel
import kabinet.utils.cosineDistances
import kabinet.utils.format
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import ponder.embedlam.AppDb
import ponder.embedlam.model.data.Block
import ponder.embedlam.model.data.BlockEmbedding
import ponder.embedlam.model.data.BlockEmbeddingId
import ponder.embedlam.model.data.BlockId
import ponder.embedlam.model.data.ModelId
import pondui.ui.core.ModelState
import pondui.ui.core.StateModel
import pondui.utils.FileDao
import kotlin.system.measureTimeMillis

class BlockFeedModel(
    private val blockDao: FileDao<Block> = AppDb.blockDao,
    private val blockEmbeddingDao: FileDao<BlockEmbedding> = AppDb.blockEmbeddingDao,
    private val ollama: OllamaClient = OllamaClient()
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
    }

    fun setText(value: String) {
        setState { it.copy(text = value) }
        val text = value.takeIf { it.isNotEmpty() } ?: return
        embedJob?.cancel()
        embedJob = ioLaunch {
            delay(1000L)

            textEmbeddings = coroutineScope {
                embedModels.map { m -> async { ModelId(m.apiLabel) to embed(m, text) } }
                    .awaitAll()
                    .toMap()
            }

            val distances = textEmbeddings.entries.associate { (modelId, embedding) ->
                val blockEmbeddings = stateNow.blocks.takeIf { it.size == feedEmbeddings[modelId]?.size }
                    ?.mapNotNull { feedEmbeddings[modelId]?.get(it.blockId)?.embedding }
                modelId to blockEmbeddings?.let { cosineDistances(embedding, it) }
            }
            setStateFromMain { it.copy(distances = distances) }
        }
    }

    private suspend fun embed(model: OllamaModel, text: String): FloatArray {
        val embedding: FloatArray
        val millis = measureTimeMillis {
            embedding = ollama.embed(text, model)?.embeddings?.firstOrNull() ?: error("embedding not found")
        }
        println(
            "${model.apiLabel} dims: ${embedding.size} millis: $millis perchar: ${
                (millis / text.length.toFloat()).format(
                    1
                )
            }"
        )
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
                    val modelId = ModelId(model.apiLabel)
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
}

data class BlockFeedState(
    val blocks: List<Block> = emptyList(),
    val text: String = "",
    val label: String = "",
    val distances: Map<ModelId, List<Float>?> = emptyMap()
)

val embedModels = listOf(OllamaModel.NomicEmbed, OllamaModel.MxbaiEmbed, OllamaModel.GemmaEmbed)