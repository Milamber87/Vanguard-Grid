package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.CloudSyncRepository
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOutFromUec
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SlateVoid
import com.example.ui.theme.SteelElevated
import com.example.ui.theme.SteelHull
import com.example.viewmodel.VanguardViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class VanguardTab(val labelRes: Int, val icon: ImageVector, val tag: String) {
    FACTORY(R.string.nav_factory, Icons.Rounded.GridView, "nav_tab_factory"),
    UNITS(R.string.nav_units, Icons.Rounded.Shield, "nav_tab_units"),
    PLANETS(R.string.nav_planets, Icons.Rounded.Public, "nav_tab_planets"),
    TECH(R.string.nav_tech, Icons.Rounded.AccountTree, "nav_tab_tech"),
    OPS(R.string.nav_ops, Icons.Rounded.Hub, "nav_tab_ops")
}

@Composable
fun VanguardApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            VanguardAuthenticatedShell(
                currentUserId = user.uid,
                displayName = user.displayName ?: "Commander"
            )
        }
    }
}

@Composable
private fun VanguardAuthenticatedShell(
    currentUserId: String,
    displayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: VanguardViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VanguardViewModel(
                    repository = CloudSyncRepository(db),
                    currentUserId = currentUserId,
                    defaultCallsign = displayName
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(VanguardTab.FACTORY) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusToast) {
        val msg = uiState.statusToast
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // BackHandler returns from secondary screens to primary Factory Grid
    BackHandler(enabled = activeTab != VanguardTab.FACTORY) {
        activeTab = VanguardTab.FACTORY
    }

    val hasUrgentEvent = uiState.activeDynamicEvent != null
    val hasActiveHostiles = uiState.activePlanet.units.any { !it.isPlayer }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateVoid)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                NavigationRail(
                    containerColor = SteelHull,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    VanguardTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = {
                                TabIconWithBadge(
                                    tab = tab,
                                    showBadge = (tab == VanguardTab.OPS && hasUrgentEvent) ||
                                        (tab == VanguardTab.UNITS && hasActiveHostiles)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color(0xFF0E141B),
                                selectedTextColor = SafetyAmber,
                                indicatorColor = SafetyAmber,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Scaffold(
                        containerColor = SlateVoid,
                        snackbarHost = {
                            SnackbarHost(snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = SteelElevated,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            ActiveTabContent(
                                activeTab = activeTab,
                                uiState = uiState,
                                viewModel = viewModel,
                                onSelectTab = { activeTab = it },
                                onSignOut = { signOutFromUec(credentialManager, scope) }
                            )
                        }
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = SlateVoid,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = SteelElevated,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = SteelHull,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        VanguardTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        showBadge = (tab == VanguardTab.OPS && hasUrgentEvent) ||
                                            (tab == VanguardTab.UNITS && hasActiveHostiles)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF0E141B),
                                    selectedTextColor = SafetyAmber,
                                    indicatorColor = SafetyAmber,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(innerPadding)
                ) {
                    ActiveTabContent(
                        activeTab = activeTab,
                        uiState = uiState,
                        viewModel = viewModel,
                        onSelectTab = { activeTab = it },
                        onSignOut = { signOutFromUec(credentialManager, scope) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(tab: VanguardTab, showBadge: Boolean) {
    if (showBadge) {
        BadgedBox(badge = { Badge(containerColor = HazardCrimson) }) {
            Icon(imageVector = tab.icon, contentDescription = stringResource(tab.labelRes))
        }
    } else {
        Icon(imageVector = tab.icon, contentDescription = stringResource(tab.labelRes))
    }
}

@Composable
private fun ActiveTabContent(
    activeTab: VanguardTab,
    uiState: com.example.viewmodel.VanguardUiState,
    viewModel: VanguardViewModel,
    onSelectTab: (VanguardTab) -> Unit,
    onSignOut: () -> Unit
) {
    when (activeTab) {
        VanguardTab.FACTORY -> FactoryScreen(
            uiState = uiState,
            viewModel = viewModel,
            onOpenEventsTab = { onSelectTab(VanguardTab.OPS) },
            onOpenTechTab = { onSelectTab(VanguardTab.TECH) }
        )
        VanguardTab.UNITS -> UnitsScreen(
            uiState = uiState,
            viewModel = viewModel,
            onJumpToGrid = { onSelectTab(VanguardTab.FACTORY) }
        )
        VanguardTab.PLANETS -> PlanetsScreen(
            uiState = uiState,
            onDeployToPlanet = { planetId ->
                viewModel.switchPlanet(planetId)
                onSelectTab(VanguardTab.FACTORY)
            }
        )
        VanguardTab.TECH -> TechTreeScreen(
            uiState = uiState,
            onUnlockTech = { viewModel.unlockTech(it) }
        )
        VanguardTab.OPS -> OperationsScreen(
            uiState = uiState,
            viewModel = viewModel,
            onSignOutClick = onSignOut
        )
    }
}
