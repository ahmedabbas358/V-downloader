package com.junkfood.seal.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.junkfood.seal.R
import com.junkfood.seal.ui.common.LocalWindowWidthState
import com.junkfood.seal.ui.common.Route
import com.junkfood.seal.ui.page.downloadv2.DownloadPageImplV2
import kotlinx.coroutines.launch

@Composable
fun NavigationDrawer(
    modifier: Modifier = Modifier,
    drawerState: DrawerState,
    windowWidth: WindowWidthSizeClass = LocalWindowWidthState.current,
    currentRoute: String? = null,
    currentTopDestination: String? = null,
    showQuickSettings: Boolean = true,
    onNavigateToRoute: (String) -> Unit,
    onDismissRequest: suspend () -> Unit,
    gesturesEnabled: Boolean = true,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    when (windowWidth) {
        WindowWidthSizeClass.Compact,
        WindowWidthSizeClass.Medium -> {
            ModalNavigationDrawer(
                gesturesEnabled = gesturesEnabled,
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(drawerState = drawerState, modifier = modifier.width(360.dp)) {
                        NavigationDrawerSheetContent(
                            modifier = Modifier,
                            currentRoute = currentRoute,
                            showQuickSettings = showQuickSettings,
                            onNavigateToRoute = onNavigateToRoute,
                            onDismissRequest = onDismissRequest,
                            footer = footer,
                        )
                    }
                },
                content = content,
            )
        }
        WindowWidthSizeClass.Expanded -> {
            ModalNavigationDrawer(
                gesturesEnabled = drawerState.isOpen,
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(drawerState = drawerState, modifier = modifier.width(360.dp)) {
                        NavigationDrawerSheetContent(
                            modifier = Modifier,
                            currentRoute = currentRoute,
                            showQuickSettings = showQuickSettings,
                            onNavigateToRoute = onNavigateToRoute,
                            onDismissRequest = onDismissRequest,
                            footer = footer,
                        )
                    }
                },
            ) {
                Row {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.zIndex(1f),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxHeight().systemBarsPadding().width(92.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(8.dp))
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Icon(Icons.Outlined.Menu, null)
                            }
                            Spacer(Modifier.weight(1f))
                            NavigationRailContent(
                                modifier = Modifier,
                                currentTopDestination = currentTopDestination,
                                onNavigateToRoute = onNavigateToRoute,
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    content()
                }
            }
        }
    }
}

@Composable
fun NavigationDrawerSheetContent(
    modifier: Modifier = Modifier,
    currentRoute: String? = null,
    showQuickSettings: Boolean = true,
    onNavigateToRoute: (String) -> Unit,
    onDismissRequest: suspend () -> Unit,
    footer: @Composable (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier =
            modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
    ) {
        Spacer(Modifier.height(16.dp))

        // Sleek Modern Drawer Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Text(
                text = "V-Downloader",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = "v3.2.0",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = "Smart Media Engine",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.download_queue), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                icon = { Icon(Icons.Filled.Download, null) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                onClick = {
                    scope
                        .launch { onDismissRequest() }
                        .invokeOnCompletion { onNavigateToRoute(Route.HOME) }
                },
                selected = currentRoute == Route.HOME,
                colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            Spacer(Modifier.height(4.dp))

            NavigationDrawerItem(
                label = { Text("Social Hub", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                icon = { Icon(Icons.Outlined.Explore, null) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                onClick = {
                    scope
                        .launch { onDismissRequest() }
                        .invokeOnCompletion { onNavigateToRoute(Route.SOCIAL_HUB) }
                },
                selected = currentRoute == Route.SOCIAL_HUB,
                colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            Spacer(Modifier.height(4.dp))

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.downloads_history), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                icon = { Icon(Icons.Outlined.VideoLibrary, null) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                onClick = {
                    scope
                        .launch { onDismissRequest() }
                        .invokeOnCompletion { onNavigateToRoute(Route.DOWNLOADS) }
                },
                selected = currentRoute == Route.DOWNLOADS,
                colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            Spacer(Modifier.height(4.dp))

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.custom_command), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                icon = { Icon(Icons.Outlined.Terminal, null) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                onClick = {
                    scope
                        .launch { onDismissRequest() }
                        .invokeOnCompletion { onNavigateToRoute(Route.TASK_LIST) }
                },
                selected = currentRoute == Route.TASK_LIST,
                colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            Spacer(Modifier.height(4.dp))

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                icon = { Icon(Icons.Outlined.Settings, null) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                onClick = {
                    scope
                        .launch { onDismissRequest() }
                        .invokeOnCompletion { onNavigateToRoute(Route.SETTINGS) }
                },
                selected = currentRoute == Route.SETTINGS_PAGE,
                colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )

            if (showQuickSettings) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.general_settings)) },
                    icon = { Icon(Icons.Rounded.SettingsApplications, null) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    onClick = {
                        scope
                            .launch { onDismissRequest() }
                            .invokeOnCompletion {
                                onNavigateToRoute(Route.SETTINGS)
                                onNavigateToRoute(Route.GENERAL_DOWNLOAD_PREFERENCES)
                            }
                    },
                    selected = currentRoute == Route.GENERAL_DOWNLOAD_PREFERENCES,
                )
                Spacer(Modifier.height(2.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.download_directory)) },
                    icon = { Icon(Icons.Rounded.Folder, null) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    onClick = {
                        scope
                            .launch { onDismissRequest() }
                            .invokeOnCompletion {
                                onNavigateToRoute(Route.SETTINGS)
                                onNavigateToRoute(Route.DOWNLOAD_DIRECTORY)
                            }
                    },
                    selected = currentRoute == Route.DOWNLOAD_DIRECTORY,
                )
                Spacer(Modifier.height(2.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.cookies)) },
                    icon = { Icon(Icons.Rounded.Cookie, null) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    onClick = {
                        scope
                            .launch { onDismissRequest() }
                            .invokeOnCompletion {
                                onNavigateToRoute(Route.SETTINGS)
                                onNavigateToRoute(Route.COOKIE_PROFILE)
                            }
                    },
                    selected = currentRoute == Route.COOKIE_PROFILE,
                )
                Spacer(Modifier.height(2.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.trouble_shooting)) },
                    icon = { Icon(Icons.Rounded.BugReport, null) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    onClick = {
                        scope
                            .launch { onDismissRequest() }
                            .invokeOnCompletion {
                                onNavigateToRoute(Route.SETTINGS)
                                onNavigateToRoute(Route.TROUBLESHOOTING)
                            }
                    },
                    selected = currentRoute == Route.TROUBLESHOOTING,
                )
                Spacer(Modifier.height(2.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.about)) },
                    icon = { Icon(Icons.Rounded.Info, null) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    onClick = {
                        scope
                            .launch { onDismissRequest() }
                            .invokeOnCompletion {
                                onNavigateToRoute(Route.SETTINGS)
                                onNavigateToRoute(Route.ABOUT)
                            }
                    },
                    selected = currentRoute == Route.ABOUT,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
        footer?.invoke()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NavigationRailItemVariant(
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit),
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.large)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent
                )
                .selectable(selected = selected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            icon()
        }
    }
}

