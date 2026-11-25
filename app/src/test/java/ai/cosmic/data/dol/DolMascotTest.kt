package ai.cosmic.data.dol

import ai.cosmic.data.orchestration.AutonomousTask
import ai.cosmic.data.orchestration.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DolMascotTest {

    @Before
    fun setUp() {
        DolVoiceManager.resetDeduplicationForTest()
    }

    @Test
    fun taskEventsMapToExpectedExpressionsAndMessages() {
        assertEquals(DolExpression.WELCOME, DolTaskMapper.mapEvent(DolTaskEvent.TASK_CREATED).expression)

        val running = DolTaskMapper.mapEvent(DolTaskEvent.TASK_RUNNING)
        assertEquals(DolExpression.FOCUSED, running.expression)
        assertEquals(DolTaskMapper.MSG_RUNNING_DISPLAY, running.displayMessage)
        assertEquals(DolTaskMapper.MSG_RUNNING_VOICE, running.voiceMessage)

        assertEquals(DolExpression.THINKING, DolTaskMapper.mapEvent(DolTaskEvent.TASK_PROGRESS).expression)

        val approval = DolTaskMapper.mapEvent(DolTaskEvent.TASK_WAITING_FOR_APPROVAL)
        assertEquals(DolExpression.ATTENTIVE, approval.expression)
        assertEquals(DolTaskMapper.MSG_APPROVAL_DISPLAY, approval.displayMessage)
        assertEquals(DolTaskMapper.MSG_APPROVAL_VOICE, approval.voiceMessage)

        val completed = DolTaskMapper.mapEvent(DolTaskEvent.TASK_COMPLETED, isMajorCompletion = true)
        assertEquals(DolExpression.CELEBRATING, completed.expression)
        assertEquals(DolTaskMapper.MSG_COMPLETED_DISPLAY, completed.displayMessage)
        assertEquals(DolTaskMapper.MSG_COMPLETED_VOICE, completed.voiceMessage)

        val failed = DolTaskMapper.mapEvent(DolTaskEvent.TASK_FAILED)
        assertEquals(DolExpression.SAD, failed.expression)
        assertEquals(DolTaskMapper.MSG_FAILED_DISPLAY, failed.displayMessage)
        assertEquals(DolTaskMapper.MSG_FAILED_VOICE, failed.voiceMessage)

        assertEquals(DolExpression.IDLE, DolTaskMapper.mapEvent(DolTaskEvent.TASK_CANCELLED).expression)
    }

    @Test
    fun unverifiedCompletedTaskDoesNotTriggerSuccess() {
        val unverified = AutonomousTask(
            id = "t1",
            title = "Build",
            goal = "Build",
            projectPath = "/tmp/p",
            status = TaskStatus.COMPLETED,
            verified = false
        )
        assertEquals(DolTaskEvent.TASK_PROGRESS, DolTaskMapper.eventForTask(unverified))

        val verified = unverified.copy(verified = true)
        assertEquals(DolTaskEvent.TASK_COMPLETED, DolTaskMapper.eventForTask(verified))
    }

    @Test
    fun duplicateVoiceAnnouncementsAreSuppressed() {
        assertTrue(DolVoiceManager.announce("task_1", DolTaskEvent.TASK_COMPLETED, DolTaskMapper.MSG_COMPLETED_VOICE))
        assertFalse(DolVoiceManager.announce("task_1", DolTaskEvent.TASK_COMPLETED, DolTaskMapper.MSG_COMPLETED_VOICE))
        // Different task ID should still announce
        assertTrue(DolVoiceManager.announce("task_2", DolTaskEvent.TASK_COMPLETED, DolTaskMapper.MSG_COMPLETED_VOICE))
    }

    @Test
    fun overlayActiveRequiresBothEnabledAndPermissionGranted() {
        assertFalse(DolOverlayManager.computeOverlayActive(enabled = false, permissionGranted = false))
        assertFalse(DolOverlayManager.computeOverlayActive(enabled = true, permissionGranted = false))
        assertFalse(DolOverlayManager.computeOverlayActive(enabled = false, permissionGranted = true))
        assertTrue(DolOverlayManager.computeOverlayActive(enabled = true, permissionGranted = true))
    }
}
