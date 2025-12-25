package me.rerere.rikkahub.ui.pages.backup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.UserSessionStore
import me.rerere.rikkahub.data.db.ConversationRepository
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.sync.WebDavBackupItem
import me.rerere.rikkahub.data.sync.WebdavSync
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.UiState
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val TAG = "BackupVM"
private const val BASE_URL = "https://rikkahub.zeabur.app/api"

@Serializable
private data class CloudSyncResponse(
    val success: Boolean,
    val data: List<CloudConversation>? = null
)

@Serializable
private data class CloudConversation(
    val id: String,
    val title: String? = null,
    val nodes: kotlinx.serialization.json.JsonElement? = null,
    val usage: kotlinx.serialization.json.JsonElement? = null,
    val assistantId: String? = null,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

// 云端同步状态
sealed class CloudSyncState {
    data object Idle : CloudSyncState()
    data object Loading : CloudSyncState()
    data class Success(val restoredCount: Int, val skippedCount: Int) : CloudSyncState()
    data class Error(val message: String) : CloudSyncState()
}

class BackupVM(
    private val settingsStore: SettingsStore,
    private val webdavSync: WebdavSync,
    private val userSessionStore: UserSessionStore,
    private val okHttpClient: OkHttpClient,
    private val conversationRepo: ConversationRepository,
    private val json: Json
) : ViewModel() {
    val settings = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings.dummy()
    )

    val webDavBackupItems = MutableStateFlow<UiState<List<WebDavBackupItem>>>(UiState.Idle)
    
    // 云端同步状态
    val cloudSyncState = MutableStateFlow<CloudSyncState>(CloudSyncState.Idle)
    val cloudConversationCount = MutableStateFlow<Int?>(null)

    init {
        loadBackupFileItems()
        loadCloudConversationCount()
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun loadBackupFileItems() {
        viewModelScope.launch {
            runCatching {
                webDavBackupItems.emit(UiState.Loading)
                webDavBackupItems.emit(
                    value = UiState.Success(
                        data = webdavSync.listBackupFiles(
                            webDavConfig = settings.value.webDavConfig
                        ).sortedByDescending { it.lastModified }
                    )
                )
            }.onFailure {
                webDavBackupItems.emit(UiState.Error(it))
            }
        }
    }

    suspend fun testWebDav() {
        webdavSync.testWebdav(settings.value.webDavConfig)
    }

    suspend fun backup() {
        webdavSync.backupToWebDav(settings.value.webDavConfig)
    }

    suspend fun restore(item: WebDavBackupItem) {
        webdavSync.restoreFromWebDav(webDavConfig = settings.value.webDavConfig, item = item)
    }

    suspend fun deleteWebDavBackupFile(item: WebDavBackupItem) {
        webdavSync.deleteWebDavBackupFile(settings.value.webDavConfig, item)
    }

    suspend fun exportToFile(): File {
        return webdavSync.prepareBackupFile(settings.value.webDavConfig.copy())
    }

    suspend fun restoreFromLocalFile(file: File) {
        webdavSync.restoreFromLocalFile(file, settings.value.webDavConfig)
    }
    
    // ==================== 云端同步功能 ====================
    
    private fun loadCloudConversationCount() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: return@launch
                
                val request = Request.Builder()
                    .url("$BASE_URL/sync/conversations")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@launch
                    val result = json.decodeFromString<CloudSyncResponse>(body)
                    if (result.success && result.data != null) {
                        // 只统计未删除的对话
                        cloudConversationCount.value = result.data.count { !it.isDeleted }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadCloudConversationCount failed: ${e.message}")
            }
        }
    }
    
    fun restoreFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            cloudSyncState.value = CloudSyncState.Loading
            
            try {
                val token = userSessionStore.getToken()
                if (token == null) {
                    cloudSyncState.value = CloudSyncState.Error("未登录")
                    return@launch
                }
                
                // 获取云端对话列表
                val request = Request.Builder()
                    .url("$BASE_URL/sync/conversations")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                
                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }
                
                if (!response.isSuccessful) {
                    cloudSyncState.value = CloudSyncState.Error("请求失败: ${response.code}")
                    return@launch
                }
                
                val body = response.body?.string()
                if (body == null) {
                    cloudSyncState.value = CloudSyncState.Error("响应为空")
                    return@launch
                }
                
                val result = json.decodeFromString<CloudSyncResponse>(body)
                if (!result.success || result.data == null) {
                    cloudSyncState.value = CloudSyncState.Error("解析失败")
                    return@launch
                }
                
                // 获取本地所有对话ID
                val localConversationIds = conversationRepo.getAllConversations()
                    .map { it.id.toString() }
                    .toSet()
                
                var restoredCount = 0
                var skippedCount = 0
                
                // 遍历云端对话
                for (cloudConv in result.data) {
                    // 跳过已删除的对话
                    if (cloudConv.isDeleted) {
                        skippedCount++
                        continue
                    }
                    
                    // 检查本地是否已存在
                    if (localConversationIds.contains(cloudConv.id)) {
                        skippedCount++
                        continue
                    }
                    
                    // 尝试恢复对话
                    try {
                        val conversation = parseCloudConversation(cloudConv)
                        if (conversation != null) {
                            conversationRepo.insertConversation(conversation)
                            restoredCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore conversation ${cloudConv.id}: ${e.message}")
                        skippedCount++
                    }
                }
                
                cloudSyncState.value = CloudSyncState.Success(restoredCount, skippedCount)
                
            } catch (e: Exception) {
                Log.e(TAG, "restoreFromCloud failed: ${e.message}")
                cloudSyncState.value = CloudSyncState.Error(e.message ?: "未知错误")
            }
        }
    }
    
    private fun parseCloudConversation(cloudConv: CloudConversation): Conversation? {
        // 解析云端对话为本地Conversation对象
        return try {
            val id = kotlin.uuid.Uuid.parse(cloudConv.id)
            
            // 创建基本的Conversation
            Conversation(
                id = id,
                title = cloudConv.title ?: "恢复的对话",
                isPinned = cloudConv.isPinned
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseCloudConversation failed: ${e.message}")
            null
        }
    }
    
    fun resetCloudSyncState() {
        cloudSyncState.value = CloudSyncState.Idle
    }

    fun restoreFromChatBox(file: File) {
        val importProviders = arrayListOf<ProviderSetting>()

        val jsonElements = JsonInstant.parseToJsonElement(file.readText()).jsonObject
        val settingsObj = jsonElements["settings"]?.jsonObject
        if (settingsObj != null) {
            settingsObj["providers"]?.jsonObject?.let { providers ->
                providers["openai"]?.jsonObject?.let { openai ->
                    val apiHost = openai["apiHost"]?.jsonPrimitive?.contentOrNull ?: "https://api.openai.com"
                    val apiKey = openai["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                    val models = openai["models"]?.jsonArray?.map { element ->
                        val modelId = element.jsonObject["modelId"]?.jsonPrimitive?.contentOrNull ?: ""
                        val capabilities =
                            element.jsonObject["capabilities"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull }
                                ?: emptyList()
                        Model(
                            modelId = modelId,
                            displayName = modelId,
                            inputModalities = buildList {
                                if (capabilities.contains("vision")) {
                                    add(Modality.IMAGE)
                                }
                            },
                            abilities = buildList {
                                if (capabilities.contains("tool_use")) {
                                    add(ModelAbility.TOOL)
                                }
                                if (capabilities.contains("reasoning")) {
                                    add(ModelAbility.REASONING)
                                }
                            }
                        )
                    } ?: emptyList()
                    if (apiKey.isNotBlank()) importProviders.add(
                        ProviderSetting.OpenAI(
                            name = "OpenAI",
                            baseUrl = "$apiHost/v1",
                            apiKey = apiKey,
                            models = models,
                        )
                    )
                }
                providers["claude"]?.jsonObject?.let { claude ->
                    val apiHost =
                        claude["apiHost"]?.jsonPrimitive?.contentOrNull ?: "https://api.anthropic.com"
                    val apiKey = claude["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (apiKey.isNotBlank()) importProviders.add(
                        ProviderSetting.Claude(
                            name = "Claude",
                            baseUrl = "${apiHost}/v1",
                            apiKey = apiKey,
                        )
                    )
                }
                providers["gemini"]?.jsonObject?.let { gemini ->
                    val apiHost = gemini["apiHost"]?.jsonPrimitive?.contentOrNull
                        ?: "https://generativelanguage.googleapis.com"
                    val apiKey = gemini["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (apiKey.isNotBlank()) importProviders.add(
                        ProviderSetting.Google(
                            name = "Gemini",
                            baseUrl = "$apiHost/v1beta",
                            apiKey = apiKey,
                        )
                    )
                }
            }
        }

        Log.i(TAG, "restoreFromChatBox: import ${importProviders.size} providers: $importProviders")

        updateSettings(
            settings.value.copy(
                providers = importProviders + settings.value.providers,
            )
        )
    }
}
