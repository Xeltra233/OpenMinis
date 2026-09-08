package com.openminis.app.ui.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTodoTaskGoalTest {

    @Test
    fun todoAndTaskModel_holdsData() {
        val todo = SessionTodo(id = 1, subject = "Initial task", status = "pending", description = "Test desc")
        assertEquals(1, todo.id)
        assertEquals("Initial task", todo.subject)
        assertEquals("pending", todo.status)
        assertEquals("Test desc", todo.description)

        val updated = todo.copy(status = "completed")
        assertEquals("completed", updated.status)
    }

    @Test
    fun goalModel_holdsData() {
        val goal = SessionGoal(objective = "Build APK", status = "active", summary = "In progress")
        assertEquals("Build APK", goal.objective)
        assertEquals("active", goal.status)
        assertEquals("In progress", goal.summary)

        val done = goal.copy(status = "completed", summary = "All tests passed")
        assertEquals("completed", done.status)
    }
}
