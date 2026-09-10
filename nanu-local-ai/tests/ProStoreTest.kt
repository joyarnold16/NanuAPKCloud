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
    @Test fun projectsAssistantsAndFileLinksSurviveReopen() {
        val context=RuntimeEnvironment.getApplication<android.app.Application>()
        context.deleteDatabase("nanu_projects.db")
        val constructor=ProStore::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible=true }
        var store=constructor.newInstance(context)
        val project=store.createProject("Research")
        store.saveAssistant(null,"Tutor","Explain using examples")
        store.addAsset(project,"notes.txt","/private/notes.txt","text/plain","Research notes")
        store.link(project,"conversation-a"); store.link(project,"conversation-a")
        store.close(); store=constructor.newInstance(context)
        assertEquals("Research",store.projects().single().name)
        assertEquals("Explain using examples",store.assistants().single().instructions)
        assertEquals("Research notes",store.assets(project).single().text)
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
}
