package com.example.llama

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class ProStoreTest {
    @Test fun upgradesVersionOneWithoutLosingProjects() {
        val context: android.app.Application=RuntimeEnvironment.getApplication()
        context.deleteDatabase("nanu_projects.db")
        context.openOrCreateDatabase("nanu_projects.db",Context.MODE_PRIVATE,null).use { db ->
            db.execSQL("CREATE TABLE projects(id TEXT PRIMARY KEY,name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE assistants(id TEXT PRIMARY KEY,name TEXT NOT NULL,instructions TEXT NOT NULL)")
            db.execSQL("CREATE TABLE assets(id TEXT PRIMARY KEY,project TEXT NOT NULL REFERENCES projects(id),name TEXT NOT NULL,path TEXT NOT NULL,mime TEXT NOT NULL,text TEXT NOT NULL)")
            db.execSQL("CREATE TABLE conversations(project TEXT NOT NULL REFERENCES projects(id),conversation TEXT NOT NULL,PRIMARY KEY(project,conversation))")
            db.execSQL("INSERT INTO projects VALUES('legacy','Existing project')")
            db.version=1
        }
        val constructor=ProStore::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible=true }
        val store=constructor.newInstance(context)
        store.saveMemory("legacy","Migration keeps this local note.")
        assertEquals("Existing project",store.projects().single().name)
        assertEquals("Migration keeps this local note.",store.memories("legacy").single().text)
        store.close()
    }

    @Test fun projectsAssistantsAndFileLinksSurviveReopen() {
        val context: android.app.Application=RuntimeEnvironment.getApplication()
        context.deleteDatabase("nanu_projects.db")
        val constructor=ProStore::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible=true }
        var store=constructor.newInstance(context)
        val project=store.createProject("Research")
        store.saveAssistant(null,"Tutor","Explain using examples")
        store.addAsset(project,"notes.txt","/private/notes.txt","text/plain","Research notes")
        store.saveMemory(project,"Prefer concise answers with source citations.")
        store.link(project,"conversation-a"); store.link(project,"conversation-a")
        store.close(); store=constructor.newInstance(context)
        assertEquals("Research",store.projects().single().name)
        assertEquals("Explain using examples",store.assistants().single().instructions)
        assertEquals("Research notes",store.assets(project).single().text)
        assertEquals("Prefer concise answers with source citations.",store.memories(project).single().text)
        assertEquals(setOf("conversation-a"),store.conversations(project))
        store.close()
    }
    @Test fun documentExcerptsIncludeEverySourceWithinBoundedBudget() {
        val assets=(1..5).map { ProAsset("$it","file$it.txt","","text/plain","$it".repeat(20000)) }
        val result=ProStore.documentContext(assets)
        for(i in 1..5) assertTrue(result.contains("[Document $i: file$i.txt]"))
        assertTrue(result.contains("excerpt only"))
        assertTrue(result.length<16500)
        try { ProStore.documentContext(emptyList()); fail("Empty input must fail") } catch(_:IllegalArgumentException) { }
        try { ProStore.documentContext(assets+assets); fail("Too many documents must fail") } catch(_:IllegalArgumentException) { }
    }
    @Test fun projectRetrievalUsesDocumentsAndExplicitMemory() {
        val assets=listOf(
            ProAsset("sms","safety.txt","","text/plain","Emergency steering drills are recorded in the deck log."),
            ProAsset("food","menu.txt","","text/plain","Dinner includes rice and soup.")
        )
        val memory=ProMemory("m","p","The vessel call sign is V7NA.",100,true)

        val drill=ProStore.retrieveProjectContext("Where are emergency steering drills recorded?",assets,listOf(memory))
        assertTrue(drill.context.contains("deck log"))
        assertFalse(drill.context.contains("Dinner"))
        val callSign=ProStore.retrieveProjectContext("What is the vessel call sign?",assets,listOf(memory))
        assertTrue(callSign.context.contains("V7NA"))
        assertTrue(callSign.citations.any { it.contains("Project memory") })
    }
}
