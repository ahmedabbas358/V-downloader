package com.junkfood.seal.ui.component

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.MediaProcessingEngine
import com.junkfood.seal.util.ToastUtil
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatHms(seconds: Float): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

@Composable
fun MediaTrimmerDialog(
    file: File,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var durationSec by remember { mutableFloatStateOf(0f) }
    var startTime by remember { mutableStateOf("00:00:00") }
    var endTime by remember { mutableStateOf("00:01:00") }
    var isError by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file) {
        val probedSec = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                (durationMs / 1000f).coerceAtLeast(0f)
            } catch (_: Exception) {
                0f
            } finally {
                runCatching { retriever.release() }
            }
        }
        if (probedSec > 1f) {
            durationSec = probedSec
            endTime = formatHms(probedSec)
        }
    }

    val sliderStart = (MediaProcessingEngine.parseTimestampSeconds(startTime)?.toFloat() ?: 0f)
        .coerceIn(0f, durationSec.coerceAtLeast(1f))
    val sliderEnd = (MediaProcessingEngine.parseTimestampSeconds(endTime)?.toFloat() ?: durationSec)
        .coerceIn(sliderStart, durationSec.coerceAtLeast(1f))

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(imageVector = Icons.Outlined.ContentCut, contentDescription = null) },
        title = {
            Text(
                text = stringResource(R.string.trim_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (durationSec > 1f) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.trim_duration_label, formatHms(durationSec)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (durationSec > 2f) {
                    RangeSlider(
                        value = sliderStart..sliderEnd.coerceAtLeast(sliderStart + 1f).coerceAtMost(durationSec),
                        onValueChange = { range ->
                            startTime = formatHms(range.start)
                            endTime = formatHms(range.endInclusive)
                            isError = false
                        },
                        valueRange = 0f..durationSec,
                        enabled = !isProcessing,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (durationSec > 30f) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    startTime = "00:00:00"
                                    endTime = formatHms(30f)
                                    isError = false
                                },
                                label = { Text(stringResource(R.string.trim_preset_first_30s)) },
                            )
                        }
                        FilterChip(
                            selected = false,
                            onClick = {
                                startTime = "00:00:00"
                                endTime = formatHms(durationSec / 2f)
                                isError = false
                            },
                            label = { Text(stringResource(R.string.trim_preset_half)) },
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                startTime = "00:00:00"
                                endTime = formatHms(durationSec)
                                isError = false
                            },
                            label = { Text(stringResource(R.string.trim_preset_full)) },
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Text(
                        text = stringResource(R.string.trim_dialog_range_prompt),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {
                            startTime = it
                            isError = false
                        },
                        isError = isError,
                        label = { Text(stringResource(R.string.trim_start_label)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {
                            endTime = it
                            isError = false
                        },
                        isError = isError,
                        label = { Text(stringResource(R.string.trim_end_label)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.trim_invalid_times),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.trim_lossless_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startSec = MediaProcessingEngine.parseTimestampSeconds(startTime)
                    val endSec = MediaProcessingEngine.parseTimestampSeconds(endTime)
                    if (startSec == null || endSec == null || endSec <= startSec) {
                        isError = true
                        ToastUtil.makeToast(context.getString(R.string.trim_invalid_times))
                        return@Button
                    }
                    scope.launch {
                        isProcessing = true
                        val trimmedName = "${file.nameWithoutExtension}_trimmed.${file.extension}"
                        val targetOutput = File(file.parentFile, trimmedName)
                        val result = MediaProcessingEngine.trimMediaLossless(
                            inputFile = file,
                            startFormatted = startTime.trim(),
                            endFormatted = endTime.trim(),
                            outputFile = targetOutput,
                        )
                        isProcessing = false
                        result.onSuccess { trimmedFile ->
                            FileUtil.scanFileToMediaLibrary(trimmedFile)
                            ToastUtil.makeToast(context.getString(R.string.trim_success, trimmedFile.name))
                            onDismissRequest()
                        }.onFailure {
                            ToastUtil.makeToast(context.getString(R.string.trim_failed, it.message ?: ""))
                        }
                    }
                },
                enabled = !isProcessing,
            ) {
                Icon(Icons.Outlined.ContentCut, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isProcessing) stringResource(R.string.trim_in_progress)
                    else stringResource(R.string.trim_and_save)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !isProcessing) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
