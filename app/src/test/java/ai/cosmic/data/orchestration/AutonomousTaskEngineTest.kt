package ai.cosmic.data.orchestration

import org.junit.Assert.assertEquals
import org.junit.Test

class AutonomousTaskEngineTest {

    @Test
    fun taskProgressReflectsCompletedSteps() {
        val steps = listOf(
            TaskStep("s1", 1, "Plan", "Plan work", AgentRole.ARCHITECT, "antigravity", TaskStatus.COMPLETED),
            TaskStep("s2", 2, "Code", "Write code", AgentRole.DEVELOPER, "codex", TaskStatus.RUNNING),
            TaskStep("s3", 3, "Test", "Run tests", AgentRole.TESTER, "antigravity", TaskStatus.PENDING),
            TaskStep("s4", 4, "Review", "Review diff", AgentRole.REVIEWER, "claude", TaskStatus.PENDING)
        )
        val task = AutonomousTask(
            id = "t1",
            title = "Build feature",
            goal = "Build feature",
            projectPath = "/tmp/proj",
            status = TaskStatus.RUNNING,
            steps = steps
        )
        assertEquals(25, task.progressPercent)
    }

    @Test
    fun emptyTaskHasZeroProgress() {
        val task = AutonomousTask(
            id = "t2",
            title = "Empty",
            goal = "Empty",
            projectPath = "/tmp/proj"
        )
        assertEquals(0, task.progressPercent)
    }
}
