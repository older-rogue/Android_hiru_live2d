package com.zx.hiru.cache

import android.content.Context
import android.content.SharedPreferences

/**
 * 缓存管理器
 *
 * 基于SharedPreferences的用户信息存储工具类
 *
 * 使用前必须先调用 init() 方法进行初始化
 *
 * 使用示例：
 * ```kotlin
 * // 在Application或Activity中初始化
 * CacheManager.init(context)
 *
 * // 保存数据
 * CacheManager.saveUserInfo("user_name", "张三")
 * CacheManager.saveUserInfo("user_age", 25)
 * CacheManager.saveUserInfo("is_logged_in", true)
 *
 * // 读取数据
 * val name = CacheManager.getUserInfo("user_name")
 * val age = CacheManager.getUserInfo("user_age", 0)
 * val isLoggedIn = CacheManager.getUserInfo("is_logged_in", false)
 *
 * // 删除数据
 * CacheManager.removeUserInfo("user_name")
 *
 * // 清空所有缓存
 * CacheManager.clearAll()
 * ```
 */
object CacheManager {

    private const val DEFAULT_CACHE_NAME = "hiru_cache"

    private var sharedPreferences: SharedPreferences? = null

    /**
     * 初始化缓存管理器
     *
     * @param context Application或Activity的Context
     * @param cacheName 缓存文件名称，默认为 "hiru_cache"
     */
    fun init(context: Context, cacheName: String = DEFAULT_CACHE_NAME) {
        sharedPreferences = context.getSharedPreferences(cacheName, Context.MODE_PRIVATE)
    }

    private fun requireSharedPreferences(): SharedPreferences {
        return sharedPreferences
            ?: throw IllegalStateException("CacheManager未初始化，请先调用 init() 方法")
    }

    // ==================== String 类型操作 ====================

    /**
     * 保存用户信息（String类型）
     */
    fun saveUserInfo(key: String, value: String) {
        requireSharedPreferences().edit().putString(key, value).apply()
    }

    /**
     * 获取用户信息（String类型）
     *
     * @param key 键名
     * @param default 默认值
     * @return 存储的值，如果不存在则返回默认值
     */
    fun getUserInfo(key: String, default: String = ""): String {
        return requireSharedPreferences().getString(key, default) ?: default
    }

    // ==================== Int 类型操作 ====================

    /**
     * 保存用户信息（Int类型）
     */
    fun saveUserInfo(key: String, value: Int) {
        requireSharedPreferences().edit().putInt(key, value).apply()
    }

    /**
     * 获取用户信息（Int类型）
     */
    fun getUserInfo(key: String, default: Int): Int {
        return requireSharedPreferences().getInt(key, default)
    }

    // ==================== Boolean 类型操作 ====================

    /**
     * 保存用户信息（Boolean类型）
     */
    fun saveUserInfo(key: String, value: Boolean) {
        requireSharedPreferences().edit().putBoolean(key, value).apply()
    }

    /**
     * 获取用户信息（Boolean类型）
     */
    fun getUserInfo(key: String, default: Boolean): Boolean {
        return requireSharedPreferences().getBoolean(key, default)
    }

    // ==================== Long 类型操作 ====================

    /**
     * 保存用户信息（Long类型）
     */
    fun saveUserInfo(key: String, value: Long) {
        requireSharedPreferences().edit().putLong(key, value).apply()
    }

    /**
     * 获取用户信息（Long类型）
     */
    fun getUserInfo(key: String, default: Long): Long {
        return requireSharedPreferences().getLong(key, default)
    }

    // ==================== Float 类型操作 ====================

    /**
     * 保存用户信息（Float类型）
     */
    fun saveUserInfo(key: String, value: Float) {
        requireSharedPreferences().edit().putFloat(key, value).apply()
    }

    /**
     * 获取用户信息（Float类型）
     */
    fun getUserInfo(key: String, default: Float): Float {
        return requireSharedPreferences().getFloat(key, default)
    }

    // ==================== 通用操作 ====================

    /**
     * 删除指定键的用户信息
     */
    fun removeUserInfo(key: String) {
        requireSharedPreferences().edit().remove(key).apply()
    }

    /**
     * 检查指定键是否存在
     */
    fun contains(key: String): Boolean {
        return requireSharedPreferences().contains(key)
    }

    /**
     * 清空所有缓存
     */
    fun clearAll() {
        requireSharedPreferences().edit().clear().apply()
    }

    /**
     * 获取所有缓存的键值对
     */
    fun getAll(): Map<String, *> {
        return requireSharedPreferences().all
    }
}