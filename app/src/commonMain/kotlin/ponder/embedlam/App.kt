package ponder.embedlam

import androidx.compose.runtime.*
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString
import kabinet.gemini.GeminiClient
import kabinet.utils.Environment
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.ui.tooling.preview.Preview
import ponder.embedlam.model.data.Block
import ponder.embedlam.model.data.BlockEmbedding
import ponder.embedlam.model.data.Tag

import pondui.ui.core.PondApp
import pondui.ui.nav.NavRoute
import pondui.ui.theme.ProvideTheme
import pondui.utils.FileDao

@Composable
@Preview
fun App(
    changeRoute: (NavRoute) -> Unit,
    exitApp: (() -> Unit)?,
) {
    ProvideTheme {
        PondApp(
            config = appConfig,
            changeRoute = changeRoute,
            exitApp = exitApp
        )
    }
}

object AppDb {
    val blockDao = FileDao(Block::class) { it.blockId.value }
    val blockEmbeddingDao = FileDao(BlockEmbedding::class) { it.blockEmbeddingId.value }
    val tagDao = FileDao(Tag::class) { it.tagId.value }
}

object AppClients {
    private val environment by lazy { runBlocking { PlatformFile("../.env").readString().let { Environment.fromText(it) } } }

    val gemini by lazy { GeminiClient(environment.read("GEMINI_TOKEN")) { level, msg -> println(msg)} }
}
