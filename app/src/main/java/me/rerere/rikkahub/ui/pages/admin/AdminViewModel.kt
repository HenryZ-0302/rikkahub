package me.rerere.rikkahub.ui.pages.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.UserSessionStore
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: Exception) : UiState<Nothing>()
}

@Serializable
data class AdminUser(
    val id: String,
    val email: String,
    val username: String,
    val isAdmin: Boolean = false,
    val isDisabled: Boolean = false,
    val conversationCount: Int = 0,
    val createdAt: String = ""
)

@Serializable
data class UsersResponse(
    val success: Boolean,
    val data: UsersData? = null
)

@Serializable
data class UsersData(
    val users: List<AdminUser>,
    val pagination: Pagination
)

@Serializable
data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
)

@Serializable
data class AdminConversation(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val isDeleted: Boolean = false
)

@Serializable
data class ConversationsResponse(
    val success: Boolean,
    val data: ConversationsData? = null
)

@Serializable
data class ConversationsData(
    val conversations: List<AdminConversation>,
    val pagination: Pagination
)

@Serializable
data class MessagePart(
    val type: String,     // "text" or "image"
    val text: String? = null,
    val url: String? = null
)

@Serializable
data class ConversationMessage(
    val id: String,
    val role: String,
    val content: String = "",  // Keep for backward compatibility
    val parts: List<MessagePart> = emptyList(),
    val createdAt: String
)

@Serializable
data class ConversationMessagesResponse(
    val success: Boolean,
    val data: ConversationMessagesData? = null
)

@Serializable
data class ConversationMessagesData(
    val id: String,
    val title: String,
    val messages: List<ConversationMessage>
)

@Serializable
data class ConfigItem(
    val key: String,
    val value: String
)

@Serializable
data class ConfigResponse(
    val success: Boolean,
    val data: List<ConfigItem>? = null
)

