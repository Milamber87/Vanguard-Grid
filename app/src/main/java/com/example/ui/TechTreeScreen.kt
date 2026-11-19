package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ResourceType
import com.example.data.model.TechNode
import com.example.data.model.TechTreeCatalog
import com.example.ui.theme.HazardCrimson
import com.example.ui.theme.PlasmaCyan
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SlateVoid
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.SteelHull
import com.example.ui.theme.SteelPanel
import com.example.ui.theme.TelemetryEmerald
import com.example.viewmodel.VanguardUiState

@Composable
fun TechTreeScreen(
    uiState: VanguardUiState,
    onUnlockTech: (String) -> Unit
) {
    val unlockedSet = uiState.unlockedTechSet
    val inventory = uiState.activePlanet.inventory
    val groupedByTier = remember { TechTreeCatalog.nodes.groupBy { it.tier }.toSortedMap() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateVoid),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Surface(
                color = SteelHull,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UEC RESEARCH & TECH TREE",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Unlocked ${unlockedSet.size}/${TechTreeCatalog.nodes.size} nodes • Synced across all planets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = SafetyAmber.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "${uiState.profile.uecCredits} CR",
                            style = MaterialTheme.typography.labelLarge,
                            color = SafetyAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        groupedByTier.forEach { (tier, nodes) ->
            item(key = "tier_header_$tier") {
                TierHeader(
                    tier = tier,
                    unlockedCount = nodes.count { it.id in unlockedSet },
                    totalCount = nodes.size,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                )
            }

            items(nodes, key = { it.id }) { node ->
                TechNodeCard(
                    node = node,
                    unlockedSet = unlockedSet,
                    inventory = inventory,
                    uecCredits = uiState.profile.uecCredits,
                    onResearch = { onUnlockTech(node.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                )
            }
        }
    }
}

@Composable
private fun TierHeader(
    tier: Int,
    unlockedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val tierTitle = when (tier) {
        1 -> "TIER 1 // EXTRACTION & METALLURGY"
        2 -> "TIER 2 // ASSEMBLY & HAZARD HARDENING"
        3 -> "TIER 3 // MK.2 LOGISTICS, GEOTHERMAL & DEFENSE"
        4 -> "TIER 4 // PLANETARY COLONIZATION & FUSION"
        else -> "TIER 5 // QUANTUM MANUFACTURING & VANGUARD FLEET"
    }
    Row(
        modifier = modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.AccountTree,
                contentDescription = null,
                tint = PlasmaCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tierTitle,
                style = MaterialTheme.typography.labelLarge,
                color = PlasmaCyan
            )
        }
        Text(
            text = "$unlockedCount/$totalCount",
            style = MaterialTheme.typography.labelSmall,
            color = TelemetryEmerald
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechNodeCard(
    node: TechNode,
    unlockedSet: Set<String>,
    inventory: Map<ResourceType, Int>,
    uecCredits: Long,
    onResearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnlocked = node.id in unlockedSet
    val prereqsMet = node.prerequisites.all { it in unlockedSet }
    val hasCredits = uecCredits >= node.creditCost
    val hasResources = node.resourceCosts.all { (res, qty) -> (inventory[res] ?: 0) >= qty }
    val canResearch = !isUnlocked && prereqsMet && hasCredits && hasResources

    val borderColor = when {
        isUnlocked -> TelemetryEmerald
        canResearch -> SafetyAmber
        prereqsMet -> PlasmaCyan.copy(alpha = 0.55f)
        else -> SteelBorder
    }

    Surface(
        color = SteelHull,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (canResearch || isUnlocked) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.testTag("tech_node_${node.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.branch,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUnlocked) TelemetryEmerald else SafetyAmber
                    )
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isUnlocked) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Unlocked",
                        tint = TelemetryEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (!prereqsMet) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Prerequisites locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = node.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Prerequisites Row
            if (node.prerequisites.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    node.prerequisites.forEach { prereqId ->
                        val met = prereqId in unlockedSet
                        val title = TechTreeCatalog.byId(prereqId)?.title ?: prereqId
                        Surface(
                            color = if (met) TelemetryEmerald.copy(alpha = 0.14f) else SteelPanel,
                            shape = MaterialTheme.shapes.extraSmall,
                            border = BorderStroke(1.dp, if (met) TelemetryEmerald.copy(alpha = 0.6f) else SteelBorder)
                        ) {
                            Text(
                                text = "REQ: $title",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (met) TelemetryEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Unlockable Buildings / Recipes / Global Bonuses
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                node.unlocksBuildings.forEach { b ->
                    Surface(
                        color = b.accentColor.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, b.accentColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = "+ ${b.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = b.accentColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                node.unlocksRecipes.forEach { r ->
                    Surface(
                        color = PlasmaCyan.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, PlasmaCyan.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "RECIPE: $r",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlasmaCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                node.globalBonusText?.let { bonus ->
                    Surface(
                        color = SafetyAmber.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "BONUS: $bonus",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafetyAmber,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Costs & Research Action Button
            if (!isUnlocked) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (node.creditCost > 0) {
                        CostChip(
                            label = "${node.creditCost} CR",
                            affordable = hasCredits
                        )
                    }
                    node.resourceCosts.forEach { (res, qty) ->
                        val held = inventory[res] ?: 0
                        CostChip(
                            label = "${res.code} $held/$qty",
                            affordable = held >= qty
                        )
                    }
                }

                Button(
                    onClick = onResearch,
                    enabled = canResearch,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyAmber,
                        contentColor = Color(0xFF0E141B),
                        disabledContainerColor = SteelPanel,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("research_btn_${node.id}")
                ) {
                    Icon(
                        imageVector = if (prereqsMet) Icons.Rounded.Science else Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !prereqsMet -> stringResource(R.string.locked_prereq).uppercase()
                            canResearch -> "${stringResource(R.string.research_unlock).uppercase()} ${node.title.uppercase()}"
                            else -> "INSUFFICIENT RESOURCES / CREDITS"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CostChip(label: String, affordable: Boolean) {
    val color = if (affordable) TelemetryEmerald else HazardCrimson
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
