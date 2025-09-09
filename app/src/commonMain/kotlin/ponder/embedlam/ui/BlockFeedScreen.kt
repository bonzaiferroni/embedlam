package ponder.embedlam.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import kabinet.utils.format
import ponder.embedlam.model.data.ModelId
import pondui.ui.controls.Button
import pondui.ui.controls.Expando
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
        TextField(state.text, onTextChanged = viewModel::setText, modifier = Modifier.fillMaxWidth().height(200.dp))
        Row(1) {
            Button("Recalculate", onClick = viewModel::refreshEmbeddings)
            Expando()
            embedModels.forEach {
                Text(it.apiLabel.take(3), modifier = Modifier.width(colWidth))
            }
        }

        LazyColumn(1) {
            itemsIndexed(state.blocks) { index, block ->
                Row(1) {
                    Text(block.label, modifier = Modifier.weight(1f))
                    embedModels.forEach {
                        val distances = state.distances[ModelId(it.apiLabel)]
                        val distance = distances?.getOrNull(index)
                        Text(distance?.format(2) ?: "-", modifier = Modifier.width(colWidth))
                    }
                }
            }
        }
    }
}

val colWidth = 40.dp