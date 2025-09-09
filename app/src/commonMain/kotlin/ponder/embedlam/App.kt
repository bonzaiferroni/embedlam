package ponder.embedlam

import androidx.compose.runtime.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import ponder.embedlam.model.data.Block
import ponder.embedlam.model.data.BlockEmbedding

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
}