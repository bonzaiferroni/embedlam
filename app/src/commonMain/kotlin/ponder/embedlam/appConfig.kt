package ponder.embedlam

import compose.icons.TablerIcons
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Home
import compose.icons.tablericons.Rocket
import compose.icons.tablericons.YinYang
import kotlinx.collections.immutable.persistentListOf
import ponder.embedlam.ui.BlockFeedScreen
import ponder.embedlam.ui.ExampleListScreen
import ponder.embedlam.ui.ExampleProfileScreen
import ponder.embedlam.ui.HelloScreen
import ponder.embedlam.ui.StartScreen
import pondui.ui.core.PondConfig
import pondui.ui.core.RouteConfig
import pondui.ui.nav.PortalDoor
import pondui.ui.nav.defaultScreen

val appConfig = PondConfig(
    name = "Embedlam",
    logo = TablerIcons.Heart,
    home = StartRoute,
    routes = persistentListOf(
        RouteConfig(StartRoute::matchRoute) { defaultScreen<StartRoute> { StartScreen() } },
        RouteConfig(HelloRoute::matchRoute) { defaultScreen<HelloRoute> { HelloScreen() } },
        RouteConfig(ExampleListRoute::matchRoute) { defaultScreen<ExampleListRoute> { ExampleListScreen() } },
        RouteConfig(ExampleProfileRoute::matchRoute) { defaultScreen<ExampleProfileRoute> { ExampleProfileScreen(it) } },
        RouteConfig(BlockFeedRoute::matchRoute) { defaultScreen<BlockFeedRoute> { BlockFeedScreen() } }
    ),
    doors = persistentListOf(
        PortalDoor(TablerIcons.YinYang, BlockFeedRoute),
    ),
)