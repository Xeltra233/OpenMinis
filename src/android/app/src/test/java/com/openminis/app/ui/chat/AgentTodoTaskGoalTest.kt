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

    @Test
    fun todoAndGoal_diskPersistenceCycle() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "minis-test-" + java.util.UUID.randomUUID())
        tempDir.mkdirs()
        try {
            val todosFile = java.io.File(tempDir, "todos.json")
            val todos = listOf(
                SessionTodo(id = 1, subject = "Task 1", status = "in_progress", description = "Work on disk persistence"),
                SessionTodo(id = 2, subject = "Task 2", status = "pending", description = "Test reload"),
            )
            val array = org.json.JSONArray()
            for (t in todos) {
                array.put(JSONObject().put("id", t.id).put("subject", t.subject).put("status", t.status).put("description", t.description))
            }
            todosFile.writeText(array.toString())
            assertTrue("todos.json must exist on disk", todosFile.exists())

            val loadedText = todosFile.readText()
            val loadedArr = org.json.JSONArray(loadedText)
            assertEquals(2, loadedArr.length())
            assertEquals("Task 1", loadedArr.getJSONObject(0).getString("subject"))
            assertEquals("in_progress", loadedArr.getJSONObject(0).getString("status"))

            val goalFile = java.io.File(tempDir, "goal.json")
            val goal = SessionGoal(objective = "Persist Goal", status = "active", summary = "Writing to disk")
            val goalJson = JSONObject().put("objective", goal.objective).put("status", goal.status).put("summary", goal.summary)
            goalFile.writeText(goalJson.toString())
            assertTrue("goal.json must exist on disk", goalFile.exists())

            val loadedGoalJson = JSONObject(goalFile.readText())
            assertEquals("Persist Goal", loadedGoalJson.getString("objective"))
            assertEquals("active", loadedGoalJson.getString("status"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
