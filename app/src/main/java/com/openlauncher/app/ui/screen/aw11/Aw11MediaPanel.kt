package com.openlauncher.app.ui.screen.aw11

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.service.MediaListenerService
import com.openlauncher.app.ui.theme.Aw11Border
import com.openlauncher.app.ui.theme.Aw11Dim
import com.openlauncher.app.ui.theme.Aw11Primary
import com.openlauncher.app.ui.theme.Aw11Secondary
import com.openlauncher.app.util.CoverArtHelper.createRetroAlbumArt
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun Aw11MediaPanel(
    nowPlaying: NowPlayingState?,
    showTestValues: Boolean,
    selfTestProgress: Float,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediaServiceConnected by
    MediaListenerService.isConnected.collectAsState()

    var mediaLinkDots by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(
        mediaServiceConnected,
        nowPlaying,
        showTestValues
    ) {
        if (
            !showTestValues &&
            !mediaServiceConnected &&
            nowPlaying == null
        ) {
            while (true) {
                mediaLinkDots =
                    (mediaLinkDots + 1) % 4

                delay(350)
            }
        } else {
            mediaLinkDots = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Aw11Border.copy(
                    alpha = 0.45f
                )
            )
    ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = mediaSourceName(nowPlaying),
                    color = Aw11Primary,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )

                Spacer(Modifier.weight(1f))

                when {
                    showTestValues -> {
                        Aw11MediaSelfTest(
                            progress = selfTestProgress
                        )
                    }

                    nowPlaying != null -> {
                        val controller = nowPlaying.controller
                        val playbackState = controller?.playbackState
                        val canSkipPrevious =
                            playbackState != null &&
                                    (playbackState.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L

                        val canSkipNext =
                            playbackState != null &&
                                    (playbackState.actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
                        val actions = playbackState?.actions ?: 0L

                        val canPlay =
                            (actions and PlaybackState.ACTION_PLAY) != 0L ||
                                    (actions and PlaybackState.ACTION_PLAY_PAUSE) != 0L

                        val canPause =
                            (actions and PlaybackState.ACTION_PAUSE) != 0L ||
                                    (actions and PlaybackState.ACTION_PLAY_PAUSE) != 0L

                        val canPlayPause =
                            if (nowPlaying.isPlaying) canPause else canPlay
                        val canSeek =
                            playbackState != null &&
                                    (playbackState.actions and PlaybackState.ACTION_SEEK_TO) != 0L
                        val progressColor =
                            if (canSeek) Aw11Primary
                            else Aw11Secondary
                        val durationMs =
                            controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                                ?: 0L

                        var currentPositionMs by remember(
                            nowPlaying.title,
                            nowPlaying.artist,
                            nowPlaying.isPlaying
                        ) {
                            mutableLongStateOf(playbackState?.position ?: 0L)
                        }

                        LaunchedEffect(
                            nowPlaying.title,
                            nowPlaying.artist,
                            nowPlaying.isPlaying
                        ) {
                            while (true) {
                                val state = controller?.playbackState

                                currentPositionMs = if (
                                    state != null &&
                                    state.state == PlaybackState.STATE_PLAYING
                                ) {
                                    val elapsedSinceUpdate =
                                        SystemClock.elapsedRealtime() - state.lastPositionUpdateTime

                                    (
                                            state.position +
                                                    elapsedSinceUpdate * state.playbackSpeed
                                            ).toLong()
                                        .coerceAtLeast(0L)
                                        .coerceAtMost(durationMs)
                                } else {
                                    state?.position ?: 0L
                                }

                                delay(500)
                            }
                        }
                        val progress = if (durationMs > 0L) {
                            (currentPositionMs.toFloat() / durationMs.toFloat())
                                .coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        fun formatTime(ms: Long): String {
                            val totalSeconds = ms / 1000
                            val minutes = totalSeconds / 60
                            val seconds = totalSeconds % 60

                            return "%02d:%02d".format(minutes, seconds)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                //artist
                                Text(
                                    text = nowPlaying.artist.uppercase(),
                                    color = Aw11Secondary,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.25.sp,
                                    maxLines = 1
                                )

                                //title
                                Text(
                                    text = nowPlaying.title.uppercase(),
                                    color = Aw11Primary,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.sp,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(1.dp))


                            }

                            val retroAlbumArt = remember(nowPlaying.albumArt) {
                                nowPlaying.albumArt?.let { createRetroAlbumArt(it) }
                            }
                            if (retroAlbumArt != null) {
                                Image(
                                    bitmap = retroAlbumArt.asImageBitmap(),
                                    contentDescription = "Album art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .border(
                                            width = 1.dp,
                                            color = Aw11Border.copy(alpha = 0.45f)
                                        )
                                        .clickable {
                                            onOpenMedia()
                                        }
                                )
                            }
                        }
                        Spacer(Modifier.height(1.dp))

                        //Whole progress bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            //elapsed time
                            Text(
                                text = if (showTestValues) {
                                    "88:88"
                                } else {
                                    formatTime(currentPositionMs)
                                },
                                color = Aw11Secondary,
                                fontSize = 9.sp
                            )

                            Spacer(Modifier.width(8.dp))

                            //bar
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .pointerInput(durationMs, canSeek) {
                                        detectTapGestures { offset ->
                                            if (canSeek && durationMs > 0L && size.width > 0) {
                                                val progress =
                                                    (offset.x / size.width.toFloat())
                                                        .coerceIn(0f, 1f)

                                                val newPosition =
                                                    (durationMs * progress).toLong()

                                                controller
                                                    ?.transportControls
                                                    ?.seekTo(newPosition)
                                            }
                                        }
                                    },
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(24) { index ->
                                    val activeSegments =
                                        if (showTestValues) {
                                            (selfTestProgress * 24f)
                                                .roundToInt()
                                                .coerceIn(0, 24)
                                        } else {
                                            (progress * 24f)
                                                .roundToInt()
                                                .coerceIn(0, 24)
                                        }

                                    val active = index < activeSegments

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                            .background(
                                                if (active) {
                                                    if (showTestValues) {
                                                        Aw11Primary
                                                    } else {
                                                        progressColor
                                                    }
                                                } else {
                                                    Aw11Dim.copy(alpha = 0.35f)
                                                }
                                            )
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            //total time
                            Text(
                                text = if (showTestValues) {
                                    "88:88"
                                } else {
                                    formatTime(durationMs)
                                },
                                color = Aw11Secondary,
                                fontSize = 9.sp
                            )
                        }
                        Spacer(Modifier.height(2.dp))

                        //equalizer
                        RetroEqualizer(
                            isPlaying = nowPlaying?.isPlaying == true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            // Previous
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (canSkipPrevious) {
                                            Aw11Border.copy(alpha = 0.65f)
                                        } else {
                                            Aw11Dim.copy(alpha = 0.35f)
                                        }
                                    )
                                    .clickable(
                                        enabled = canSkipPrevious
                                    ) {
                                        onPrev()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⏮",
                                    color = if (canSkipPrevious) {
                                        Aw11Secondary
                                    } else {
                                        Aw11Dim
                                    },
                                    fontSize = 22.sp
                                )
                            }

                            // Play / Pause
                            Box(
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(52.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (canPlayPause) {
                                            Aw11Primary
                                        } else {
                                            Aw11Dim.copy(alpha = 0.35f)
                                        }
                                    )
                                    .clickable(
                                        enabled = canPlayPause
                                    ) {
                                        onPlayPause()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (nowPlaying.isPlaying) {
                                        "Ⅱ"
                                    } else {
                                        "▶"
                                    },
                                    color = if (canPlayPause) {
                                        Aw11Primary
                                    } else {
                                        Aw11Dim
                                    },
                                    fontSize = 25.sp
                                )
                            }

                            // Next
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (canSkipNext) {
                                            Aw11Border.copy(alpha = 0.65f)
                                        } else {
                                            Aw11Dim.copy(alpha = 0.35f)
                                        }
                                    )
                                    .clickable(
                                        enabled = canSkipNext
                                    ) {
                                        onNext()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⏭",
                                    color = if (canSkipNext) {
                                        Aw11Secondary
                                    } else {
                                        Aw11Dim
                                    },
                                    fontSize = 22.sp
                                )
                            }
                        }


                    }

                    !mediaServiceConnected -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MEDIA LINK",
                                color = Aw11Secondary,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )

                            Spacer(Modifier.width(2.dp))

                            Row(
                                modifier = Modifier.width(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                repeat(3) { index ->
                                    Text(
                                        text = ".",
                                        color = if (index < mediaLinkDots) {
                                            Aw11Secondary
                                        } else {
                                            Color.Transparent
                                        },
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = "NO MEDIA",
                            color = Aw11Dim,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }

    }
}


@Composable
private fun RetroEqualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(
        label = "retro_equalizer"
    )

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 900,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "equalizer_phase"
    )

    val levels = if (isPlaying) {
        listOf(
            2 + ((phase * 5).toInt() % 5),
            4 + ((phase * 7).toInt() % 3),
            3 + ((phase * 9).toInt() % 4),
            5 + ((phase * 6).toInt() % 2),
            2 + ((phase * 8).toInt() % 5),
            4 + ((phase * 11).toInt() % 3),
            3 + ((phase * 5).toInt() % 4),
            5 + ((phase * 9).toInt() % 2),
            3 + ((phase * 7).toInt() % 4),
            2 + ((phase * 10).toInt() % 5),
            4 + ((phase * 6).toInt() % 3),
            3 + ((phase * 8).toInt() % 4)
        )
    } else {
        List(12) { 1 }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        levels.forEach { level ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom
            ) {
                repeat(7) { segment ->
                    val active = segment < level

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 1.dp)
                            .background(
                                if (active) {
                                    Aw11Primary.copy(alpha = 0.85f)
                                } else {
                                    Aw11Dim.copy(alpha = 0.25f)
                                }
                            )
                    )
                }
            }
        }
    }
}

