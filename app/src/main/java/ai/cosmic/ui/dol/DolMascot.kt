package ai.cosmic.ui.dol

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.cosmic.data.dol.DolExpression
import kotlin.math.cos
import kotlin.math.sin

/**
 * COSMIC CODEX mascot: DOL.
 *
 * Vector/Canvas-rendered robot with consistent proportions, white/lavender shell,
 * dark visor, glowing electric-blue eyes, and COSMIC CODEX 'C' chest emblem with orbit arc.
 */
@Composable
fun DolMascot(
    expression: DolExpression,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    val infinite = rememberInfiniteTransition(label = "dol_anim")

    // Natural periodic blinking
    val blinkScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3600
                1f at 0
                1f at 3200
                0.08f at 3320
                1f at 3440
                1f at 3600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dol_blink"
    )

    // Gentle head bob / float
    val bobOffset by infinite.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dol_bob"
    )

    // Subtle eye horizontal scan when focused/thinking
    val eyeScan by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dol_scan"
    )

    // Celebration sparkle rotation
    val sparkleAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dol_sparkle"
    )

    val eyeGlowColor by animateColorAsState(
        targetValue = when (expression) {
            DolExpression.SAD -> Color(0xFF60A5FA)
            DolExpression.ATTENTIVE -> Color(0xFFA78BFA)
            DolExpression.CELEBRATING, DolExpression.HAPPY -> Color(0xFF38BDF8)
            else -> Color(0xFF00D2FF)
        },
        animationSpec = tween(300),
        label = "dol_eye_color"
    )

    val headTilt by animateFloatAsState(
        targetValue = when (expression) {
            DolExpression.THINKING -> -6f
            DolExpression.SAD -> 8f
            DolExpression.ATTENTIVE -> -3f
            DolExpression.CELEBRATING -> 4f
            else -> 0f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dol_tilt"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bob = if (expression == DolExpression.SAD) 2f else bobOffset

        val shellWhite = Color(0xFFF3F0FF)
        val shellShadow = Color(0xFFD8D0F0)
        val visorDark = Color(0xFF0B0F19)
        val purpleAccent = Color(0xFF7C3AED)
        val goldStar = Color(0xFFFBBF24)

        // Celebration sparkles
        if (expression == DolExpression.CELEBRATING || expression == DolExpression.HAPPY) {
            val count = if (expression == DolExpression.CELEBRATING) 6 else 3
            for (i in 0 until count) {
                val angleRad = Math.toRadians((sparkleAngle + i * (360f / count)).toDouble())
                val rx = w * 0.44f
                val ry = h * 0.42f
                val sx = w * 0.5f + (rx * cos(angleRad)).toFloat()
                val sy = h * 0.45f + (ry * sin(angleRad)).toFloat()
                drawCircle(
                    color = if (i % 2 == 0) goldStar else eyeGlowColor,
                    radius = w * 0.032f,
                    center = Offset(sx, sy)
                )
            }
        }

        // Body (torso)
        val bodyW = w * 0.42f
        val bodyH = h * 0.26f
        val bodyLeft = (w - bodyW) / 2f
        val bodyTop = h * 0.62f + bob * 0.5f
        drawRoundRect(
            color = shellShadow,
            topLeft = Offset(bodyLeft, bodyTop + h * 0.015f),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        drawRoundRect(
            color = shellWhite,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )

        // Chest 'C' COSMIC CODEX emblem — arc with orbit ring
        // Main "C" arc (270° sweep leaving a gap on the right)
        drawArc(
            color = Color(0xFF4A90E2),
            startAngle = 50f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(w * 0.40f, bodyTop + bodyH * 0.14f),
            size = Size(bodyW * 0.52f, bodyH * 0.58f),
            style = Stroke(width = w * 0.030f, cap = StrokeCap.Round)
        )
        // Orbit ring — thin diagonal ellipse across the C emblem
        drawOval(
            color = purpleAccent.copy(alpha = 0.72f),
            topLeft = Offset(w * 0.37f, bodyTop + bodyH * 0.26f),
            size = Size(bodyW * 0.60f, bodyH * 0.22f),
            style = Stroke(width = w * 0.016f)
        )
        // Central 4-point star glyph (two lines crossing)
        val starCx = w * 0.50f
        val starCy = bodyTop + bodyH * 0.44f
        val starR = w * 0.038f
        val starPath = Path().apply {
            moveTo(starCx, starCy - starR)
            lineTo(starCx, starCy + starR)
            moveTo(starCx - starR, starCy)
            lineTo(starCx + starR, starCy)
            moveTo(starCx - starR * 0.6f, starCy - starR * 0.6f)
            lineTo(starCx + starR * 0.6f, starCy + starR * 0.6f)
            moveTo(starCx + starR * 0.6f, starCy - starR * 0.6f)
            lineTo(starCx - starR * 0.6f, starCy + starR * 0.6f)
        }
        drawPath(
            path = starPath,
            color = Color(0xFFE0D0FF),
            style = Stroke(width = w * 0.018f, cap = StrokeCap.Round)
        )

        // Head group with tilt & bob
        rotate(degrees = headTilt, pivot = Offset(w * 0.5f, h * 0.38f + bob)) {
            val headW = w * 0.66f
            val headH = h * 0.46f
            val headLeft = (w - headW) / 2f
            val headTop = h * 0.16f + bob

            // Top antenna loop
            drawRoundRect(
                color = purpleAccent,
                topLeft = Offset(w * 0.43f, headTop - h * 0.06f),
                size = Size(w * 0.14f, h * 0.08f),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                style = Stroke(width = w * 0.028f)
            )

            // Side headphone/ears
            drawCircle(
                color = purpleAccent,
                radius = w * 0.075f,
                center = Offset(headLeft, headTop + headH * 0.52f)
            )
            drawCircle(
                color = purpleAccent,
                radius = w * 0.075f,
                center = Offset(headLeft + headW, headTop + headH * 0.52f)
            )

            // Helmet shell
            drawRoundRect(
                color = shellShadow,
                topLeft = Offset(headLeft, headTop + h * 0.018f),
                size = Size(headW, headH),
                cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
            )
            drawRoundRect(
                color = shellWhite,
                topLeft = Offset(headLeft, headTop),
                size = Size(headW, headH),
                cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
            )

            // Dark Visor
            val visorW = headW * 0.78f
            val visorH = headH * 0.68f
            val visorLeft = (w - visorW) / 2f
            val visorTop = headTop + headH * 0.18f
            drawRoundRect(
                color = visorDark,
                topLeft = Offset(visorLeft, visorTop),
                size = Size(visorW, visorH),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
            )

            // Eyes
            val eyeShiftX = if (expression == DolExpression.FOCUSED || expression == DolExpression.THINKING) {
                eyeScan * (visorW * 0.05f)
            } else 0f
            val leftEyeX = visorLeft + visorW * 0.32f + eyeShiftX
            val rightEyeX = visorLeft + visorW * 0.68f + eyeShiftX
            val eyeY = visorTop + visorH * 0.44f
            val eyeRadius = w * 0.052f

            when (expression) {
                DolExpression.HAPPY, DolExpression.CELEBRATING, DolExpression.WELCOME -> {
                    // Happy ^ ^ eyes
                    val stroke = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
                    val leftArc = Path().apply {
                        moveTo(leftEyeX - eyeRadius, eyeY + eyeRadius * 0.3f)
                        quadraticTo(leftEyeX, eyeY - eyeRadius * 0.8f, leftEyeX + eyeRadius, eyeY + eyeRadius * 0.3f)
                    }
                    val rightArc = Path().apply {
                        moveTo(rightEyeX - eyeRadius, eyeY + eyeRadius * 0.3f)
                        quadraticTo(rightEyeX, eyeY - eyeRadius * 0.8f, rightEyeX + eyeRadius, eyeY + eyeRadius * 0.3f)
                    }
                    drawPath(leftArc, eyeGlowColor, style = stroke)
                    drawPath(rightArc, eyeGlowColor, style = stroke)
                }
                DolExpression.SAD -> {
                    // Drooping sad eyes
                    val stroke = Stroke(width = w * 0.03f, cap = StrokeCap.Round)
                    drawLine(
                        color = eyeGlowColor,
                        start = Offset(leftEyeX - eyeRadius, eyeY - eyeRadius * 0.2f),
                        end = Offset(leftEyeX + eyeRadius * 0.8f, eyeY + eyeRadius * 0.3f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = eyeGlowColor,
                        start = Offset(rightEyeX - eyeRadius * 0.8f, eyeY + eyeRadius * 0.3f),
                        end = Offset(rightEyeX + eyeRadius, eyeY - eyeRadius * 0.2f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
                else -> {
                    // Glowing rounded eyes with natural blink
                    val eh = (eyeRadius * 1.4f * blinkScale).coerceAtLeast(2f)
                    drawOval(
                        color = eyeGlowColor,
                        topLeft = Offset(leftEyeX - eyeRadius * 0.75f, eyeY - eh / 2f),
                        size = Size(eyeRadius * 1.5f, eh)
                    )
                    drawOval(
                        color = eyeGlowColor,
                        topLeft = Offset(rightEyeX - eyeRadius * 0.75f, eyeY - eh / 2f),
                        size = Size(eyeRadius * 1.5f, eh)
                    )
                }
            }

            // Mouth
            val mouthY = visorTop + visorH * 0.74f
            if (expression == DolExpression.HAPPY || expression == DolExpression.CELEBRATING || expression == DolExpression.WELCOME) {
                val smile = Path().apply {
                    moveTo(w * 0.44f, mouthY - h * 0.01f)
                    quadraticTo(w * 0.5f, mouthY + h * 0.045f, w * 0.56f, mouthY - h * 0.01f)
                }
                drawPath(smile, eyeGlowColor, style = Stroke(width = w * 0.025f, cap = StrokeCap.Round))
            } else if (expression == DolExpression.SAD) {
                val frown = Path().apply {
                    moveTo(w * 0.45f, mouthY + h * 0.02f)
                    quadraticTo(w * 0.5f, mouthY - h * 0.02f, w * 0.55f, mouthY + h * 0.02f)
                }
                drawPath(frown, eyeGlowColor, style = Stroke(width = w * 0.022f, cap = StrokeCap.Round))
            }
        }
    }
}
