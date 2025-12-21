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
import okhttp3.Request

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
    
    fun clearUserConversations() {
        _userConversations.value = UiState.Idle
    }
}
