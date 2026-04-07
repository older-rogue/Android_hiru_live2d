package com.zx.hiru.domain.usecase

import com.zx.hiru.ai.DialogValidator
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
    private lateinit var dialogValidator: DialogValidator
    private lateinit var chatUseCase: ChatUseCase

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        aiRepository = mockk()
        memoryRepository = mockk()
        dialogValidator = mockk()
        chatUseCase = ChatUseCase(aiRepository, memoryRepository, dialogValidator)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when input is invalid, should emit Filtered state`() = runTest {
        // Given - DialogValidator.validate 是 suspend 函数，使用 coEvery
        coEvery { dialogValidator.validate("test") } returns false

        // When
        val flow = chatUseCase.execute("test")
        val states = mutableListOf<ChatState>()
        flow.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is ChatState.Filtered })
    }

    @Test
    fun `when input is valid, should emit Thinking and Responding states`() = runTest {
        // Given
        val response = ChatResponse("Hello", null, null)
        coEvery { dialogValidator.validate("hello") } returns true
        every { memoryRepository.getMemoryContext() } returns ""
        coEvery { aiRepository.chat("hello", "") } returns Result.success(response)
        coEvery { memoryRepository.updateMemory(any(), any()) } returns Result.success(Unit)

        // When
        val flow = chatUseCase.execute("hello")
        val states = mutableListOf<ChatState>()
        flow.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is ChatState.Thinking })
        assertTrue(states.any { it is ChatState.Responding })
    }
}
