package ai.cosmic.ui.agents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.cosmic.data.agent.adapter.AgentAdapterRegistry
import ai.cosmic.data.agent.adapter.AgentDescriptor
import ai.cosmic.data.agent.adapter.AgentInstallStatus
import ai.cosmic.domain.model.AgentKind
import ai.cosmic.theme.AppColors
import ai.cosmic.ui.components.bouncyVerticalScroll
import kotlinx.coroutines.launch

/**
 * Dedicated Agent Management Hub.
 * Displays real installation, online, auth, version, and capability status
 * for every registered agent adapter without faking compatibility.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentsHubScreen(
    onSwitchToAgentChat: (AgentKind) -> Unit,
    onOpenAgentSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AgentAdapterRegistry.ensureInitialized(context)
        AgentAdapterRegistry.refreshAll(context)
    }

    val descriptors by AgentAdapterRegistry.descriptors.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppColors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .bouncyVerticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Agents",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.onBackground
                    )
                    Text(
                        text = "Real-time adapter status, capabilities & runtime detection",
                        fontSize = 12.5.sp,
                        color = AppColors.textSecondary
                    )
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            refreshing = true
                            AgentAdapterRegistry.refreshAll(context)
                            refreshing = false
                        }
                    },
                    enabled = !refreshing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (refreshing) "Scanning…" else "Scan", fontSize = 12.5.sp)
                }
            }

            descriptors.forEach { agent ->
                AgentAdapterCard(
                    agent = agent,
                    onChat = {
                        agent.chatKind?.let { onSwitchToAgentChat(it) }
                    },
                    onConfigure = onOpenAgentSettings
                )
            }

            Spacer(Modifier.height(72.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentAdapterCard(
    agent: AgentDescriptor,
    onChat: () -> Unit,
    onConfigure: () -> Unit
) {
    val statusColor = when (agent.installStatus) {
        AgentInstallStatus.INSTALLED -> if (agent.isOnline) AppColors.success else AppColors.info
        AgentInstallStatus.UNSUPPORTED_ON_ANDROID -> AppColors.textSecondary
        AgentInstallStatus.NOT_INSTALLED -> AppColors.warning
        AgentInstallStatus.CHECKING -> AppColors.textSecondary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.card,
        border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = agent.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.onBackground
                        )
                        Text(
                            text = agent.vendor,
                            fontSize = 11.5.sp,
                            color = AppColors.textSecondary
                        )
                    }
                    Text(
                        text = agent.runtimeType.label + (agent.version?.let { " · $it" } ?: ""),
                        fontSize = 11.5.sp,
                        color = AppColors.textSecondary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (agent.installStatus == AgentInstallStatus.INSTALLED && agent.isOnline) "Online"
                        else agent.installStatus.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            Text(
                text = agent.description,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = AppColors.textSecondary
            )

            if (agent.capabilities.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    agent.capabilities.take(6).forEach { cap ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.fill
                        ) {
                            Text(
                                text = cap.label,
                                fontSize = 11.sp,
                                color = AppColors.onBackground.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (agent.installStatus != AgentInstallStatus.INSTALLED && !agent.installHint.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.fill.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = agent.installHint,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onConfigure) {
                    Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Configure", fontSize = 12.5.sp)
                }
                if (agent.chatKind != null && agent.installStatus == AgentInstallStatus.INSTALLED) {
                    Spacer(Modifier.width(6.dp))
                    FilledTonalButton(
                        onClick = onChat,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Outlined.Chat, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Chat", fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
