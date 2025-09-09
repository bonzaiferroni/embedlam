package ponder.embedlam.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ponder.embedlam.ExampleProfileRoute
import ponder.embedlam.io.ExampleStore
import ponder.embedlam.model.data.Example
import pondui.ui.core.ModelState
import pondui.ui.core.StateModel

class ExampleProfileModel(
    route: ExampleProfileRoute,
    private val store: ExampleStore = ExampleStore()
): StateModel<ExampleProfileState>() {

    override val state = ModelState(ExampleProfileState())

    init {
        viewModelScope.launch {
            val example = store.readExample(route.exampleId)
            setState { it.copy(example = example, symtrix = example.label) }
        }
    }

    fun toggleEdit() {
        setState { it.copy(isEditing = !it.isEditing) }
    }

    fun setSymtrix(value: String) {
        setState { it.copy(symtrix = value) }
    }

    fun finalizeEdit() {
        val example = stateNow.example?.copy(label = stateNow.symtrix) ?: return
        viewModelScope.launch {
            val isSuccess = store.updateExample(example)
            if (isSuccess) {
                setState { it.copy(example = example) }
                toggleEdit()
            }
        }
    }
}

data class ExampleProfileState(
    val example: Example? = null,
    val symtrix: String = "",
    val isEditing: Boolean = false
)