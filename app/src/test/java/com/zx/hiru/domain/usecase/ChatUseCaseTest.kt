package com.zx.hiru.domain.usecase

import com.zx.hiru.data.repository.AiRepository
import com.zx.hiru.data.repository.MemoryRepository
import com.zx.hiru.domain.model.ChatResponse
import com.zx.hiru.domain.model.ChatState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatUseCaseTest {

    private lateinit var aiRepository: AiRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var chatUseCase: ChatUseCase

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        aiRepository = mockk()
        memoryRepository = mockk()
        chatUseCase = ChatUseCase(aiRepository, memoryRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when AI returns is_valid false, should emit Filtered state`() = runTest {
        // Given
        val response = ChatResponse(text = "", tone = null, action = null, isValid = false)
        every { memoryRepository.getMemory() } returns ""
        coEvery { aiRepository.chat("test", "") } returns Result.success(response)

        // When
        val flow = chatUseCase.execute("test")
        val states = mutableListOf<ChatState>()
        flow.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is ChatState.Filtered })
    }

    @Test
    fun `when AI returns is_valid true, should emit Thinking and Responding states`() = runTest {
        // Given
        val response = ChatResponse(text = "Hello", tone = null, action = null, isValid = true)
        every { memoryRepository.getMemory() } returns ""
        coEvery { aiRepository.chat("hello", "") } returns Result.success(response)
        coEvery { memoryRepository.saveMemory(any()) } returns Result.success(Unit)

        // When
        val flow = chatUseCase.execute("hello")
        val states = mutableListOf<ChatState>()
        flow.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is ChatState.Thinking })
        assertTrue(states.any { it is ChatState.Responding })
    }

    @Test
    fun `when AI fails, should emit Error state`() = runTest {
        // Given
        every { memoryRepository.getMemory() } returns ""
        coEvery { aiRepository.chat("hello", "") } returns Result.failure(RuntimeException("Network error"))

        // When
        val flow = chatUseCase.execute("hello")
        val states = mutableListOf<ChatState>()
        flow.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is ChatState.Error })
    }
}
