package ai.cosmic.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.cosmic.data.agent.adapter.AgentAdapterRegistry
import ai.cosmic.data.agent.adapter.AgentInstallStatus
import ai.cosmic.data.local.LocalEnvironmentManager
import ai.cosmic.data.orchestration.AutonomousTaskEngine
import ai.cosmic.data.orchestration.TaskStatus
import ai.cosmic.data.storage.PermissionManager
import ai.cosmic.data.storage.StorageGateway
import ai.cosmic.domain.model.Conversation
import ai.cosmic.theme.AppColors
import ai.cosmic.theme.BrandConfig
import ai.cosmic.ui.components.bouncyVerticalScroll
import java.io.File

/**
 * Primary Home Dashboard for the unified AI Agent Workspace.
 */
@Composable
fun WorkspaceHomeScreen(
    conversations: List<Conversation>,
    onOpenChats: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        AgentAdapterRegistry.ensureInitialized(context)
        AgentAdapterRegistry.refreshAll(context)
        AutonomousTaskEngine.init(context)
        PermissionManager.refresh(context)
    }

    val descriptors by AgentAdapterRegistry.descriptors.collectAsState()
    val tasks by AutonomousTaskEngine.tasks.collectAsState()
    val permState by PermissionManager.state.collectAsState()
    val storageState by StorageGateway.accessState.collectAsState()

    val recentProjects = remember(storageState) {
        val projectsDir = LocalEnvironmentManager.getProjectsDir(context)
        if (projectsDir.exists() && projectsDir.isDirectory) {
            projectsDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(8)
                ?: emptyList()
        } else emptyList()
    }

    val installedAgents = descriptors.filter { it.installStatus == AgentInstallStatus.INSTALLED }
    val activeTasks = tasks.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.AWAITING_APPROVAL }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Welcome Header — COSMIC CODEX branding
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(ai.cosmic.R.drawable.ic_cosmic_codex_logo),
                        contentDescription = "Cosmic Codex",
                        modifier = Modifier
                            .height(40.dp)
                            .width(40.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Column {
                        Text(
                            text = BrandConfig.productName,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.onBackground
                        )
                        Text(
                            text = BrandConfig.tagline,
                            fontSize = 12.5.sp,
                            color = AppColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = AppColors.onBackground)
                }
            }

            // DOL AI Mascot Assistant Card
            ai.cosmic.ui.dol.DolAssistantCard(onClick = onOpenMissions)

            // Permission / Storage Revocation Alert Banner (only if not granted)
            AnimatedVisibility(
                visible = !storageState.hasSharedStorageAccess,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPermissions),
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.warning.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AppColors.warning.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.FolderOff, null, tint = AppColors.warning, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shared Storage Access Needed",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.onBackground
                            )
                            Text(
                                text = permState.storageExplanation,
                                fontSize = 11.5.sp,
                                color = AppColors.textSecondary
                            )
                        }
                        Text("Grant", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AppColors.warning)
                    }
                }
            }

            // Quick Actions Grid
            Text(
                text = "Quick Start",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.textSecondary
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    QuickActionCard(Icons.Outlined.AddComment, "New Chat", AppColors.primary, onNewChat)
                }
                item {
                    QuickActionCard(Icons.Outlined.AutoAwesome, "New Mission", AppColors.accentAlt, onOpenMissions)
                }
                item {
                    QuickActionCard(Icons.Outlined.Code, "Projects IDE", AppColors.info, onOpenProjects)
                }
                item {
                    QuickActionCard(Icons.Outlined.Language, "Browser", AppColors.success, onOpenBrowser)
                }
                item {
                    QuickActionCard(Icons.Outlined.Terminal, "Terminal", AppColors.warning, onOpenTerminal)
                }
            }

            // Active Agents Strip
            SectionHeader(
                title = "Agents (${installedAgents.size}/${descriptors.size} ready)",
                actionLabel = "Manage",
                onAction = onOpenAgents
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(descriptors.take(6), key = { it.id }) { agent ->
                    Surface(
                        modifier = Modifier
                            .width(155.dp)
                            .clickable(onClick = onOpenAgents),
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.card,
                        border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (agent.isOnline) AppColors.success else AppColors.textSecondary.copy(alpha = 0.4f))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = agent.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = agent.installStatus.label,
                                fontSize = 11.5.sp,
                                color = if (agent.installStatus == AgentInstallStatus.INSTALLED) AppColors.success else AppColors.textSecondary
                            )
                        }
                    }
                }
            }

            // Active Missions
            SectionHeader(
                title = "Missions (${activeTasks.size} active)",
                actionLabel = "View All",
                onAction = onOpenMissions
            )
            if (tasks.isEmpty()) {
                EmptySectionCard("No autonomous missions yet. Launch a multi-agent mission to plan, code, and verify.")
            } else {
                tasks.take(2).forEach { task ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenMissions),
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.card,
                        border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = task.status.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = when (task.status) {
                                        TaskStatus.COMPLETED -> AppColors.success
                                        TaskStatus.FAILED -> AppColors.danger
                                        TaskStatus.RUNNING -> AppColors.info
                                        else -> AppColors.warning
                                    }
                                )
                            }
                            LinearProgressIndicator(
                                progress = { task.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = AppColors.primary,
                                trackColor = AppColors.fill
                            )
                        }
                    }
                }
            }

            // Recent Projects
            SectionHeader(
                title = "Recent Projects",
                actionLabel = "Open IDE",
                onAction = onOpenProjects
            )
            if (recentProjects.isEmpty()) {
                EmptySectionCard("No projects in ~/projects yet. Open Projects IDE to create one.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentProjects, key = { it.absolutePath }) { dir ->
                        ProjectMiniCard(dir = dir, onClick = onOpenProjects)
                    }
                }
            }

            // Recent Conversations
            SectionHeader(
                title = "Recent Conversations",
                actionLabel = "All Chats",
                onAction = onOpenChats
            )
            if (conversations.isEmpty()) {
                EmptySectionCard("No conversations yet. Tap New Chat to start coding with an agent.")
            } else {
                conversations.take(4).forEach { conv ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectConversation(conv.id)
                                onOpenChats()
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.card,
                        border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = AppColors.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title.ifBlank { "Untitled Chat" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AppColors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = conv.agent.name,
                                    fontSize = 11.5.sp,
                                    color = AppColors.textSecondary
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(106.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.card,
        border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProjectMiniCard(dir: File, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.card,
        border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.Folder, null, tint = AppColors.info, modifier = Modifier.size(20.dp))
            Text(
                text = dir.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "~/projects/${dir.name}",
                fontSize = 11.sp,
                color = AppColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.onBackground
        )
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            Text(actionLabel, fontSize = 12.5.sp, color = AppColors.primary)
        }
    }
}

@Composable
private fun EmptySectionCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.card.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            color = AppColors.textSecondary,
            modifier = Modifier.padding(14.dp)
        )
    }
}
