package ai.cosmic.ui.missions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import ai.cosmic.data.local.LocalEnvironmentManager
import ai.cosmic.data.orchestration.AutonomousTask
import ai.cosmic.data.orchestration.AutonomousTaskEngine
import ai.cosmic.data.orchestration.TaskStatus
import ai.cosmic.theme.AppColors
import ai.cosmic.ui.components.bouncyVerticalScroll
import kotlinx.coroutines.launch

/**
 * Autonomous Task Engine & Multi-Agent Orchestration Screen.
 */
@Composable
fun MissionsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        AutonomousTaskEngine.init(context)
    }

    val tasks by AutonomousTaskEngine.tasks.collectAsState()
    val activeLocks by AutonomousTaskEngine.activeLocks.collectAsState()

    var goalInput by remember { mutableStateOf("") }
    var workspaceInput by remember {
        mutableStateOf(LocalEnvironmentManager.getProjectsDir(context).absolutePath)
    }
    var creating by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Autonomous Missions",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.onBackground
            )
            Text(
                text = "Plan, delegate across agents, track progress, and verify results.",
                fontSize = 12.5.sp,
                color = AppColors.textSecondary
            )

            ai.cosmic.ui.dol.DolAssistantCard()

            // Mission Composer Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = AppColors.card,
                border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        label = { Text("Mission Goal") },
                        placeholder = { Text("e.g. Create a responsive e-commerce storefront and verify in browser") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = workspaceInput,
                        onValueChange = { workspaceInput = it },
                        label = { Text("Workspace Directory") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (goalInput.isNotBlank()) {
                                scope.launch {
                                    creating = true
                                    val task = AutonomousTaskEngine.createTask(context, goalInput, workspaceInput)
                                    AutonomousTaskEngine.startTask(context, task.id)
                                    goalInput = ""
                                    creating = false
                                }
                            }
                        },
                        enabled = goalInput.isNotBlank() && !creating,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.RocketLaunch, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (creating) "Planning Mission…" else "Launch Mission")
                    }
                }
            }

            if (activeLocks.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppColors.info.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AppColors.info.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${activeLocks.size} active file lock(s) preventing concurrent edit conflicts",
                        fontSize = 12.sp,
                        color = AppColors.info,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (tasks.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.card.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "No missions yet. Describe a goal above to orchestrate planning, implementation, and verification.",
                        fontSize = 13.sp,
                        color = AppColors.textSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                tasks.forEach { task ->
                    MissionCard(
                        task = task,
                        onStart = { AutonomousTaskEngine.startTask(context, task.id) },
                        onPause = { AutonomousTaskEngine.pauseTask(context, task.id) },
                        onCancel = { AutonomousTaskEngine.cancelTask(context, task.id) }
                    )
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun MissionCard(
    task: AutonomousTask,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    var expanded by remember { mutableStateOf(task.status == TaskStatus.RUNNING) }
    val statusColor = when (task.status) {
        TaskStatus.COMPLETED -> AppColors.success
        TaskStatus.FAILED, TaskStatus.CANCELLED -> AppColors.danger
        TaskStatus.RUNNING -> AppColors.info
        TaskStatus.AWAITING_APPROVAL, TaskStatus.PAUSED -> AppColors.warning
        else -> AppColors.textSecondary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.card,
        border = BorderStroke(1.dp, AppColors.border.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.onBackground
                    )
                    Text(
                        text = "${task.projectPath} · ${task.progressPercent}%",
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
                    Text(task.status.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                }
            }

            LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = statusColor,
                trackColor = AppColors.fill
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.steps.forEach { step ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AppColors.fill.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${step.index}. ${step.title}",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.onBackground
                                    )
                                    Text(
                                        text = "${step.role.label} (${step.assignedAgentId})",
                                        fontSize = 11.sp,
                                        color = AppColors.primary
                                    )
                                }
                                Text(
                                    text = "Status: ${step.status.label}",
                                    fontSize = 11.sp,
                                    color = AppColors.textSecondary
                                )
                                if (!step.error.isNullOrBlank()) {
                                    Text(
                                        text = step.error,
                                        fontSize = 11.5.sp,
                                        color = AppColors.danger,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    if (!task.verificationSummary.isNullOrBlank()) {
                        Text(
                            text = if (task.verified) "Verified: ${task.verificationSummary}" else task.verificationSummary,
                            fontSize = 12.sp,
                            color = if (task.verified) AppColors.success else AppColors.warning
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == TaskStatus.PAUSED || task.status == TaskStatus.PENDING) {
                    TextButton(onClick = onStart) {
                        Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resume", fontSize = 12.5.sp)
                    }
                }
                if (task.status == TaskStatus.RUNNING) {
                    TextButton(onClick = onPause) {
                        Icon(Icons.Outlined.Pause, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause", fontSize = 12.5.sp)
                    }
                }
                if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PAUSED || task.status == TaskStatus.AWAITING_APPROVAL) {
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(16.dp), tint = AppColors.danger)
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", fontSize = 12.5.sp, color = AppColors.danger)
                    }
                }
            }
        }
    }
}
