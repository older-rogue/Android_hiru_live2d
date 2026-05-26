package com.zx.hiru.data.repository.impl

import android.content.Context
import com.zx.hiru.ai.MemoryManager
import com.zx.hiru.data.repository.MemoryRepository

/**
 * Memory Repository 实现
 *
 * 封装 MemoryManager 单例
 */
class MemoryRepositoryImpl(
    context: Context
) : MemoryRepository {

    private val appContext = context.applicationContext
    private val memoryManager = MemoryManager.getInstance()

    // 初始化块：在构造时自动初始化 MemoryManager
    init {
        memoryManager.init(appContext)
    }

    override fun getMemory(): String = memoryManager.getMemory()

    override fun getMemoryContext(): String = memoryManager.getMemoryContext()

    override suspend fun saveMemory(memoryText: String): Result<Unit> {
        return runCatching {
            if (memoryText.isNotBlank() && memoryText != "无变化") {
                memoryManager.updateMemoryText(memoryText)
            }
        }
    }

    override fun clearMemory() {
        memoryManager.clearMemory()
    }
}
