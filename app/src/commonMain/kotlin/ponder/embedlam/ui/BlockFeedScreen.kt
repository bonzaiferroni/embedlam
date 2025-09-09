package ponder.embedlam.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import pondui.ui.controls.Button
import pondui.ui.controls.LazyColumn
import pondui.ui.controls.Row
import pondui.ui.controls.Scaffold
import pondui.ui.controls.Text
import pondui.ui.controls.TextField

@Composable
fun BlockFeedScreen(
    viewModel: BlockFeedModel = viewModel { BlockFeedModel() }
) {
    val state by viewModel.stateFlow.collectAsState()
    Scaffold {
        Row(1) {
            TextField(state.label, onTextChanged = viewModel::setLabel, modifier = Modifier.weight(1f))
            Button(TablerIcons.Plus, onClick = viewModel::addBlock)
        }
        TextField(state.text, onTextChanged = viewModel::setText, modifier = Modifier.fillMaxWidth())

        LazyColumn(1) {
            items(state.blocks) { block ->
                Text(block.label)
            }
        }
    }
}
