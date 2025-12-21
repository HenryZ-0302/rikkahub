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
    
    fun clearUserConversations() {
        _userConversations.value = UiState.Idle
    }
}
