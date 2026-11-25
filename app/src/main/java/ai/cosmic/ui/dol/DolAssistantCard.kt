package ai.cosmic.ui.dol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.cosmic.data.dol.DolCoordinator
import ai.cosmic.data.dol.DolExpression
import ai.cosmic.theme.AppColors

/**
 * Reusable DOL assistant status card for Home, Missions, and Agent workspaces.
 */
@Composable
fun DolAssistantCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val dolState by DolCoordinator.uiState.collectAsState()
    val accent = when (dolState.expression) {
        DolExpression.HAPPY, DolExpression.CELEBRATING -> AppColors.success
        DolExpression.SAD -> AppColors.danger
        DolExpression.ATTENTIVE -> AppColors.warning
        else -> Color(0xFF7C3AED)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppColors.card,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DolMascot(expression = dolState.expression, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DOL",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    if (!dolState.activeTaskTitle.isNullOrBlank()) {
                        Text(
                            text = "· ${dolState.activeTaskTitle}",
                            fontSize = 11.5.sp,
                            color = AppColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = dolState.displayMessage,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = AppColors.onBackground
                )
            }
        }
    }
}
