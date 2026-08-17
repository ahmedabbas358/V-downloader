package com.junkfood.seal.ui.page

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.junkfood.seal.R
import com.junkfood.seal.util.ToastUtil
import com.junkfood.seal.util.UpdateUtil
import com.junkfood.seal.util.UpdateUtil.getVersion
import com.junkfood.seal.util.UpdateUtil.toVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(onDismissRequest: () -> Unit, release: UpdateUtil.Release) {
    var currentDownloadStatus by remember {
        mutableStateOf(UpdateUtil.DownloadStatus.NotYet as UpdateUtil.DownloadStatus)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    UpdateDialogImpl(
        onDismissRequest = onDismissRequest,
        release = release,
        onConfirmUpdate = {
            scope.launch(Dispatchers.IO) {
                runCatching {
                        UpdateUtil.downloadApk(release = release).collect { downloadStatus ->
                            currentDownloadStatus = downloadStatus
                            if (downloadStatus is UpdateUtil.DownloadStatus.Finished) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    UpdateUtil.installLatestApk()
                                }
                            }
                        }
                    }
                    .onFailure {
                        it.printStackTrace()
                        currentDownloadStatus = UpdateUtil.DownloadStatus.NotYet
                        ToastUtil.makeToastSuspend(context.getString(R.string.app_update_failed))
                    }
            }
        },
        downloadStatus = currentDownloadStatus,
    )
}

