package com.openlauncher.app.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.ui.theme.Aw11Background
import com.openlauncher.app.ui.theme.Aw11Primary
import com.openlauncher.app.ui.theme.Aw11Secondary
import com.openlauncher.app.ui.theme.Aw11Warning

@Composable
fun Aw11DemoControls(
    isDemoMode: Boolean,
    isPaused: Boolean,
    speedMultiplier: Double,
    onStart: () -> Unit,
    onMultiplierChange: (Double) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isDemoMode) {
        DemoButton(
            text = "DEMO",
            selected = false,
            onClick = onStart,
            modifier = modifier
        )

        return
    }

    Column(
        modifier = modifier
            .background(
                Aw11Background.copy(
                    alpha = 0.92f
                )
            )
            .border(
                width = 1.dp,
                color = Aw11Primary.copy(
                    alpha = 0.85f
                )
            )
            .padding(6.dp),
        verticalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "DEMO",
                color = Aw11Primary,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )

            Text(
                text =
                    "${speedMultiplier.toInt()}X",
                color = Aw11Secondary,
                fontSize = 11.sp
            )
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                1.0,
                4.0,
                8.0
            ).forEach { multiplier ->

                DemoButton(
                    text =
                        "${multiplier.toInt()}X",
                    selected =
                        speedMultiplier ==
                                multiplier,
                    onClick = {
                        onMultiplierChange(
                            multiplier
                        )
                    }
                )
            }
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(4.dp)
        ) {
            DemoButton(
                text =
                    if (isPaused) {
                        "RESUME"
                    } else {
                        "PAUSE"
                    },
                selected = false,
                onClick = {
                    if (isPaused) {
                        onResume()
                    } else {
                        onPause()
                    }
                }
            )

            DemoButton(
                text = "STOP",
                selected = false,
                warning = true,
                onClick = onStop
            )
        }
    }
}

@Composable
private fun DemoButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    val color =
        if (warning) {
            Aw11Warning
        } else {
            Aw11Primary
        }

    Text(
        text = text,
        color =
            if (selected) {
                Aw11Background
            } else {
                color
            },
        fontSize = 11.sp,
        modifier = modifier
            .background(
                if (selected) {
                    color
                } else {
                    Aw11Background.copy(
                        alpha = 0.92f
                    )
                }
            )
            .border(
                width = 1.dp,
                color = color
            )
            .clickable(
                onClick = onClick
            )
            .padding(
                horizontal = 8.dp,
                vertical = 5.dp
            )
    )
}