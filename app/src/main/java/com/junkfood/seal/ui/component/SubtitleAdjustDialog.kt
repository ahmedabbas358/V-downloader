package com.junkfood.seal.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AvTimer
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.SubtitleUtil
import com.junkfood.seal.util.ToastUtil
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SampleCue(val startMs: Long, val endMs: Long, val text: String)

private val previewCueRegex =
    Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})[,.](\d{3})""")

private fun formatPreviewTimestamp(ms: Long, separator: String): String {
    val safe = ms.coerceAtLeast(0L)
    val h = safe / 3_600_000L
    val m = (safe % 3_600_000L) / 60_000L
    val s = (safe % 60_000L) / 1000L
    val rem = safe % 1000L
    return String.format(Locale.US, "%02d:%02d:%02d%s%03d", h, m, s, separator, rem)
}

@Composable
fun SubtitleAdjustDialog(
    file: File,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var offsetSeconds by remember { mutableStateOf("1.0") }
    var isOffsetError by remember { mutableStateOf(false) }
    var saveAsCopy by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var sampleCue by remember { mutableStateOf<SampleCue?>(null) }
    val scope = rememberCoroutineScope()
    val isVtt = remember(file) { file.name.endsWith(".vtt", ignoreCase = true) }

    LaunchedEffect(file) {
        sampleCue = withContext(Dispatchers.IO) {
            runCatching {
                file.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    var foundMatch: MatchResult? = null
                    val captionText = StringBuilder()
                    var lineCount = 0
                    while (lineCount < 120) {
                        val line = reader.readLine() ?: break
                        lineCount++
                        if (foundMatch == null) {
                            foundMatch = previewCueRegex.find(line)
                        } else {
                            if (line.isBlank()) break
                            if (captionText.isNotEmpty()) captionText.append(" ")
                            captionText.append(line.trim())
                        }
                    }
                    foundMatch?.let { m ->
                        val h1 = m.groupValues[1].toLongOrNull() ?: 0L
                        val min1 = m.groupValues[2].toLong()
                        val s1 = m.groupValues[3].toLong()
                        val ms1 = m.groupValues[4].toLong()
                        val h2 = m.groupValues[5].toLongOrNull() ?: 0L
                        val min2 = m.groupValues[6].toLong()
                        val s2 = m.groupValues[7].toLong()
                        val ms2 = m.groupValues[8].toLong()
                        SampleCue(
                            startMs = ((h1 * 3600 + min1 * 60 + s1) * 1000 + ms1),
                            endMs = ((h2 * 3600 + min2 * 60 + s2) * 1000 + ms2),
                            text = captionText.toString().ifBlank { "…" },
                        )
                    }
                }
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(imageVector = Icons.Outlined.Subtitles, contentDescription = null) },
        title = {
            Text(
                text = stringResource(R.string.subtitle_adjust_title),
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
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.subtitle_adjust_prompt),
                    style = MaterialTheme.typography.labelLarge,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = offsetSeconds,
                    onValueChange = {
                        offsetSeconds = it
                        isOffsetError = false
                    },
                    isError = isOffsetError,
                    label = { Text(stringResource(R.string.subtitle_offset_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val cur = offsetSeconds.toDoubleOrNull() ?: 0.0
                            offsetSeconds = String.format(Locale.US, "%.1f", cur - 1.0)
                            isOffsetError = false
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.shift_minus_1s))
                    }

                    OutlinedButton(
                        onClick = {
                            val cur = offsetSeconds.toDoubleOrNull() ?: 0.0
                            offsetSeconds = String.format(Locale.US, "%.1f", cur - 0.5)
                            isOffsetError = false
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("-0.5s")
                    }

                    OutlinedButton(
                        onClick = {
                            val cur = offsetSeconds.toDoubleOrNull() ?: 0.0
                            offsetSeconds = String.format(Locale.US, "%.1f", cur + 0.5)
                            isOffsetError = false
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+0.5s")
                    }

                    OutlinedButton(
                        onClick = {
                            val cur = offsetSeconds.toDoubleOrNull() ?: 0.0
                            offsetSeconds = String.format(Locale.US, "%.1f", cur + 1.0)
                            isOffsetError = false
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.shift_plus_1s))
                    }
                }

                // Live timing preview
                val parsedMs = ((offsetSeconds.trim().toDoubleOrNull() ?: 0.0) * 1000).toLong()
                sampleCue?.let { cue ->
                    Spacer(modifier = Modifier.height(10.dp))
                    val sep = if (isVtt) "." else ","
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = stringResource(R.string.subtitle_live_preview),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${formatPreviewTimestamp(cue.startMs, sep)}  →  ${formatPreviewTimestamp(cue.startMs + parsedMs, sep)}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = cue.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isProcessing) { saveAsCopy = !saveAsCopy }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = saveAsCopy,
                        onCheckedChange = { saveAsCopy = it },
                        enabled = !isProcessing,
                    )
                    Text(
                        text = stringResource(R.string.subtitle_save_copy),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Format conversion
                if (file.name.endsWith(".srt", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val vttFile = File(file.parentFile, file.nameWithoutExtension + ".vtt")
                                val res = SubtitleUtil.convertSrtToVtt(file, vttFile)
                                isProcessing = false
                                res.onSuccess {
                                    FileUtil.scanFileToMediaLibrary(it)
                                    ToastUtil.makeToast(context.getString(R.string.subtitle_convert_vtt_success))
                                    onDismissRequest()
                                }.onFailure {
                                    ToastUtil.makeToast(context.getString(R.string.subtitle_convert_failed, it.message ?: ""))
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.subtitle_convert_vtt_action))
                    }
                } else if (isVtt) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val srtFile = File(file.parentFile, file.nameWithoutExtension + ".srt")
                                val res = SubtitleUtil.convertVttToSrt(file, srtFile)
                                isProcessing = false
                                res.onSuccess {
                                    FileUtil.scanFileToMediaLibrary(it)
                                    ToastUtil.makeToast(context.getString(R.string.subtitle_convert_srt_success))
                                    onDismissRequest()
                                }.onFailure {
                                    ToastUtil.makeToast(context.getString(R.string.subtitle_convert_failed, it.message ?: ""))
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.subtitle_convert_srt_action))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sec = offsetSeconds.trim().toDoubleOrNull()
                    if (sec == null) {
                        isOffsetError = true
                        ToastUtil.makeToast(context.getString(R.string.subtitle_invalid_offset))
                        return@Button
                    }
                    val ms = (sec * 1000).toLong()
                    val outputTarget = if (saveAsCopy) {
                        File(file.parentFile, "${file.nameWithoutExtension}_synced.${file.extension}")
                    } else file
                    scope.launch {
                        isProcessing = true
                        val res = SubtitleUtil.shiftSubtitleTiming(file, ms, outputTarget)
                        isProcessing = false
                        res.onSuccess { savedFile ->
                            FileUtil.scanFileToMediaLibrary(savedFile)
                            ToastUtil.makeToast(
                                context.getString(R.string.subtitle_shift_success, offsetSeconds.trim())
                            )
                            onDismissRequest()
                        }.onFailure {
                            ToastUtil.makeToast(
                                context.getString(R.string.subtitle_shift_failed, it.message ?: "")
                            )
                        }
                    }
                },
                enabled = !isProcessing,
            ) {
                Icon(Icons.Outlined.AvTimer, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.apply_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !isProcessing) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