@Composable
fun NavigationRailContent(
    modifier: Modifier = Modifier,
    currentTopDestination: String? = null,
    onNavigateToRoute: (String) -> Unit,
) {
    Column(
        modifier = modifier.selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val scope = rememberCoroutineScope()
        NavigationRailItemVariant(
            icon = {
                Icon(
                    if (currentTopDestination == Route.HOME) Icons.Filled.Download
                    else Icons.Outlined.Download,
                    stringResource(R.string.download_queue),
                )
            },
            modifier = Modifier,
            selected = currentTopDestination == Route.HOME,
            onClick = { onNavigateToRoute(Route.HOME) },
        )

        NavigationRailItemVariant(
            icon = {
                Icon(
                    if (currentTopDestination == Route.SOCIAL_HUB) Icons.Filled.Explore
                    else Icons.Outlined.Explore,
                    "Social Hub",
                )
            },
            modifier = Modifier,
            selected = currentTopDestination == Route.SOCIAL_HUB,
            onClick = { onNavigateToRoute(Route.SOCIAL_HUB) },
        )

        NavigationRailItemVariant(
            icon = {
                Icon(
                    if (currentTopDestination == Route.DOWNLOADS) Icons.Filled.Subscriptions
                    else Icons.Outlined.Subscriptions,
                    stringResource(R.string.downloads_history),
                )
            },
            modifier = Modifier,
            selected = currentTopDestination == Route.DOWNLOADS,
            onClick = { onNavigateToRoute(Route.DOWNLOADS) },
        )

        NavigationRailItemVariant(
            icon = {
                Icon(
                    if (currentTopDestination == Route.TASK_LIST) Icons.Filled.Terminal
                    else Icons.Outlined.Terminal,
                    stringResource(R.string.custom_command),
                )
            },
            modifier = Modifier,
            selected = currentTopDestination == Route.TASK_LIST,
            onClick = { onNavigateToRoute(Route.TASK_LIST) },
        )

        NavigationRailItemVariant(
            icon = {
                Icon(
                    if (currentTopDestination == Route.SETTINGS_PAGE) Icons.Filled.Settings
                    else Icons.Outlined.Settings,
                    stringResource(R.string.settings),
                )
            },
            modifier = Modifier,
            selected = currentTopDestination == Route.SETTINGS_PAGE,
            onClick = { onNavigateToRoute(Route.SETTINGS_PAGE) },
        )
    }
}

@Preview(device = "spec:width=673dp,height=841dp")
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun ExpandedPreview() {
    val widthDp = LocalConfiguration.current.screenWidthDp
    var currentRoute = remember { mutableStateOf(Route.HOME) }

    CompositionLocalProvider(
        LocalWindowWidthState provides
            if (widthDp > 480) WindowWidthSizeClass.Expanded
            else if (widthDp > 360) WindowWidthSizeClass.Medium else WindowWidthSizeClass.Compact
    ) {
        Row {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            NavigationDrawer(
                currentRoute = currentRoute.value,
                currentTopDestination = currentRoute.value,
                drawerState = drawerState,
                onNavigateToRoute = { currentRoute.value = it },
                onDismissRequest = {},
            ) {
                DownloadPageImplV2(taskDownloadStateMap = remember { mutableStateMapOf() }) { _, _
                    ->
                }
            }
        }
    }
}
