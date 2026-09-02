package com.example.llama

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class Conversation(val id: String, val title: String, val updated: Long, val mode: String)
data class ChatTask(val id: String, val conversation: String, val message: String, val request: String)

/** All database access is serialized off the UI thread. No chat data leaves app storage. */
class ChatStore private constructor(context: Context) : SQLiteOpenHelper(context, "chat_history.db", null, 1) {
    val changes = MutableStateFlow(0L)
    private var recovered = false
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE conversations(id TEXT PRIMARY KEY, title TEXT NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL, mode TEXT NOT NULL)")
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY, conversation TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, created INTEGER NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX messages_conversation ON messages(conversation, created)")
        db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY, conversation TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, message TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE, request TEXT NOT NULL, state TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { error("Missing chat database migration $old -> $new") }
    private suspend fun <T> access(write: Boolean = false, block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        synchronized(this@ChatStore) {
            val db = writableDatabase
            if (write) db.beginTransaction()
            try {
                val result = block(db)
                if (write) db.setTransactionSuccessful()
                result
            } finally {
                if (write) { db.endTransaction(); changes.value += 1 }
            }
        }
    }
    suspend fun create(mode: String): String = access(true) { db ->
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.execSQL("INSERT INTO conversations VALUES(?, ?, ?, ?, ?)", arrayOf(id, "New conversation", now, now, mode))
        id
    }
    suspend fun list(query: String = ""): List<Conversation> = access { db ->
        val pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        db.rawQuery("SELECT DISTINCT c.id,c.title,c.updated,c.mode FROM conversations c LEFT JOIN messages m ON m.conversation=c.id WHERE c.title LIKE ? ESCAPE '\\' OR m.payload LIKE ? ESCAPE '\\' ORDER BY c.updated DESC", arrayOf(pattern, pattern)).use { c ->
            buildList { while (c.moveToNext()) add(Conversation(c.getString(0), c.getString(1), c.getLong(2), c.getString(3))) }
        }
    }
    suspend fun messages(id: String): List<Message> = access { db ->
        db.rawQuery("SELECT payload FROM messages WHERE conversation=? ORDER BY created,rowid", arrayOf(id)).use { c ->
            buildList { while (c.moveToNext()) add(decode(c.getString(0))) }
        }
    }
    suspend fun rename(id: String, title: String) = access(true) { db ->
        require(title.trim().isNotEmpty())
        db.execSQL("UPDATE conversations SET title=? WHERE id=?", arrayOf(title.trim().take(120), id))
    }
    suspend fun delete(id: String? = null) = access(true) { db ->
        db.rawQuery("SELECT id FROM tasks WHERE state IN ('queued','running')" + if (id == null) "" else " AND conversation=?", if (id == null) null else arrayOf(id)).use {
            check(!it.moveToFirst()) { "Stop the active task before deleting its history." }
        }
        db.delete("conversations", if (id == null) null else "id=?", if (id == null) null else arrayOf(id))
    }
    suspend fun enqueue(conversation: String, user: Message, assistant: Message, request: String, mode: String): String = access(true) { db ->
        db.rawQuery("SELECT id FROM tasks WHERE state IN ('queued','running')", null).use { check(!it.moveToFirst()) { "Another local task is still running." } }
        put(db, conversation, user); put(db, conversation, assistant)
        db.execSQL("UPDATE conversations SET title=CASE WHEN title='New conversation' THEN ? ELSE title END, updated=?, mode=? WHERE id=?", arrayOf(user.content.take(70), System.currentTimeMillis(), mode, conversation))
        val id = UUID.randomUUID().toString()
        db.execSQL("INSERT INTO tasks VALUES(?,?,?,?, 'queued')", arrayOf(id, conversation, assistant.id, request))
        id
    }
    suspend fun claim(id: String): ChatTask? = access(true) { db ->
        val count = db.update("tasks", ContentValues().apply { put("state", "running") }, "id=? AND state='queued'", arrayOf(id))
        if (count != 1) null else db.rawQuery("SELECT conversation,message,request FROM tasks WHERE id=?", arrayOf(id)).use {
            check(it.moveToFirst()); ChatTask(id, it.getString(0), it.getString(1), it.getString(2))
        }
    }
    suspend fun update(task: ChatTask, message: Message, state: String? = null) = access(true) { db ->
        put(db, task.conversation, message)
        db.execSQL("UPDATE conversations SET updated=? WHERE id=?", arrayOf(System.currentTimeMillis(), task.conversation))
        if (state != null) db.execSQL("UPDATE tasks SET state=?, request='' WHERE id=?", arrayOf(state, task.id))
    }
    suspend fun failQueued(id: String) = access(true) { db ->
        db.execSQL("UPDATE tasks SET state='failed',request='' WHERE id=? AND state='queued'", arrayOf(id))
        db.rawQuery("SELECT m.id,m.payload FROM messages m JOIN tasks t ON t.message=m.id WHERE t.id=? AND t.state='failed'", arrayOf(id)).use { c ->
            if (c.moveToFirst()) db.execSQL("UPDATE messages SET payload=? WHERE id=?", arrayOf(encode(decode(c.getString(1)).copy(content="Could not start background task. Please try again.", status="Failed")), c.getString(0)))
        }
    }
    suspend fun recoverInterrupted() = access(true) { db ->
        if (recovered) return@access
        db.rawQuery("SELECT m.id,m.payload FROM messages m JOIN tasks t ON t.message=m.id WHERE t.state IN ('queued','running')", null).use { c ->
            while(c.moveToNext()) db.execSQL("UPDATE messages SET payload=? WHERE id=?", arrayOf(encode(decode(c.getString(1)).copy(status="Interrupted — tap Regenerate to retry")), c.getString(0)))
        }
        db.execSQL("UPDATE tasks SET state='interrupted',request='' WHERE state IN ('queued','running')")
        recovered = true
    }
    private fun put(db: SQLiteDatabase, conversation: String, m: Message) {
        val values = ContentValues().apply {
            put("id", m.id); put("conversation", conversation); put("created", m.createdAt); put("payload", encode(m))
        }
        if (db.update("messages", values, "id=?", arrayOf(m.id)) == 0) db.insertOrThrow("messages", null, values)
    }
    companion object {
        @Volatile private var instance: ChatStore? = null
        fun get(context: Context): ChatStore = instance ?: synchronized(this) {
            instance ?: ChatStore(context.applicationContext).also { instance = it }
        }
        private fun encode(m: Message): String = JSONObject().apply {
            put("id", m.id); put("content", m.content); put("user", m.isUser); put("created", m.createdAt)
            put("attachment", m.attachmentName); put("info", m.attachmentInfo); put("image", m.imagePath)
            put("status", m.status); put("prompt", m.sourcePrompt)
        }.toString()
        private fun decode(raw: String): Message = JSONObject(raw).let { j ->
            fun optional(key: String) = if (j.isNull(key)) null else j.optString(key).takeIf { it.isNotEmpty() }
            Message(j.getString("id"), j.getString("content"), j.getBoolean("user"), optional("attachment"), optional("info"), optional("image"), optional("status"), optional("prompt"), j.getLong("created"))
        }
    }
}
