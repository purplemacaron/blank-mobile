package dev.blank.mobile.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `parses openai data array with vision hints`() {
        val models = ModelCatalog.parse(
            """
            {"data":[
              {"id":"gpt-4o","architecture":{"input_modalities":["text","image"]},"context_window":128000},
              {"id":"text-only","supports_vision":false},
              {"id":"plain"}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("gpt-4o", "text-only", "plain"), models.map { it.id })
        assertEquals(true, models[0].vision)
        assertEquals(128000, models[0].context)
        assertEquals(false, models[1].vision)
        assertNull(models[2].vision)
        assertEquals(128000, models[2].context)
    }

    @Test
    fun `parses gemini models array stripping name prefix`() {
        val models = ModelCatalog.parse(
            """
            {"models":[
              {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","inputTokenLimit":1048576}
            ]}
            """.trimIndent(),
        )
        assertEquals(1, models.size)
        assertEquals("gemini-2.5-flash", models[0].id)
        assertEquals("Gemini 2.5 Flash", models[0].name)
        assertEquals(1048576, models[0].context)
    }

    @Test
    fun `capabilities image_input counts as vision hint`() {
        val models = ModelCatalog.parse("""{"data":[{"id":"m","capabilities":{"image_input":true}}]}""")
        assertEquals(true, models[0].vision)
    }

    @Test
    fun `empty or malformed listing yields empty list`() {
        assertEquals(emptyList<ModelInfo>(), ModelCatalog.parse("{}"))
        assertEquals(emptyList<ModelInfo>(), ModelCatalog.parse("""{"data":[{"id":""}]}"""))
    }
}