package com.junkfood.seal.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.download.engine.postprocess.MediaStorageScanner
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.MediaProcessingEngine
import com.junkfood.seal.util.ToastUtil
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RemoveMusicDialog(
    file: File,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var processingResult by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {
            if (!isProcessing) onDismissRequest()
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.MusicOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "عزل الصوت وإزالة الموسيقى",
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
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${file.extension.uppercase()} • ${FileUtil.formatFileSize(file.length())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isProcessing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "جاري تطبيق معالجة DSP المتقدمة لإلغاء الموسيقى وعزل الصوت...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (processingResult != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "تم عزل الصوت بنجاح!",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = processingResult!!.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "خطأ أثناء المعالجة: $errorMessage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "يقوم هذا المحرك بتحليل الإشارة الصوتية، وإلغاء الآلات الموسيقية وترددات الخلفية، وتوضيح الصوت البشري بدقة عالية.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (processingResult != null) {
                Button(
                    onClick = {
                        FileUtil.openFile(processingResult!!.absolutePath) {
                            ToastUtil.makeToast("تعذر فتح الملف")
                        }
                        onDismissRequest()
                    }
                ) {
                    Text("تشغيل الملف المعزول")
                }
            } else if (!isProcessing) {
                Button(
                    onClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            val parent = file.parentFile ?: context.filesDir
                            val baseName = file.nameWithoutExtension
                            val ext = file.extension
                            val outputFile = File(parent, "[Vocals] $baseName.$ext")

                            val res = MediaProcessingEngine.removeMusicAndIsolateVoice(
                                inputFile = file,
                                outputFile = outputFile
                            )

                            isProcessing = false
                            if (res.isSuccess) {
                                val savedFile = res.getOrThrow()
                                MediaStorageScanner.scanSingleFile(savedFile)
                                processingResult = savedFile
                                ToastUtil.makeToast("تم عزل الصوت وحفظه في نفس المجلد")
                            } else {
                                errorMessage = res.exceptionOrNull()?.message ?: "فشلت عملية العزل"
                            }
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بدء عزل الصوت")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isProcessing
            ) {
                Text(stringResource(if (processingResult != null) R.string.close else R.string.cancel))
            }
        }
    )
}
