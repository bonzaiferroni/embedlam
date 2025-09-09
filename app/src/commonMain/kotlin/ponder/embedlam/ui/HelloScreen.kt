package ponder.embedlam.ui

import androidx.compose.runtime.Composable
import ponder.embedlam.StartRoute
import pondui.ui.controls.RouteButton
import pondui.ui.controls.Scaffold
import pondui.ui.controls.Text

@Composable
fun HelloScreen() {
    Scaffold {
        Text("Hello world!")
        RouteButton("Go to start") { StartRoute }
    }
}