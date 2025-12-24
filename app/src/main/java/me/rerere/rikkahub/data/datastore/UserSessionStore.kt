package me.rerere.rikkahub.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

/**
 * User session storage for authentication
 */
class UserSessionStore(private val context: Context) {
    
    companion object {
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_IS_ADMIN = booleanPreferencesKey("is_admin")
    }
    
    val isLoggedIn: Flow<Boolean> = context.userSessionDataStore.data.map { prefs ->
        prefs[KEY_IS_LOGGED_IN] == true
    }
    
    val userId: Flow<String?> = context.userSessionDataStore.data.map { prefs ->
        prefs[KEY_USER_ID]
    }
    
    val email: Flow<String?> = context.userSessionDataStore.data.map { prefs ->
        prefs[KEY_EMAIL]
    }
    
    val username: Flow<String?> = context.userSessionDataStore.data.map { prefs ->
        prefs[KEY_USERNAME]
    }
    
    val token: Flow<String?> = context.userSessionDataStore.data.map { prefs ->
        prefs[KEY_TOKEN]
    }
    
    val isAdmin: Flow<Boolean> = context.userSessionDataStore.data.map { prefs ->
        prefs[KEY_IS_ADMIN] == true
    }
    
    suspend fun getToken(): String? {
        return context.userSessionDataStore.data.first()[KEY_TOKEN]
    }
    
    suspend fun getRefreshToken(): String? {
        return context.userSessionDataStore.data.first()[KEY_REFRESH_TOKEN]
    }
    
    suspend fun saveSession(
        userId: String,
        email: String,
        username: String,
        token: String,
        refreshToken: String,
        isAdmin: Boolean = false
    ) {
        context.userSessionDataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_USER_ID] = userId
            prefs[KEY_EMAIL] = email
            prefs[KEY_USERNAME] = username
            prefs[KEY_TOKEN] = token
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_IS_ADMIN] = isAdmin
        }
    }
    
    suspend fun updateToken(token: String) {
        context.userSessionDataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
        }
    }
    
    suspend fun clearSession() {
        context.userSessionDataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    fun getAuthHeader(): Flow<String?> = token.map { t ->
        t?.let { "Bearer $it" }
    }
    
    // 公告相关
    companion object AnnouncementKeys {
        private val KEY_READ_ANNOUNCEMENT_VERSION = stringPreferencesKey("read_announcement_version")
    }
    
    val readAnnouncementVersion: Flow<String?> = context.userSessionDataStore.data.map { prefs ->
        prefs[AnnouncementKeys.KEY_READ_ANNOUNCEMENT_VERSION]
    }
    
    suspend fun getReadAnnouncementVersion(): String? {
        return context.userSessionDataStore.data.first()[AnnouncementKeys.KEY_READ_ANNOUNCEMENT_VERSION]
    }
    
    suspend fun saveReadAnnouncementVersion(version: String) {
        context.userSessionDataStore.edit { prefs ->
            prefs[AnnouncementKeys.KEY_READ_ANNOUNCEMENT_VERSION] = version
        }
    }
}
