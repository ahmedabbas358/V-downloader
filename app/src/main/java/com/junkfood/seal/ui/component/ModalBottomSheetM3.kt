package com.junkfood.seal.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SealModalBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState =
        with(LocalDensity.current) {
            SheetState(
                initialValue = SheetValue.Expanded,
                skipPartiallyExpanded = true,
                velocityThreshold = { 56.dp.toPx() },
                positionalThreshold = { 125.dp.toPx() },
            )
        },
    onDismissRequest: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 28.dp),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    // NOTE: we intentionally do NOT apply Modifier.glassmorphism() here. That modifier can only
    // blur its own content (this sheet's children), not whatever screen is visually behind the
    // sheet — Android has no real backdrop-blur without a library like `haze`, which isn't wired
    // up yet. Combining a transparent containerColor with a no-op blur let the previous screen's
    // content show through (unblurred) inside the sheet bounds. Until real backdrop blur is
    // added, the sheet surface must stay fully opaque so nothing bleeds through.
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        properties = properties,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                width = 48.dp,
                height = 5.dp
            )
        },
    ) {
        Column(modifier = Modifier.padding(paddingValues = contentPadding)) {
            content()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DrawerSheetSubtitle(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}
