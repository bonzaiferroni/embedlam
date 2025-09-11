package ponder.embedlam.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import kabinet.utils.format
import kotlinx.collections.immutable.toImmutableList
import ponder.embedlam.model.data.ModelId
import pondui.ui.behavior.selected
import pondui.ui.charts.AxisSide
import pondui.ui.charts.LineChartArray
import pondui.ui.charts.LineChartConfig
import pondui.ui.charts.LineChartWithLegend
import pondui.ui.charts.SideAxisAutoConfig
import pondui.ui.controls.Button
import pondui.ui.controls.Column
import pondui.ui.controls.DropMenu
import pondui.ui.controls.Expando
import pondui.ui.controls.FlowRow
import pondui.ui.controls.Label
import pondui.ui.controls.LazyColumn
import pondui.ui.controls.MoreMenu
import pondui.ui.controls.MoreMenuItem
import pondui.ui.controls.Row
import pondui.ui.controls.Scaffold
import pondui.ui.controls.Section
import pondui.ui.controls.Tab
import pondui.ui.controls.Tabs
import pondui.ui.controls.Text
import pondui.ui.controls.TextField
import pondui.ui.controls.actionable
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
            TextField(state.textLabel, onTextChanged = viewModel::setLabel, modifier = Modifier.weight(1f))
            Button(TablerIcons.Plus, onClick = viewModel::addBlock, isEnabled = !state.isGenerating)
        }
        TextField(state.text, onTextChanged = viewModel::setText, modifier = Modifier.fillMaxWidth().height(200.dp))
        Row(1) {
            Button("Recalculate", onClick = viewModel::refreshEmbeddings)
            DropMenu(state.valueType, onSelect = viewModel::setValueType)

        }
        Row(1) {
            Label("Apply tags")
            FlowRow(1) {
                state.applyTags.forEach { tagId ->
                    val tag = state.tags.first { it.tagId == tagId }
                    val color = colors.swatches[tag.colorIndex]
                    Text(tag.label, color = color, modifier = Modifier.actionable { viewModel.removeApplyTag(tag) })
                }
            }
            MoreMenu {
                state.tags.forEach { tag ->
                    if (state.applyTags.contains(tag.tagId)) return@forEach
                    val color = colors.swatches[tag.colorIndex]
                    MoreMenuItem(tag.label, color = color) { viewModel.addApplyTag(tag) }
                }
            }
        }
        Row(1) {
            Label("Filter tags")
            FlowRow(1) {
                state.filterTags.forEach { tagId ->
                    val tag = state.tags.first { it.tagId == tagId }
                    val color = colors.swatches[tag.colorIndex]
                    Text(tag.label, color = color, modifier = Modifier.actionable { viewModel.removeFilterTag(tag) })
                }
            }
            MoreMenu {
                state.tags.forEach { tag ->
                    if (state.filterTags.contains(tag.tagId)) return@forEach
                    val color = colors.swatches[tag.colorIndex]
                    MoreMenuItem(tag.label, color = color) { viewModel.addFilterTag(tag) }
                }
            }
        }

        Tabs {
            Tab("Scores") {
                Row(1, modifier = Modifier.padding(Pond.ruler.unitSpacing)) {
                    // Expando()
                    embedModels.forEach {
                        Text(it.label, style= Pond.typo.body.copy(fontSize = 8.sp), modifier = Modifier.width(colWidth))
                    }
                }
                LazyColumn(1) {
                    itemsIndexed(state.blocks) { index, block ->
                        Section {
                            Row(1) {
                                Column(1, modifier = Modifier.weight(1f)) {
                                    Text(block.label)
                                    Row(1) {
                                        block.tagIds.forEach { tagId ->
                                            val tag = state.tags.first { it.tagId == tagId }
                                            val color = colors.swatches[tag.colorIndex]
                                            Text(tag.label, color = color, modifier = Modifier.actionable { viewModel.removeTag(tag, block) })
                                        }
                                    }
                                    Row(1) {
                                        embedModels.forEach { model ->
                                            val distances = state.textDistances[model.modelId]
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
                                                style = Pond.typo.body.copy(fontSize = 12.sp),
                                                modifier = Modifier.width(colWidth)
                                                    .selected(distance?.distanceScaled == 0f)
                                            )
                                        }
                                    }
                                }
                                MoreMenu() {
                                    state.tags.filter { !block.tagIds.contains(it.tagId) }.forEach { tag ->
                                        val color = colors.swatches[tag.colorIndex]
                                        MoreMenuItem(label = tag.label, color = color) { }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Tab("Charts") {
                val config = remember(state.textDistances, state.valueType) {
                    LineChartConfig(
                        arrays = embedModels.mapIndexedNotNull { index, model ->
                            state.textDistances[model.modelId]?.let { distances ->
                                LineChartArray(
                                    values = distances.mapIndexed { index, distance -> Pair(index, distance) },
                                    color = colors.swatches[index],
                                    label = model.label,
                                    axis = SideAxisAutoConfig(
                                        tickCount = 5,
                                        side = AxisSide.Right
                                    ),
                                ) { (_, distance) -> distance.getValue(state.valueType).toDouble() }
                            }
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
                Row(1) {
                    TextField(state.tagLabel, onTextChanged = viewModel::setTagLabel)
                    Button(TablerIcons.Plus) { viewModel.createTag(colors.swatches.size) }
                }
                state.tags.forEach { tag ->
                    val color = colors.swatches[tag.colorIndex]
                    Text(text = tag.label, color = color)
                }
            }
        }
    }
}

val colWidth = 32.dp