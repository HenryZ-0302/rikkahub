package me.rerere.rikkahub.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.UserSessionStore
import me.rerere.rikkahub.data.model.Assistant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "CloudSyncService"
private const val BASE_URL = "https://rikkahub.zeabur.app/api"

/**
 * 云端同步服务
 * 自动同步设置到云端（提供商、助手配置、其他设置）
 */
class CloudSyncService(
    private val settingsStore: SettingsStore,
    private val userSessionStore: UserSessionStore,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val scope: CoroutineScope
) {
    private var lastSyncedSettings: Settings? = null
    
    fun startAutoSync() {
        scope.launch(Dispatchers.IO) {
            // 监听设置变化，延迟2秒后同步（避免频繁同步）
            settingsStore.settingsFlow
                .debounce(2000)
                .distinctUntilChanged()
                .collectLatest { settings ->
                    if (settings.init) return@collectLatest
                    
                    // 检查是否有实际变化
                    if (lastSyncedSettings == settings) return@collectLatest
                    
                    syncSettingsToCloud(settings)
                    lastSyncedSettings = settings
                }
        }
    }
    
    private suspend fun syncSettingsToCloud(settings: Settings) {
        try {
            val token = userSessionStore.getToken() ?: return
            
            // 1. 同步提供商
            syncProviders(token, settings.providers)
            
            // 2. 同步助手
            syncAssistants(token, settings.assistants)
            
            // 3. 同步其他设置
            syncSettings(token, settings)
            
            Log.d(TAG, "Settings synced to cloud successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync settings to cloud: ${e.message}")
        }
    }
    
    private suspend fun syncProviders(token: String, providers: List<ProviderSetting>) {
        try {
            val providersJson = json.encodeToString(
                kotlinx.serialization.serializer<List<ProviderSetting>>(),
                providers
            )
            
            val request = Request.Builder()
                .url("$BASE_URL/sync/providers")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post("""{"providers":$providersJson}""".toRequestBody("application/json".toMediaType()))
                .build()
            
            okHttpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync providers: ${e.message}")
        }
    }
    
    private suspend fun syncAssistants(token: String, assistants: List<Assistant>) {
        try {
            val assistantsJson = json.encodeToString(
                kotlinx.serialization.serializer<List<Assistant>>(),
                assistants
            )
            
            val request = Request.Builder()
                .url("$BASE_URL/sync/assistants")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post("""{"assistants":$assistantsJson}""".toRequestBody("application/json".toMediaType()))
                .build()
            
            okHttpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync assistants: ${e.message}")
        }
    }
    
    private suspend fun syncSettings(token: String, settings: Settings) {
        try {
            val settingsJson = json.encodeToString(
                kotlinx.serialization.serializer<Settings>(),
                settings
            )
            
            val request = Request.Builder()
                .url("$BASE_URL/sync/settings")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post("""{"settings":$settingsJson}""".toRequestBody("application/json".toMediaType()))
                .build()
            
            okHttpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync settings: ${e.message}")
        }
    }
}
