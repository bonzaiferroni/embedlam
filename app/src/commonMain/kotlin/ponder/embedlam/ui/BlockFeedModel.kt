package ponder.embedlam.ui

import kabinet.clients.OllamaClient
import kabinet.utils.cosineDistances
import kabinet.utils.format
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import ponder.embedlam.AppDb
import ponder.embedlam.model.data.Block
import ponder.embedlam.model.data.BlockId
import pondui.ui.core.ModelState
import pondui.ui.core.StateModel
import pondui.utils.FileDao
import kotlin.system.measureTimeMillis

class BlockFeedModel(
    private val blockDao: FileDao<Block> = AppDb.blockDao,
    private val ollama: OllamaClient = OllamaClient()
) : StateModel<BlockFeedState>() {
    override val state = ModelState(BlockFeedState())

    private var embedding: FloatArray? = null
    private var embedJob: Job? = null

    init {
        ioCollect(blockDao.items) { blocks ->
            setStateFromMain { it.copy(blocks = blocks) }
        }
    }

    fun setText(value: String) {
        setState { it.copy(text = value) }
        embedJob?.cancel()
        embedJob = ioLaunch {
            delay(1000L)
            var millis = measureTimeMillis {
                embedding = ollama.embed(value)?.embeddings?.firstOrNull()
            }
            println("millis: $millis perchar: ${(millis / value.length.toFloat()).format(1)}")
            var distances: List<Float>
            millis = measureTimeMillis {
                distances = embedding?.let { cosineDistances(it, stateNow.blocks.map { block -> block.embedding }) }
                    ?: emptyList()
            }
            println("cosineDistance millis: $millis")
            setStateFromMain { it.copy(distances = distances) }
        }
    }

    fun setLabel(value: String) = setState { it.copy(label = value) }

    fun addBlock() {
        val text = stateNow.text.takeIf { it.isNotEmpty() } ?: return
        val embedding = embedding ?: return
        val label = stateNow.label.takeIf { it.isNotEmpty() } ?: "${text.take(50)}..."
        ioLaunch {
            blockDao.create(
                Block(
                    blockId = BlockId.random(),
                    text = text,
                    embedding = embedding,
                    label = label,
                    createdAt = Clock.System.now()
                )
            )
            setStateFromMain { it.copy(text = "", label = "") }
        }
    }
}

data class BlockFeedState(
    val blocks: List<Block> = emptyList(),
    val text: String = "",
    val label: String = "",
    val distances: List<Float> = emptyList()
)
