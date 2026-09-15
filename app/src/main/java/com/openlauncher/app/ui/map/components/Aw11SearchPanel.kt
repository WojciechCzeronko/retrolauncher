package com.openlauncher.app.ui.map.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openlauncher.app.ui.map.HereNearbyCategory
import com.openlauncher.app.ui.map.HereSearchResult
import com.openlauncher.app.ui.theme.Aw11Background
import com.openlauncher.app.ui.theme.Aw11Primary
import com.openlauncher.app.ui.theme.Aw11Secondary
import java.util.Locale

@Composable
fun Aw11SearchPanel(
    isOpen: Boolean,
    query: String,
    results: List<HereSearchResult>,
    isSearching: Boolean,
    error: String?,
    showOpenButton: Boolean = true,
    onOpen: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onNearbySearch: (HereNearbyCategory) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onResultSelected: (HereSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val context = LocalContext.current

    var isListening by remember {
        mutableStateOf(false)
    }

    var speechError by remember {
        mutableStateOf<String?>(null)
    }

    val currentOnQueryChange by rememberUpdatedState(
        onQueryChange
    )

    val currentOnSearch by rememberUpdatedState(
        onSearch
    )

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    val recognitionIntent = remember {
        Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
            )
        }
    }

    DisposableEffect(speechRecognizer) {
        if (speechRecognizer == null) {
            onDispose { }
        } else {
            speechRecognizer.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(
                        params: Bundle?
                    ) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(
                        rmsdB: Float
                    ) = Unit

                    override fun onBufferReceived(
                        buffer: ByteArray?
                    ) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onError(
                        error: Int
                    ) {
                        isListening = false

                        speechError =
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH ->
                                    "NO MATCH"

                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                    "NO SPEECH DETECTED"

                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                    "RECOGNIZER BUSY"

                                else ->
                                    "ERROR $error"
                            }
                    }

                    override fun onResults(
                        results: Bundle?
                    ) {
                        isListening = false

                        val spokenText =
                            results
                                ?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                ?.firstOrNull()
                                ?.trim()

                        if (!spokenText.isNullOrBlank()) {
                            speechError = null

                            currentOnQueryChange(
                                spokenText
                            )

                            // Search immediately after dictation.
                            currentOnSearch(
                                spokenText
                            )
                        } else {
                            speechError =
                                "NO MATCH"
                        }
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) = Unit

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) = Unit
                }
            )

            onDispose {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
            }
        }
    }

    fun startVoiceRecognition() {
        if (speechRecognizer == null) {
            speechError =
                "VOICE INPUT UNAVAILABLE"

            return
        }

        speechError = null

        runCatching {
            speechRecognizer.startListening(
                recognitionIntent
            )
        }.onFailure {
            isListening = false
            speechError =
                "VOICE INPUT FAILED"
        }
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startVoiceRecognition()
            } else {
                speechError =
                    "MICROPHONE PERMISSION DENIED"
            }
        }

    fun triggerVoiceInput() {
        focusManager.clearFocus()
        keyboardController?.hide()

        val granted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startVoiceRecognition()
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    fun triggerSearch() {
        if (
            query.isBlank() ||
            isSearching
        ) {
            return
        }

        focusManager.clearFocus()
        keyboardController?.hide()

        onSearch(query)
    }

    fun triggerNearbySearch(
        category: HereNearbyCategory
    ) {
        if (isSearching) {
            return
        }

        focusManager.clearFocus()
        keyboardController?.hide()

        onNearbySearch(category)
    }
    if (!isOpen) {
        if (!showOpenButton) {
            return
        }
        Box(
            modifier = modifier
                .widthIn(min = 112.dp)
                .heightIn(min = 56.dp)
                .background(
                    Aw11Background.copy(alpha = 0.90f)
                )
                .border(
                    width = 1.dp,
                    color = Aw11Primary.copy(alpha = 0.85f)
                )
                .clickable(onClick = onOpen)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SEARCH",
                color = Aw11Primary,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
        }

        return
    }

    Column(
        modifier = modifier
            .width(240.dp)
            .background(
                Aw11Background.copy(alpha = 0.96f)
            )
            .border(
                width = 1.dp,
                color = Aw11Primary.copy(alpha = 0.85f)
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        triggerSearch()
                    }
                ),
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = Aw11Primary,
                        fontSize = 15.sp
                    ),
                cursorBrush =
                    SolidColor(Aw11Primary),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(
                                width = 1.dp,
                                color = Aw11Primary.copy(
                                    alpha = 0.35f
                                )
                            )
                            .padding(
                                start = 10.dp,
                                end = 6.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f),
                            contentAlignment =
                                Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text =
                                        if (isListening) {
                                            "LISTENING..."
                                        } else {
                                            "DESTINATION..."
                                        },
                                    color = Aw11Secondary,
                                    fontSize = 10.sp
                                )
                            }

                            innerTextField()
                        }

                        if (query.isNotEmpty()) {
                            Text(
                                text = "CLR",
                                color = Aw11Secondary,
                                fontSize = 10.sp,
                                letterSpacing = 0.sp,
                                modifier = Modifier
                                    .clickable(
                                        onClick = onClear
                                    )
                                    .padding(
                                        horizontal = 4.dp,
                                        vertical = 6.dp
                                    )
                            )
                        }
                    }
                }
            )

            Spacer(
                Modifier.width(6.dp)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        width = 1.dp,
                        color = Aw11Primary.copy(
                            alpha = 0.85f
                        )
                    )
                    .clickable {
                        if (query.isBlank()) {
                            if (isListening) {
                                speechRecognizer?.cancel()
                                isListening = false
                            } else {
                                triggerVoiceInput()
                            }
                        } else {
                            triggerSearch()
                        }
                    },
                contentAlignment =
                    Alignment.Center
            ) {
                if (query.isBlank()) {
                    Text(
                        text =
                            if (isListening) {
                                "REC"
                            } else {
                                "MIC"
                            },
                        color = Aw11Primary,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                } else {
                    Canvas(
                        modifier = Modifier
                            .size(20.dp)
                    ) {
                        val strokeWidth =
                            2.dp.toPx()

                        drawCircle(
                            color = Aw11Primary,
                            radius =
                                size.minDimension * 0.28f,
                            center = Offset(
                                x = size.width * 0.42f,
                                y = size.height * 0.42f
                            ),
                            style = Stroke(
                                width = strokeWidth
                            )
                        )

                        drawLine(
                            color = Aw11Primary,
                            start = Offset(
                                x = size.width * 0.62f,
                                y = size.height * 0.62f
                            ),
                            end = Offset(
                                x = size.width * 0.84f,
                                y = size.height * 0.84f
                            ),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Square
                        )
                    }
                }
            }

            Spacer(
                Modifier.width(4.dp)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        width = 1.dp,
                        color = Aw11Primary.copy(
                            alpha = 0.85f
                        )
                    )
                    .clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()

                        onClose()
                    },
                contentAlignment =
                    Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(18.dp)
                ) {
                    val strokeWidth =
                        2.dp.toPx()

                    drawLine(
                        color = Aw11Secondary,
                        start = Offset(
                            x = size.width * 0.2f,
                            y = size.height * 0.2f
                        ),
                        end = Offset(
                            x = size.width * 0.8f,
                            y = size.height * 0.8f
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Square
                    )

                    drawLine(
                        color = Aw11Secondary,
                        start = Offset(
                            x = size.width * 0.8f,
                            y = size.height * 0.2f
                        ),
                        end = Offset(
                            x = size.width * 0.2f,
                            y = size.height * 0.8f
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Square
                    )
                }
            }
        }

        if (query.isBlank()) {

            Spacer(
                Modifier.height(10.dp)
            )

            Text(
                text = "NEARBY",
                color = Aw11Secondary,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )

            Spacer(
                Modifier.height(5.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                NearbyCategoryButton(
                    label = "FUEL",
                    enabled = !isSearching,
                    onClick = {
                        triggerNearbySearch(
                            HereNearbyCategory.FUEL
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(
                    Modifier.width(6.dp)
                )

                NearbyCategoryButton(
                    label = "PARKING",
                    enabled = !isSearching,
                    onClick = {
                        triggerNearbySearch(
                            HereNearbyCategory.PARKING
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(
                Modifier.height(6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                NearbyCategoryButton(
                    label = "FOOD",
                    enabled = !isSearching,
                    onClick = {
                        triggerNearbySearch(
                            HereNearbyCategory.FOOD
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(
                    Modifier.width(6.dp)
                )

                NearbyCategoryButton(
                    label = "SHOPPING",
                    enabled = !isSearching,
                    onClick = {
                        triggerNearbySearch(
                            HereNearbyCategory.SHOPPING
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (isSearching) {
            Text(
                text = "SEARCHING...",
                color = Aw11Secondary,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        error?.let { searchError ->
            Text(
                text = "SEARCH ERROR: $searchError",
                color = Aw11Primary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        results.forEachIndexed { index, result ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .border(
                        width = 1.dp,
                        color = Aw11Primary.copy(alpha = 0.35f)
                    )
                    .clickable {
                        onResultSelected(result)
                    }
                    .padding(
                        horizontal = 6.dp,
                        vertical = 6.dp
                    )
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .border(
                                width = 1.dp,
                                color = Aw11Primary
                            )
                            .padding(vertical = 3.dp),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = Aw11Primary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(
                        Modifier.width(6.dp)
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Row {
                            Text(
                                text =
                                    result.title.uppercase(),
                                color = Aw11Primary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow =
                                    TextOverflow.Ellipsis,
                                modifier =
                                    Modifier.weight(1f)
                            )

                            Text(
                                text = formatDistance(
                                    result.distanceMeters
                                ),
                                color = Aw11Secondary,
                                fontSize = 9.sp
                            )
                        }

                        Text(
                            text =
                                result.address.uppercase(),
                            color = Aw11Secondary,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun formatDistance(
    distanceMeters: Double
): String {
    return if (distanceMeters < 1000.0) {
        "${distanceMeters.toInt()} M"
    } else {
        String.format(
            Locale.US,
            "%.1f KM",
            distanceMeters / 1000.0
        )
    }
}

@Composable
private fun NearbyCategoryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .border(
                width = 1.dp,
                color = Aw11Primary.copy(
                    alpha =
                        if (enabled) {
                            0.55f
                        } else {
                            0.20f
                        }
                )
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color =
                Aw11Primary.copy(
                    alpha =
                        if (enabled) {
                            1f
                        } else {
                            0.35f
                        }
                ),
            fontSize = 10.sp,
            letterSpacing = 0.7.sp
        )
    }
}