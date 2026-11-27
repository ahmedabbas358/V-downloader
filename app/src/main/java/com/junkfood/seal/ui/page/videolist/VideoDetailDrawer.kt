@file:OptIn(ExperimentalMaterialApi::class)

package com.junkfood.seal.ui.page.videolist

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.junkfood.seal.R
import com.junkfood.seal.database.objects.DownloadedVideoInfo
import com.junkfood.seal.ui.common.HapticFeedback.slightHapticFeedback
import com.junkfood.seal.ui.component.FilledTonalButtonWithIcon
import com.junkfood.seal.ui.component.LongTapTextButton
import com.junkfood.seal.ui.component.OutlinedButtonWithIcon
import com.junkfood.seal.ui.component.SealModalBottomSheetM2
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.ToastUtil

@Composable
fun VideoDetailDrawer(
    sheetState: ModalBottomSheetState,
    info: DownloadedVideoInfo,
    isFileAvailable: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDelete: () -> Unit = {},
    onTrimMedia: ((java.io.File) -> Unit)? = null,
    onAdjustSubtitle: ((java.io.File) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    BackHandler(sheetState.targetValue == ModalBottomSheetValue.Expanded) { onDismissRequest() }

    val onReDownload =
        remember(info) {
            {
                context.startActivity(
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        setPackage(context.packageName)
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, info.videoUrl)
                    }
                )
            }
        }

    val localFile = remember(info.videoPath) {
        if (info.videoPath.startsWith("content://", ignoreCase = true)) null
        else java.io.File(info.videoPath).takeIf { it.exists() }
    }
    val canTrim = remember(localFile) {
        localFile != null && (FileUtil.isVideoFile(localFile) || FileUtil.isAudioFile(localFile))
    }
    val canAdjustSub = remember(localFile) {
        localFile != null && FileUtil.isSubtitleFile(localFile)
    }

    val shareTitle = stringResource(id = R.string.share)
    with(info) {
        VideoDetailDrawerImpl(
            sheetState = sheetState,
            title = videoTitle,
            author = videoAuthor,
            url = videoUrl,
            isFileAvailable = isFileAvailable,
            onReDownload = onReDownload,
            onDismissRequest = onDismissRequest,
            onDelete = {
                view.slightHapticFeedback()
                onDismissRequest()
                onDelete()
            },
            onOpenLink = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismissRequest()
                uriHandler.openUri(videoUrl)
            },
            onOpenExternal = {
                view.slightHapticFeedback()
                onDismissRequest()
                FileUtil.openFile(path = videoPath) {
                    ToastUtil.makeToastSuspend(context.getString(R.string.file_unavailable))
                }
            },
            onShareFile = {
                view.slightHapticFeedback()
                FileUtil.createIntentForSharingFile(videoPath)?.runCatching {
                    context.startActivity(Intent.createChooser(this, shareTitle))
                }
            },
            onTrimMedia = if (canTrim && onTrimMedia != null && localFile != null) {
                {
                    view.slightHapticFeedback()
                    onDismissRequest()
                    onTrimMedia(localFile)
                }
            } else null,
            onAdjustSubtitle = if (canAdjustSub && onAdjustSubtitle != null && localFile != null) {
                {
                    view.slightHapticFeedback()
                    onDismissRequest()
                    onAdjustSubtitle(localFile)
                }
            } else null,
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DrawerPreview() {
    SealTheme {
        VideoDetailDrawerImpl(
            sheetState =
                ModalBottomSheetState(
                    ModalBottomSheetValue.Expanded,
                    density = LocalDensity.current,
                ),
            onReDownload = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailDrawerImpl(
    sheetState: ModalBottomSheetState =
        ModalBottomSheetState(ModalBottomSheetValue.Hidden, density = LocalDensity.current),
    title: String = stringResource(id = R.string.video_title_sample_text),
    author: String = stringResource(id = R.string.video_creator_sample_text),
    url: String = "https://www.example.com",
    onDismissRequest: () -> Unit = {},
    isFileAvailable: Boolean = true,
    onReDownload: (() -> Unit) = {},
    onDelete: () -> Unit = {},
    onOpenLink: () -> Unit = {},
    onOpenExternal: (() -> Unit)? = null,
    onShareFile: () -> Unit = {},
    onTrimMedia: (() -> Unit)? = null,
    onAdjustSubtitle: (() -> Unit)? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    SealModalBottomSheetM2(
        sheetState = sheetState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        sheetContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                SelectionContainer {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                if (author != "playlist" && author != "null")
                    SelectionContainer {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            text = author,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
            Row(modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
                LongTapTextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(url))
                        ToastUtil.makeToast(context.getString(R.string.link_copied))
                    },
                    onClickLabel = stringResource(id = R.string.copy_link),
                    onLongClick = onOpenLink,
                    onLongClickLabel = stringResource(R.string.open_url),
                ) {
                    Icon(Icons.Outlined.Link, stringResource(R.string.video_url))
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        text = url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 24.dp)
                        .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButtonWithIcon(
                    onClick = onDelete,
                    icon = Icons.Outlined.Delete,
                    text = stringResource(R.string.remove),
                )
                if (isFileAvailable) {
                    if (onTrimMedia != null) {
                        OutlinedButtonWithIcon(
                            onClick = onTrimMedia,
                            icon = Icons.Outlined.ContentCut,
                            text = stringResource(R.string.trim_media),
                        )
                    }
                    if (onAdjustSubtitle != null) {
                        OutlinedButtonWithIcon(
                            onClick = onAdjustSubtitle,
                            icon = Icons.Outlined.Subtitles,
                            text = stringResource(R.string.subtitle_adjust_title),
                        )
                    }
                    if (onOpenExternal != null) {
                        OutlinedButtonWithIcon(
                            onClick = onOpenExternal,
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            text = stringResource(R.string.open_with),
                        )
                    }
                    FilledTonalButtonWithIcon(
                        onClick = onShareFile,
                        icon = Icons.Outlined.Share,
                        text = stringResource(R.string.share),
                    )
                } else {
                    FilledTonalButtonWithIcon(
                        onClick = onReDownload,
                        icon = Icons.Outlined.FileDownload,
                        text = stringResource(R.string.redownload),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                    )
                }
            }
        },
    )
}