/** Converts markdown text with `**bold**`, `*italic*`, and `` `code` `` into AnnotatedString. */
fun formatMarkdownSpans(rawText: String, primaryColor: Color): AnnotatedString {
    val text = rawText.trim().removePrefix(">").trim()

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        val boldContent = text.substring(i + 2, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(boldContent) }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("*", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1 && !text.startsWith("**", i)) {
                        val italicContent = text.substring(i + 1, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append(italicContent)
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        val codeContent = text.substring(i + 1, end)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor,
                            )
                        ) {
                            append(codeContent)
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

@Composable
fun ArabicNoteCard(content: String) {
    val cleanContent =
        content
            .removePrefix(">")
            .trim()
            .removePrefix("🇸🇦")
            .trim()
            .removePrefix("**عربي:**")
            .removePrefix("عربي:")
            .removePrefix("**عربي**:")
            .trim()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = "🇸🇦", fontSize = 16.sp)
                    Text(
                        text = "التفاصيل بالعربية",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = formatMarkdownSpans(cleanContent, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
fun EnglishNoteCard(content: String) {
    val cleanContent =
        content
            .removePrefix(">")
            .trim()
            .removePrefix("🇬🇧")
            .trim()
            .removePrefix("**English:**")
            .removePrefix("English:")
            .removePrefix("**English**:")
            .trim()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = "🇬🇧", fontSize = 16.sp)
                    Text(
                        text = "English Details",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = formatMarkdownSpans(cleanContent, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
fun HighlightCard(title: String) {
    val cleanTitle = title.removePrefix(">").trim()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formatMarkdownSpans(cleanTitle, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun GenericMarkdownLine(line: String) {
    val trimmed = line.removePrefix(">").trim()
    when {
        trimmed.startsWith("### ") -> {
            Text(
                text = trimmed.removePrefix("### "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }
        trimmed.startsWith("## ") -> {
            Text(
                text = trimmed.removePrefix("## "),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
        trimmed.startsWith("# ") -> {
            Text(
                text = trimmed.removePrefix("# "),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
            val content = trimmed.substring(2).trim()
            Row(
                modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp).size(6.dp),
                ) {}
                Text(
                    text = formatMarkdownSpans(content, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        trimmed.startsWith("---") || trimmed.startsWith("***") -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        trimmed.isNotBlank() -> {
            Text(
                text = formatMarkdownSpans(trimmed, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
fun ReleaseNotesView(releaseNote: String, latestVersionTag: String = "") {
    val lines =
        remember(releaseNote) {
            releaseNote
                .lines()
                .map { it.trim() }
                .filter { line ->
                    val l = line.removePrefix(">").trim()
                    !(l.startsWith("🚀") && l.contains("V-Downloader", ignoreCase = true)) &&
                        !l.equals("V-Downloader", ignoreCase = true) &&
                        !(latestVersionTag.isNotEmpty() &&
                            l.equals(latestVersionTag, ignoreCase = true))
                }
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        lines.forEach { line ->
            val unquoted = line.removePrefix(">").trim()
            when {
                unquoted.contains("🇸🇦") ||
                    unquoted.startsWith("**عربي:**") ||
                    unquoted.startsWith("عربي:") -> {
                    ArabicNoteCard(content = line)
                }
                unquoted.contains("🇬🇧") ||
                    unquoted.startsWith("**English:**") ||
                    unquoted.startsWith("English:") -> {
                    EnglishNoteCard(content = line)
                }
                unquoted.contains("🔥") ||
                    unquoted.contains("What's New", ignoreCase = true) ||
                    unquoted.contains("أبرز التحديثات") -> {
                    HighlightCard(title = line)
                }
                else -> {
                    GenericMarkdownLine(line = line)
                }
            }
        }
    }
}

@Composable
fun UpdateDialogImpl(
    onDismissRequest: () -> Unit,
    onConfirmUpdate: () -> Unit,
    release: UpdateUtil.Release? = null,
    title: String = release?.name.orEmpty(),
    releaseNote: String = release?.body.orEmpty(),
    downloadStatus: UpdateUtil.DownloadStatus,
) {
    val context = LocalContext.current
    val currentVersion = remember { UpdateUtil.getCurrentVersion(context).toVersionName() }
    val newVersionName =
        remember(release, title) {
            val v = release?.getVersion()?.toVersionName()
            if (!v.isNullOrEmpty() && v != "0.0.0") "v$v"
            else {
                val parsed = title.toVersion().toVersionName()
                if (parsed != "0.0.0") "v$parsed" else title
            }
        }

    val subtitle =
        remember(title, release) {
            val raw = release?.name ?: title
            // Clean out the version tag prefix if title contains extra details
            raw.replace(Regex("""^🚀?\s*V-Downloader\s*v?\d+\.\d+(\.\d+)?\s*(-|—)?\s*"""), "")
                .trim()
        }

    AlertDialog(
        onDismissRequest = {
            if (downloadStatus !is UpdateUtil.DownloadStatus.Progress) onDismissRequest()
        },
        shape = RoundedCornerShape(28.dp),
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // New Version Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = newVersionName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Current Version Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            text = stringResource(R.string.current_version_badge, currentVersion),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                if (subtitle.isNotBlank() && !subtitle.equals(newVersionName, ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (downloadStatus is UpdateUtil.DownloadStatus.Progress) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = stringResource(R.string.downloading_update),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text(
                                    text = "${downloadStatus.percent}%",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { downloadStatus.percent / 100f },
                                modifier =
                                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }

                ReleaseNotesView(releaseNote = releaseNote, latestVersionTag = newVersionName)
            }
        },
        confirmButton = {
            if (downloadStatus !is UpdateUtil.DownloadStatus.Progress) {
                Button(
                    onClick = onConfirmUpdate,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.update_now),
                        style =
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest, shape = RoundedCornerShape(12.dp)) {
                Text(
                    text =
                        if (downloadStatus is UpdateUtil.DownloadStatus.Progress)
                            stringResource(R.string.cancel)
                        else stringResource(R.string.update_later),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}

@Preview
@Composable
private fun Preview() {
    var b by remember { mutableStateOf(false) }
    val flow: MutableStateFlow<UpdateUtil.DownloadStatus> = remember {
        MutableStateFlow(UpdateUtil.DownloadStatus.NotYet)
    }

    LaunchedEffect(b) {
        if (b) {
            repeat(100) { i ->
                flow.update { UpdateUtil.DownloadStatus.Progress(percent = i) }
                delay(50)
            }
        } else {
            flow.update { UpdateUtil.DownloadStatus.NotYet }
        }
    }

    val status by flow.collectAsStateWithLifecycle()

    UpdateDialogImpl(
        onDismissRequest = { b = false },
        release =
            UpdateUtil.Release(
                name = "🚀 V-Downloader v3.0.8 استقرار التشغيل والإنتاج",
                tagName = "v3.0.8",
                body =
                    """> 🔥 **أبرز التحديثات في v3.0.8 | What's New:**
> 🇸🇦 **عربي:** حل كافة أخطاء البناء، تحسين استهلاك الذاكرة وضمان الاستقرار الكامل للإنتاج.
> 🇬🇧 **English:** Resolved all compilation issues, optimized memory churn, and achieved production stability.
---
- Added support for adaptive formats
- Fixed download crash on low memory devices
""",
            ),
        onConfirmUpdate = { b = true },
        downloadStatus = status,
    )
}

