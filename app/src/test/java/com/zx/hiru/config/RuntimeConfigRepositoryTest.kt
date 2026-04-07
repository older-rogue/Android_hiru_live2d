package com.zx.hiru.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConfigRepositoryTest {

    @Test
    fun `empty storage yields incomplete config`() {
        val storage = InMemoryStorage()
        val repository = RuntimeConfigRepository(storage)

        val config = repository.load()

        assertFalse(config.isComplete())
        assertEquals("", config.ai.baseUrl)
        assertEquals("", config.tts.token)
    }

    @Test
    fun `save persists all values and makes config complete`() {
        val storage = InMemoryStorage()
        val repository = RuntimeConfigRepository(storage)
        val config = RuntimeApiConfig(
            ai = RuntimeApiConfig.Ai(
                baseUrl = "https://api.example.com/v1",
                apiKey = "sk-test",
                model = "test-model",
                timeoutMs = "30000",
                systemPrompt = "prompt"
            ),
            asr = RuntimeApiConfig.Asr(
                appId = "asr-app",
                token = "asr-token",
                address = "wss://asr.example.com",
                uri = "/asr",
                resourceId = "asr-resource"
            ),
            tts = RuntimeApiConfig.Tts(
                appId = "tts-app",
                token = "tts-token",
                address = "wss://tts.example.com",
                uri = "/tts",
                resourceId = "tts-resource"
            )
        )

        repository.save(config)
        val loaded = repository.load()

        assertTrue(loaded.isComplete())
        assertEquals("test-model", loaded.ai.model)
        assertEquals("tts-resource", loaded.tts.resourceId)
    }

    @Test
    fun `save overwrites previous values`() {
        val storage = InMemoryStorage()
        val repository = RuntimeConfigRepository(storage)

        repository.save(
            RuntimeApiConfig(
                ai = RuntimeApiConfig.Ai("a", "b", "c", "1", "p1"),
                asr = RuntimeApiConfig.Asr("1", "2", "3", "4", "5"),
                tts = RuntimeApiConfig.Tts("6", "7", "8", "9", "10")
            )
        )

        repository.save(
            RuntimeApiConfig(
                ai = RuntimeApiConfig.Ai("aa", "bb", "cc", "2", "p2"),
                asr = RuntimeApiConfig.Asr("11", "22", "33", "44", "55"),
                tts = RuntimeApiConfig.Tts("66", "77", "88", "99", "100")
            )
        )

        val loaded = repository.load()

        assertEquals("aa", loaded.ai.baseUrl)
        assertEquals("44", loaded.asr.uri)
        assertEquals("100", loaded.tts.resourceId)
    }

    private class InMemoryStorage : RuntimeConfigRepository.Storage {
        private val values = linkedMapOf<String, String>()

        override fun getString(key: String): String = values[key].orEmpty()

        override fun putString(key: String, value: String) {
            values[key] = value
        }
    }
}
