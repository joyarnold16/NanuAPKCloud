package com.example.llama

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatStoreTest {
    private lateinit var context: Context
    private lateinit var store: ChatStore
    @Before fun setup() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        // Each test gets its own helper and database without mutating production singleton state.
        val constructor = ChatStore::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        context.deleteDatabase("chat_history.db")
        store = constructor.newInstance(context)
    }
    private suspend fun queued(id: String): String = store.enqueue(id,
        Message("u", "Find literal 50%_ and document text", true, attachmentName="paper.pdf", attachmentInfo="saved context", createdAt=100),
        Message("a", "Preparing", false, sourcePrompt="question", createdAt=101), "{}", "general")
    @Test fun savesReopensSearchesRenamesAndDeletes() = runBlocking {
        val id = store.create("general")
        val task = store.claim(queued(id))!!
        store.update(task, store.messages(id)[1].copy(content="Persisted reply", status="Complete"), "complete")
        store.close()
        val constructor = ChatStore::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible=true }
        store = constructor.newInstance(context)
        assertEquals(listOf("u", "a"), store.messages(id).map { it.id })
        assertEquals("saved context", store.messages(id)[0].attachmentInfo)
        assertEquals(100L, store.messages(id)[0].createdAt)
        assertEquals(1, store.list("50%_").size)
        assertEquals(0, store.list("50X_").size)
        assertEquals(1, store.list("Persisted reply").size)
        store.rename(id, "Renamed")
        assertEquals("Renamed", store.list().single().title)
        store.delete(id)
        assertTrue(store.messages(id).isEmpty())
        assertTrue(store.list().isEmpty())
    }
    @Test fun checkpointsDoNotCascadeDeleteTaskAndDuplicateDeliveryIsIgnored() = runBlocking {
        val id = store.create("general")
        val taskId = queued(id)
        val task = store.claim(taskId)!!
        repeat(3) { store.update(task, store.messages(id)[1].copy(content="partial $it")) }
        assertNull(store.claim(taskId))
        try { store.delete(); fail("Running history must not be deleted") } catch (_: IllegalStateException) { }
        store.update(task, store.messages(id)[1].copy(status="Stopped"), "stopped")
        store.delete()
        assertTrue(store.list().isEmpty())
    }
    @Test fun processDeathRestoresPartialAndAllowsRetry(): Unit = runBlocking {
        val id = store.create("general")
        val task = store.claim(queued(id))!!
        store.update(task, store.messages(id)[1].copy(content="Saved partial"))
        store.recoverInterrupted()
        assertEquals("Saved partial", store.messages(id)[1].content)
        assertTrue(store.messages(id)[1].status!!.startsWith("Interrupted"))
        store.delete()
    }
    @Test fun oneTaskAtATimeAndFailedStartIsRecoverable(): Unit = runBlocking {
        val id = store.create("general")
        val task = queued(id)
        try { queued(id); fail("Must reject competing task") } catch (_: IllegalStateException) { }
        store.failQueued(task)
        assertEquals("Failed", store.messages(id)[1].status)
        store.delete()
    }
    @Test fun hidesCompleteAndPartialThinkingBlocks() {
        assertEquals("Answer", LocalTaskService.visibleText("<think>private</think>Answer"))
        assertEquals("", LocalTaskService.visibleText("<think>private"))
    }
    @Test fun importsLegacyFileHistoryOnce(): Unit = runBlocking {
        context.getSharedPreferences("nanu_file_chat", 0).edit().putString("history", """[{"file":"old.pdf","question":"Old question","answer":"Old answer","time":1234}]""").commit()
        store.recoverInterrupted()
        val imported = store.list().single()
        assertEquals("old.pdf", imported.title)
        assertEquals("Old answer", store.messages(imported.id)[1].content)
        store.delete(imported.id)
        store.recoverInterrupted()
        assertTrue(store.list().isEmpty())
    }
    @Test fun editSettingsSurviveTaskCompletionAndRecovery(): Unit = runBlocking {
        val id = store.create("image")
        val request = org.json.JSONObject().put("inputImage", "/private/photo.png").put("strength", 0.45).put("width", 384).put("height", 192).put("system", "private prompt")
        val options = ImageEditArguments.savedOptions(request)
        assertFalse(options.contains("private prompt"))
        val taskId = store.enqueue(id, Message("image-user", "Warm sunset", true), Message("image-reply", "Preparing", false, imageOptions=options), request.toString(), "image")
        val task = store.claim(taskId)!!
        store.update(task, store.messages(id)[1].copy(status="Done", imagePath="/private/result.png"), "complete")
        store.recoverInterrupted()
        assertEquals(options, store.messages(id)[1].imageOptions)
        assertEquals("/private/result.png", store.messages(id)[1].imagePath)
    }
}
