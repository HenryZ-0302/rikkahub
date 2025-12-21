package me.rerere.rikkahub.ui.pages.auth

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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class AuthResponse(
    val success: Boolean,
    val data: AuthData? = null,
    val error: String? = null
)

@Serializable
data class AuthData(
    val user: UserData,
    val token: String,
    val refreshToken: String
)

@Serializable
data class UserData(
    val id: String,
    val email: String,
    val username: String,
    val admin: AdminData? = null
)

@Serializable
data class AdminData(
    val id: String
)

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val userSessionStore: UserSessionStore,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : ViewModel() {
    
    companion object {
        private const val BASE_URL = "https://rikkahub.zeabur.app/api"
    }
    
    private val _loginState = MutableStateFlow<AuthState>(AuthState.Idle)
    val loginState: StateFlow<AuthState> = _loginState.asStateFlow()
    
    private val _registerState = MutableStateFlow<AuthState>(AuthState.Idle)
    val registerState: StateFlow<AuthState> = _registerState.asStateFlow()
    
    val isLoggedIn = userSessionStore.isLoggedIn
    val username = userSessionStore.username
    val email = userSessionStore.email
    val isAdmin = userSessionStore.isAdmin
    
    fun login(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = AuthState.Loading
            try {
                val requestBody = """{"email":"$email","password":"$password"}"""
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/auth/login")
                    .post(requestBody)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val authResponse = json.decodeFromString<AuthResponse>(responseBody)
                    if (authResponse.success && authResponse.data != null) {
                        userSessionStore.saveSession(
                            userId = authResponse.data.user.id,
                            email = authResponse.data.user.email,
                            username = authResponse.data.user.username,
                            token = authResponse.data.token,
                            refreshToken = authResponse.data.refreshToken,
                            isAdmin = authResponse.data.user.admin != null
                        )
                        _loginState.value = AuthState.Success("Login successful")
                    } else {
                        _loginState.value = AuthState.Error(authResponse.error ?: "Login failed")
                    }
                } else {
                    _loginState.value = AuthState.Error("Login failed: ${response.code}")
                }
            } catch (e: Exception) {
                _loginState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun register(email: String, username: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _registerState.value = AuthState.Loading
            try {
                val requestBody = """{"email":"$email","username":"$username","password":"$password"}"""
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$BASE_URL/auth/register")
                    .post(requestBody)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val authResponse = json.decodeFromString<AuthResponse>(responseBody)
                    if (authResponse.success && authResponse.data != null) {
                        userSessionStore.saveSession(
                            userId = authResponse.data.user.id,
                            email = authResponse.data.user.email,
                            username = authResponse.data.user.username,
                            token = authResponse.data.token,
                            refreshToken = authResponse.data.refreshToken,
                            isAdmin = authResponse.data.user.admin != null
                        )
                        _registerState.value = AuthState.Success("Registration successful")
                    } else {
                        _registerState.value = AuthState.Error(authResponse.error ?: "Registration failed")
                    }
                } else {
                    _registerState.value = AuthState.Error("Registration failed: ${response.code}")
                }
            } catch (e: Exception) {
                _registerState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            userSessionStore.clearSession()
            _loginState.value = AuthState.Idle
        }
    }
    
    fun resetLoginState() {
        _loginState.value = AuthState.Idle
    }
    
    fun resetRegisterState() {
        _registerState.value = AuthState.Idle
    }
}
