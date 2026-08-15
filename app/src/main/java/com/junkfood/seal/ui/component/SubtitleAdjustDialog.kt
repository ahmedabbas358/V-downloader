package com.junkfood.seal.ui.component

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junkfood.seal.util.SubtitleUtil
import com.junkfood.seal.util.ToastUtil
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SubtitleAdjustDialog(
    file: File,
    onDismissRequest: () -> Unit
) {
    var offsetSeconds by remember { mutableStateOf("1.0") }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(imageVector = Icons.Outlined.Subtitles, contentDescription = null)
        },
        title = {
            Text(
                text = "محاذاة وضبط توقيت الترجمة",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "تقديم أو تأخير التوقيت (بالثواني):",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = offsetSeconds,
                    onValueChange = { offsetSeconds = it },
                    label = { Text("قيمة الإزاحة (مثال: 1.5 أو -2.0)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cur = offsetSeconds.toDoubleOrNull() ?: 0.0
                            offsetSeconds = String.format(java.util.Locale.US, "%.1f", cur - 1.0)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("- 1.0 ث")
                    }

                    OutlinedButton(
                        onClick = {
                            val cur = offsetSeconds.toDoubleOrNull() ?: 0.0
                            offsetSeconds = String.format(java.util.Locale.US, "%.1f", cur + 1.0)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ 1.0 ث")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Format conversion
                if (file.name.endsWith(".srt", ignoreCase = true)) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val vttFile = File(file.parentFile, file.nameWithoutExtension + ".vtt")
                                SubtitleUtil.convertSrtToVtt(file, vttFile)
                                ToastUtil.makeToast("تم تحويل الترجمة إلى VTT بنجاح")
                                isProcessing = false
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تحويل إلى صيغة WebVTT (.vtt)")
                    }
                } else if (file.name.endsWith(".vtt", ignoreCase = true)) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val srtFile = File(file.parentFile, file.nameWithoutExtension + ".srt")
                                SubtitleUtil.convertVttToSrt(file, srtFile)
                                ToastUtil.makeToast("تم تحويل الترجمة إلى SRT بنجاح")
                                isProcessing = false
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تحويل إلى صيغة SubRip (.srt)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sec = offsetSeconds.toDoubleOrNull()
                    if (sec == null) {
                        ToastUtil.makeToast("يرجى إدخال قيمة رقمية صحيحة")
                        return@Button
                    }
                    val ms = (sec * 1000).toLong()
                    scope.launch {
                        isProcessing = true
                        SubtitleUtil.shiftSubtitleTiming(file, ms)
                        ToastUtil.makeToast("تم ضبط توقيت ملف الترجمة بنجاح ($offsetSeconds ثانية)")
                        isProcessing = false
                        onDismissRequest()
                    }
                },
                enabled = !isProcessing
            ) {
                Icon(Icons.Outlined.AvTimer, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تطبيق التعديل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("إلغاء")
            }
        }
    )
}
