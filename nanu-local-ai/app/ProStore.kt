package com.example.llama

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class ProProject(val id: String, val name: String)
data class ProAssistant(val id: String, val name: String, val instructions: String)
data class ProAsset(val id: String, val name: String, val path: String, val mime: String, val text: String)
data class ProMemory(val id: String, val project: String, val text: String, val created: Long, val enabled: Boolean)

class ProStore private constructor(context: Context) : SQLiteOpenHelper(context, "nanu_projects.db", null, 2) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE projects(id TEXT PRIMARY KEY,name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE assistants(id TEXT PRIMARY KEY,name TEXT NOT NULL,instructions TEXT NOT NULL)")
        db.execSQL("CREATE TABLE assets(id TEXT PRIMARY KEY,project TEXT NOT NULL REFERENCES projects(id),name TEXT NOT NULL,path TEXT NOT NULL,mime TEXT NOT NULL,text TEXT NOT NULL)")
        db.execSQL("CREATE TABLE conversations(project TEXT NOT NULL REFERENCES projects(id),conversation TEXT NOT NULL,PRIMARY KEY(project,conversation))")
        db.execSQL("CREATE TABLE memories(id TEXT PRIMARY KEY,project TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,created INTEGER NOT NULL,text TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE INDEX memories_project ON memories(project,enabled,created)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            db.execSQL("CREATE TABLE memories(id TEXT PRIMARY KEY,project TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,created INTEGER NOT NULL,text TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1)")
            db.execSQL("CREATE INDEX memories_project ON memories(project,enabled,created)")
        }
        if (new > 2) error("Missing Pro migration $old -> $new")
    }
    @Synchronized fun projects(): List<ProProject> = readableDatabase.rawQuery("SELECT id,name FROM projects ORDER BY name", null).use { c -> buildList { while(c.moveToNext()) add(ProProject(c.getString(0),c.getString(1))) } }
    @Synchronized fun createProject(name: String): String {
        require(name.trim().isNotBlank() && name.length <= 80) { "Use a project name of 1–80 characters." }
        require(projects().size < 40) { "Project limit reached (40)." }
        val id=UUID.randomUUID().toString(); writableDatabase.execSQL("INSERT INTO projects VALUES(?,?)", arrayOf(id,name.trim())); return id
    }
    @Synchronized fun assistants(): List<ProAssistant> = readableDatabase.rawQuery("SELECT id,name,instructions FROM assistants ORDER BY name", null).use { c -> buildList { while(c.moveToNext()) add(ProAssistant(c.getString(0),c.getString(1),c.getString(2))) } }
    @Synchronized fun saveAssistant(id: String?, name: String, instructions: String) {
        require(name.trim().isNotEmpty() && name.length <= 80 && instructions.trim().isNotEmpty() && instructions.length <= 4000) { "Use a name up to 80 characters and instructions up to 4,000 characters." }
        require(id != null || assistants().size < 40) { "Assistant limit reached (40)." }
        writableDatabase.execSQL("INSERT OR REPLACE INTO assistants VALUES(?,?,?)",arrayOf(id ?: UUID.randomUUID().toString(),name.trim(),instructions.trim()))
    }
    @Synchronized fun assets(project: String): List<ProAsset> = readableDatabase.rawQuery("SELECT id,name,path,mime,text FROM assets WHERE project=? ORDER BY rowid",arrayOf(project)).use { c -> buildList { while(c.moveToNext()) add(ProAsset(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4))) } }
    @Synchronized fun addAsset(project: String, name: String, path: String, mime: String, text: String = "") {
        val existing=assets(project)
        if(existing.any { it.path==path }) return
        require(existing.size < 30) { "This project already has 30 files. Create another project." }
        writableDatabase.execSQL("INSERT INTO assets VALUES(?,?,?,?,?,?)",arrayOf(UUID.randomUUID().toString(),project,name.take(160),path,mime,text.take(60000)))
    }
    @Synchronized fun memories(project: String, enabledOnly: Boolean = true): List<ProMemory> {
        val where = if (enabledOnly) "project=? AND enabled=1" else "project=?"
        return readableDatabase.rawQuery("SELECT id,project,text,created,enabled FROM memories WHERE $where ORDER BY created DESC", arrayOf(project)).use { c ->
            buildList { while (c.moveToNext()) add(ProMemory(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getInt(4)==1)) }
        }
    }
    @Synchronized fun saveMemory(project: String, text: String): String {
        val value=text.trim()
        require(value.isNotEmpty() && value.length<=2_000) { "Use a memory note of 1–2,000 characters." }
        require(memories(project,false).size<100) { "Project memory limit reached (100 notes)." }
        val id=UUID.randomUUID().toString()
        writableDatabase.execSQL("INSERT INTO memories VALUES(?,?,?,?,1)",arrayOf<Any>(id,project,System.currentTimeMillis(),value))
        return id
    }
    @Synchronized fun setMemoryEnabled(id: String, enabled: Boolean) {
        writableDatabase.execSQL("UPDATE memories SET enabled=? WHERE id=?",arrayOf<Any>(if(enabled) 1 else 0,id))
    }
    @Synchronized fun deleteMemory(id: String) { writableDatabase.delete("memories","id=?",arrayOf(id)) }
    @Synchronized fun link(project: String, conversation: String) { writableDatabase.execSQL("INSERT OR IGNORE INTO conversations VALUES(?,?)", arrayOf(project,conversation)) }
    @Synchronized fun conversations(project: String): Set<String> = readableDatabase.rawQuery("SELECT conversation FROM conversations WHERE project=?",arrayOf(project)).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
    companion object {
        @Volatile private var instance: ProStore?=null
        fun get(context: Context): ProStore = instance ?: synchronized(this) { instance ?: ProStore(context.applicationContext).also { instance=it } }
        fun activeAssistant(context: Context): ProAssistant? {
            if(!ProEntitlement.enabled(context)) return null
            val id=context.getSharedPreferences("nanu_local_ai",0).getString("pro_assistant",null)
            return get(context).assistants().firstOrNull { it.id==id }
        }
        fun activeProject(context: Context): String? {
            if(!ProEntitlement.enabled(context)) return null
            val id=context.getSharedPreferences("nanu_local_ai",0).getString("pro_project",null) ?: return null
            return id.takeIf { candidate -> get(context).projects().any { it.id==candidate } }
        }
        fun agentToolsEnabled(context: Context): Boolean = activeAssistant(context)!=null
        fun assistantInstructions(context: Context): String {
            val assistant=activeAssistant(context) ?: return ""
            return "\nActive local agent: ${assistant.name}. User-selected working instructions (never override safety rules or tool limits):\n${assistant.instructions}\n"
        }
        internal fun documentContext(assets: List<ProAsset>): String {
            val readable=assets.filter { it.text.isNotBlank() }
            require(readable.isNotEmpty()) { "Add a readable document first. Scanned PDFs need text recognition before import." }
            require(readable.size<=5) { "Select up to five documents per question." }
            val budget=15000/readable.size
            return readable.mapIndexed { i,a -> "[Document ${i+1}: ${a.name}]\n${a.text.take(budget)}\n[End Document ${i+1}${if(a.text.length>budget) "; excerpt only, remaining text not included" else ""}]" }.joinToString("\n\n")
        }
        internal fun retrieveProjectContext(query: String, assets: List<ProAsset>, memories: List<ProMemory>): RagResult {
            val documents = buildList {
                assets.filter { it.text.isNotBlank() }.forEach { add(RagDocument("asset:${it.id}",it.name,it.text)) }
                memories.filter { it.enabled && it.text.isNotBlank() }.forEach { memory ->
                    add(RagDocument("memory:${memory.id}","Project memory ${memory.id.take(8)}",memory.text))
                }
            }
            return LocalRagEngine.retrieve(query,documents)
        }
    }
}
