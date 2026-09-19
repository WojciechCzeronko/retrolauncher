package com.openlauncher.app.ui.screen.aw11

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.openlauncher.app.ui.components.Aw11ControlPanel
import com.openlauncher.app.ui.theme.Aw11Border
import com.openlauncher.app.model.NavDestination

@Composable
internal fun Aw11Shell(
    hasGps: Boolean,
    mediaAvailable: Boolean,
    onNav: () -> Unit,
    onMedia: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    currentDest: NavDestination,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.13f)
                    .fillMaxHeight()
                    .border(
                        1.dp,
                        Aw11Border.copy(
                            alpha = 0.45f
                        )
                    )
            ) {
                Aw11ControlPanel(
                    hasGps = hasGps,
                    mediaAvailable = mediaAvailable,
                    onNav = onNav,
                    onMedia = onMedia,
                    onApps = onApps,
                    currentDest = currentDest,
                    onSettings = onSettings

                )
            }

            Spacer(
                Modifier.width(3.dp)
            )

            Box(
                modifier = Modifier
                    .weight(0.87f)
                    .fillMaxHeight(),
                content = content
            )
        }

        Aw11DisplayOverlay(
            modifier = Modifier
                .matchParentSize()
                .zIndex(100f)
        )
    }
}