package ponder.embedlam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import embedlam.app.generated.resources.Res
import embedlam.app.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource
import ponder.embedlam.Greeting
import ponder.embedlam.HelloRoute
import pondui.ui.controls.Button
import pondui.ui.controls.Column
import pondui.ui.controls.RouteButton
import pondui.ui.controls.Scaffold
import pondui.ui.controls.Text
import pondui.ui.theme.Pond

@Composable
fun StartScreen() {
    Scaffold {
        var showContent by remember { mutableStateOf(false) }
        Column(
            gap = 1,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RouteButton("Go to Hello") { HelloRoute }
            Button(onClick = { showContent = !showContent }) {
                Text("Show platform")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}