package ponder.embedlam.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import kabinet.utils.format
import ponder.embedlam.model.data.ModelId
import pondui.ui.behavior.selected
import pondui.ui.charts.AxisConfig
import pondui.ui.charts.AxisSide
import pondui.ui.charts.LineChart
import pondui.ui.charts.LineChartArray
import pondui.ui.charts.LineChartConfig
import pondui.ui.charts.LineChartWithLegend
import pondui.ui.charts.SideAxisAutoConfig
import pondui.ui.controls.Button
import pondui.ui.controls.DropMenu
import pondui.ui.controls.Expando
import pondui.ui.controls.LazyColumn
import pondui.ui.controls.Row
import pondui.ui.controls.Scaffold
import pondui.ui.controls.Section
import pondui.ui.controls.Tab
import pondui.ui.controls.Tabs
import pondui.ui.controls.Text
import pondui.ui.controls.TextField
import pondui.ui.theme.Pond
import pondui.utils.lighten

@Composable
fun BlockFeedScreen(
    viewModel: BlockFeedModel = viewModel { BlockFeedModel() }
) {
    val state by viewModel.stateFlow.collectAsState()
    val colors = Pond.colors
    val localColors = Pond.localColors
    Scaffold {
        Row(1) {
            TextField(state.label, onTextChanged = viewModel::setLabel, modifier = Modifier.weight(1f))
            Button(TablerIcons.Plus, onClick = viewModel::addBlock)
        }
        TextField(state.text, onTextChanged = viewModel::setText, modifier = Modifier.fillMaxWidth().height(200.dp))
        Row(1) {
            Button("Recalculate", onClick = viewModel::refreshEmbeddings)
            DropMenu(state.valueType, onSelect = viewModel::setValueType)
        }

        Tabs {
            Tab("Scores") {
                Row(1) {
                    Expando()
                    embedModels.forEach {
                        Text(it.modelName.take(3), modifier = Modifier.width(colWidth))
                    }
                }
                LazyColumn(1) {
                    itemsIndexed(state.blocks) { index, block ->
                        Row(1) {
                            Text(block.label, modifier = Modifier.weight(1f))
                            embedModels.forEach {
                                val distances = state.distances[ModelId(it.modelName)]
                                val distance = distances?.getOrNull(index)
                                val text = when (distance) {
                                    null -> "-"
                                    else -> distance.getValue(state.valueType).format(2)
                                }
                                val color = when {
                                    distance?.distanceScaled == 1f -> Pond.colors.negation.lighten(.4f)
                                    else -> Pond.localColors.content
                                }
                                Text(
                                    text = text,
                                    color = color,
                                    modifier = Modifier.width(colWidth)
                                        .selected(distance?.distanceScaled == 0f)
                                )
                            }
                        }
                    }
                }
            }
            Tab("Charts") {
                val config = remember(state.distances, state.valueType) {
                    LineChartConfig(
                        arrays = state.distances.mapNotNull { kvp -> kvp.value?.let { Pair(kvp.key, it) } }
                            .mapIndexed { index, (modelId, distances) ->
                                LineChartArray(
                                    values = distances.mapIndexed { index, distance -> Pair(index, distance) },
                                    color = colors.swatches[index],
                                    label = modelId.value,
                                    axis = SideAxisAutoConfig(
                                        tickCount = 5,
                                        side = AxisSide.Right
                                    ),
                                ) { (_, distance) -> distance.getValue(state.valueType).toDouble() }
                            },
                        contentColor = localColors.content,
                    )
                }
                LineChartWithLegend(config) { pair ->
                    val (text, color) = pair?.let { (index, distance) ->
                        "${state.blocks[index].label}: ${distance.getValue(state.valueType).format(2) }" to colors.swatches[index]
                    } ?: ("-" to localColors.content)
                    Text(text, color = color)
                }
            }
            Tab("Tags") {

            }
        }
    }
}

val colWidth = 40.dp