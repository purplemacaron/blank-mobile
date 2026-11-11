package dev.blank.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = SessionStore(tmp.root)

    private fun session(id: String, updated: Long, title: String = "t") = Session(
        id = id,
        title = title,
        createdAt = 1L,
        updatedAt = updated,
        messages = listOf(
            StoredMessage("user", "안녕하세요"),
            StoredMessage("assistant", "반갑습니다", reasoning = "생각"),
        ),
    )

    @Test
    fun `save and load round trip preserves korean and reasoning`() {
        val store = store()
        store.save(session("a", 10L))
        val loaded = store.load("a")!!
        assertEquals("안녕하세요", loaded.messages[0].content)
        assertEquals("반갑습니다", loaded.messages[1].content)
        assertEquals("생각", loaded.messages[1].reasoning)
        assertEquals(10L, loaded.updatedAt)
    }

    @Test
    fun `list is sorted by updated descending`() {
        val store = store()
        store.save(session("old", 1L, "older"))
        store.save(session("new", 2L, "newer"))
        assertEquals(listOf("new", "old"), store.list().map { it.id })
        assertEquals("newer", store.list()[0].title)
    }

    @Test
    fun `save is atomic and leaves no temp files`() {
        val store = store()
        store.save(session("a", 1L))
        val files = tmp.root.listFiles()!!.map { it.name }
        assertEquals(listOf("a.json"), files)
        assertTrue(tmp.root.resolve("a.json").readText().contains("안녕하세요"))
    }

    @Test
    fun `overwrite replaces previous content`() {
        val store = store()
        store.save(session("a", 1L))
        store.save(session("a", 2L, "updated"))
        assertEquals("updated", store.load("a")!!.title)
        assertEquals(1, tmp.root.listFiles()!!.size)
    }

    @Test
    fun `delete removes session and missing load is null`() {
        val store = store()
        store.save(session("a", 1L))
        store.delete("a")
        assertNull(store.load("a"))
        assertFalse(tmp.root.resolve("a.json").exists())
        assertNull(store.load("never"))
    }

    @Test
    fun `title uses first user message trimmed`() {
        val long = "가".repeat(60)
        assertEquals("가".repeat(40) + "…", titleFor(listOf(StoredMessage("user", long))))
        assertEquals("질문", titleFor(listOf(StoredMessage("assistant", "답"), StoredMessage("user", " 질문 "))))
        assertEquals("", titleFor(listOf(StoredMessage("assistant", "답"))))
        assertEquals("", titleFor(emptyList()))
    }
}