package ponder.embedlam.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import ponder.embedlam.ExampleProfileRoute
import pondui.ui.controls.Button
import pondui.ui.controls.RouteButton
import pondui.ui.controls.Row
import pondui.ui.controls.Scaffold
import pondui.ui.controls.Text
import pondui.ui.controls.TextField
import pondui.ui.theme.Pond

@Composable
fun ExampleListScreen(
    viewModel: ExampleListModel = viewModel { ExampleListModel() }
) {
    val state by viewModel.stateFlow.collectAsState()
    Scaffold {
        Row(1) {
            TextField(state.newSymtrix, onTextChanged = viewModel::setSymtrix)
            Button("Add", onClick = viewModel::createNewItem)
        }
        LazyColumn {
            items(state.examples) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(it.label)
                    Row(1) {
                        Button("Delete", background = Pond.colors.negation, onClick = { viewModel.deleteItem(it) })
                        RouteButton("View") { ExampleProfileRoute(it.id) }
                    }
                }
            }
        }
    }
}