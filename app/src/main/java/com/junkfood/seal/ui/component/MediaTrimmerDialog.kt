package com.junkfood.seal.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.MediaProcessingEngine
import com.junkfood.seal.util.ToastUtil
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MediaTrimmerDialog(
    file: File,
    onDismissRequest: () -> Unit
) {
    var startTime by remember { mutableStateOf("00:00:00") }
    var endTime by remember { mutableStateOf("00:01:00") }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(imageVector = Icons.Outlined.ContentCut, contentDescription = null)
        },
        title = {
            Text(
                text = "قص وتشذيب فوري للملف",
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
                    text = "حدد نقطة البداية والنهاية (HH:MM:SS أو بالثواني):",
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("البداية") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("النهاية") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "⚡ يتم القص بدون إعادة ضغط للحفاظ على 100% من الجودة الأصلية وبسرعة فائقة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (startTime.isBlank() || endTime.isBlank()) {
                        ToastUtil.makeToast("يرجى تحديد وقت البداية والنهاية")
                        return@Button
                    }
                    scope.launch {
                        isProcessing = true
                        val trimmedName = "${file.nameWithoutExtension}_trimmed.${file.extension}"
                        val outputFile = File(file.parentFile, trimmedName)
                        val result = MediaProcessingEngine.trimMediaLossless(
                            inputFile = file,
                            startFormatted = startTime.trim(),
                            endFormatted = endTime.trim(),
                            outputFile = outputFile
                        )
                        isProcessing = false
                        result.onSuccess {
                            FileUtil.scanFileToMediaLibrary(outputFile)
                            ToastUtil.makeToast("تم قص الملف وحفظه بنجاح: ${outputFile.name}")
                            onDismissRequest()
                        }.onFailure {
                            ToastUtil.makeToast("فشل قص الملف: ${it.message}")
                        }
                    }
                },
                enabled = !isProcessing
            ) {
                Icon(Icons.Outlined.ContentCut, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isProcessing) "جار القص..." else "قص وحفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("إلغاء")
            }
        }
    )
}