private fun mediaSourceName(nowPlaying: NowPlayingState?): String {
    val packageName = nowPlaying?.controller?.packageName ?: return "MUSIC"

    return when (packageName) {
        "com.spotify.music" -> "SPOTIFY"
        "com.google.android.apps.youtube.music" -> "YOUTUBE MUSIC"
        else -> packageName
            .substringAfterLast('.')
            .replace('_', ' ')
            .uppercase()
    }
}



@Composable
private fun Aw11MediaSelfTest(
    progress: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "████████████",
                    color = Aw11Secondary,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )

                Text(
                    text = "████████████████",
                    color = Aw11Primary,
                    fontSize = 15.sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 1
                )

                Spacer(Modifier.height(1.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⏮",
                            color = Aw11Primary,
                            fontSize = 17.sp
                        )
                    }

                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ⅱ",
                            color = Aw11Primary,
                            fontSize = 18.sp,
                            modifier = Modifier.offset(y = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⏭",
                            color = Aw11Primary,
                            fontSize = 17.sp
                        )
                    }
                }
            }

            // Placeholder for album art during the display test
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(
                        width = 1.dp,
                        color = Aw11Primary
                    )
                    .background(
                        Aw11Dim.copy(alpha = 0.35f)
                    )
            )
        }

        Spacer(Modifier.height(1.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "88:88",
                color = Aw11Secondary,
                fontSize = 9.sp
            )

            Spacer(Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val activeSegments =
                    (progress * 24f)
                        .roundToInt()
                        .coerceIn(0, 24)

                repeat(24) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .background(
                                if (index < activeSegments) {
                                    Aw11Primary
                                } else {
                                    Aw11Dim.copy(alpha = 0.35f)
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = "88:88",
                color = Aw11Secondary,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(2.dp))

        // Equalizer self-test will be added next
        // Equalizer display self-test
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val rows = 7

            val activeRows =
                (progress * rows)
                    .roundToInt()
                    .coerceIn(0, rows)

            repeat(12) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    repeat(rows) { row ->
                        // Rows are drawn top-to-bottom, so activate them from the bottom
                        val active = row < activeRows

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(
                                    if (active) {
                                        Aw11Primary
                                    } else {
                                        Aw11Dim.copy(alpha = 0.35f)
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}