class AdminViewModel(
    private val userSessionStore: UserSessionStore,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : ViewModel() {
    
    companion object {
        private const val BASE_URL = "https://rikkahub.zeabur.app/api"
    }
    
    private val _users = MutableStateFlow<UiState<List<AdminUser>>>(UiState.Idle)
    val users: StateFlow<UiState<List<AdminUser>>> = _users.asStateFlow()
    
    private val _userConversations = MutableStateFlow<UiState<List<AdminConversation>>>(UiState.Idle)
    val userConversations: StateFlow<UiState<List<AdminConversation>>> = _userConversations.asStateFlow()
    
    private val _allowRegistration = MutableStateFlow(true)
    val allowRegistration: StateFlow<Boolean> = _allowRegistration.asStateFlow()
    
    private val _publicProviderEnabled = MutableStateFlow(false)
    val publicProviderEnabled: StateFlow<Boolean> = _publicProviderEnabled.asStateFlow()
    
    private val _publicProviderApiKey = MutableStateFlow("")
    val publicProviderApiKey: StateFlow<String> = _publicProviderApiKey.asStateFlow()
    
    private val _publicProviderBaseUrl = MutableStateFlow("https://api.openai.com/v1")
    val publicProviderBaseUrl: StateFlow<String> = _publicProviderBaseUrl.asStateFlow()
    
    // 公告相关
    private val _announcementContent = MutableStateFlow("")
    val announcementContent: StateFlow<String> = _announcementContent.asStateFlow()
    
    private val _announcementReadTime = MutableStateFlow(5)
    val announcementReadTime: StateFlow<Int> = _announcementReadTime.asStateFlow()
    
    private val _conversationMessages = MutableStateFlow<UiState<List<ConversationMessage>>>(UiState.Idle)
    val conversationMessages: StateFlow<UiState<List<ConversationMessage>>> = _conversationMessages.asStateFlow()
    
    fun loadUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            _users.value = UiState.Loading
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val request = Request.Builder()
                    .url("$BASE_URL/admin/users?limit=100")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val usersResponse = json.decodeFromString<UsersResponse>(responseBody)
                    if (usersResponse.success && usersResponse.data != null) {
                        _users.value = UiState.Success(usersResponse.data.users)
                    } else {
                        _users.value = UiState.Error(Exception("Failed to load users"))
                    }
                } else {
                    _users.value = UiState.Error(Exception("HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                _users.value = UiState.Error(e)
            }
        }
    }
    
    fun loadUserConversations(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _userConversations.value = UiState.Loading
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val request = Request.Builder()
                    .url("$BASE_URL/admin/users/$userId/conversations?limit=100")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val convResponse = json.decodeFromString<ConversationsResponse>(responseBody)
                    if (convResponse.success && convResponse.data != null) {
                        _userConversations.value = UiState.Success(convResponse.data.conversations)
                    } else {
                        _userConversations.value = UiState.Error(Exception("Failed to load conversations"))
                    }
                } else {
                    _userConversations.value = UiState.Error(Exception("HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                _userConversations.value = UiState.Error(e)
            }
        }
    }
    
    fun clearConversations(userId: String, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val request = Request.Builder()
                    .url("$BASE_URL/admin/users/$userId/conversations?type=$type")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    loadUserConversations(userId)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun toggleUserStatus(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val request = Request.Builder()
                    .url("$BASE_URL/admin/users/$userId/toggle-status")
                    .addHeader("Authorization", "Bearer $token")
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    loadUsers() // Refresh user list
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun deleteUser(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val request = Request.Builder()
                    .url("$BASE_URL/admin/users/$userId")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    loadUsers() // Refresh user list
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val request = Request.Builder()
                    .url("$BASE_URL/admin/config")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val configResponse = json.decodeFromString<ConfigResponse>(responseBody)
                    if (configResponse.success && configResponse.data != null) {
                        configResponse.data.forEach { config ->
                            when (config.key) {
                                "allow_registration" -> _allowRegistration.value = config.value != "false"
                                "public_provider_enabled" -> _publicProviderEnabled.value = config.value == "true"
                                "public_provider_api_key" -> _publicProviderApiKey.value = config.value
                                "public_provider_base_url" -> _publicProviderBaseUrl.value = config.value.ifEmpty { "https://api.openai.com/v1" }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun toggleRegistration() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val newValue = if (_allowRegistration.value) "false" else "true"
                val requestBody = """{"key":"allow_registration","value":"$newValue"}"""
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/admin/config")
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    _allowRegistration.value = newValue == "true"
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun togglePublicProvider() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val newValue = if (_publicProviderEnabled.value) "false" else "true"
                val requestBody = """{"key":"public_provider_enabled","value":"$newValue"}"""
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/admin/config")
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    _publicProviderEnabled.value = newValue == "true"
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updatePublicProviderApiKey(apiKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val requestBody = """{"key":"public_provider_api_key","value":"$apiKey"}"""
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/admin/config")
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    _publicProviderApiKey.value = apiKey
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updatePublicProviderBaseUrl(baseUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val requestBody = """{"key":"public_provider_base_url","value":"$baseUrl"}"""
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/admin/config")
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    _publicProviderBaseUrl.value = baseUrl
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun loadConversationMessages(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _conversationMessages.value = UiState.Loading
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                val request = Request.Builder()
                    .url("$BASE_URL/admin/conversations/$conversationId/messages")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val messagesResponse = json.decodeFromString<ConversationMessagesResponse>(responseBody)
                    if (messagesResponse.success && messagesResponse.data != null) {
                        _conversationMessages.value = UiState.Success(messagesResponse.data.messages)
                    } else {
                        _conversationMessages.value = UiState.Error(Exception("Failed to load messages"))
                    }
                } else {
                    _conversationMessages.value = UiState.Error(Exception("HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                _conversationMessages.value = UiState.Error(e)
            }
        }
    }
    
    fun clearConversationMessages() {
        _conversationMessages.value = UiState.Idle
    }
    
    fun clearUserConversations() {
        _userConversations.value = UiState.Idle
    }
    
    // 公告相关方法
    fun updateAnnouncementContent(content: String) {
        _announcementContent.value = content
    }
    
    fun updateAnnouncementReadTime(time: Int) {
        _announcementReadTime.value = time
    }
    
    fun saveAnnouncement() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: throw Exception("Not logged in")
                
                // 生成新版本号
                val version = System.currentTimeMillis().toString()
                
                // 保存公告内容
                saveConfig(token, "announcement_content", _announcementContent.value)
                // 保存阅读时间
                saveConfig(token, "announcement_read_time", _announcementReadTime.value.toString())
                // 保存版本号
                saveConfig(token, "announcement_version", version)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun saveConfig(token: String, key: String, value: String) {
        val requestBody = """{"key":"$key","value":"${value.replace("\"", "\\\"").replace("\n", "\\n")}"}"""
        val request = Request.Builder()
            .url("$BASE_URL/admin/config")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        okHttpClient.newCall(request).execute()
    }
    
    fun loadAnnouncementConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken() ?: return@launch
                val request = Request.Builder()
                    .url("$BASE_URL/admin/config")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@launch
                    val configResponse = json.decodeFromString<ConfigResponse>(body)
                    if (configResponse.success && configResponse.data != null) {
                        configResponse.data.forEach { item ->
                            when (item.key) {
                                "announcement_content" -> _announcementContent.value = item.value
                                "announcement_read_time" -> _announcementReadTime.value = item.value.toIntOrNull() ?: 5
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
