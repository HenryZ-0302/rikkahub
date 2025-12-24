package me.rerere.rikkahub.ui.components.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.UserSessionStore
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.compose.koinInject

private const val TAG = "AnnouncementDialog"

@Serializable
private data class AnnouncementResponse(
    val success: Boolean,
    val data: AnnouncementData?
)

@Serializable
private data class AnnouncementData(
    val content: String,
    val readTime: Int,
    val version: String
)

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun AnnouncementChecker(
    isLoggedIn: Boolean
) {
    val userSessionStore: UserSessionStore = koinInject()
    val okHttpClient: OkHttpClient = koinInject()
    
    var showDialog by remember { mutableStateOf(false) }
    var announcement by remember { mutableStateOf<AnnouncementData?>(null) }
    
    // 登录后检查公告
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        
        try {
            val token = userSessionStore.getToken() ?: return@LaunchedEffect
            val readVersion = userSessionStore.getReadAnnouncementVersion()
            
            // 获取公告
            val request = Request.Builder()
                .url("https://rikkahub.zeabur.app/api/announcement")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@LaunchedEffect
                val result = json.decodeFromString<AnnouncementResponse>(body)
                
                if (result.success && result.data != null) {
                    val data = result.data
                    // 检查是否需要显示
                    if (data.version.isNotBlank() && data.version != readVersion) {
                        announcement = data
                        showDialog = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check announcement: ${e.message}")
        }
    }
    
    // 显示公告弹窗
    if (showDialog && announcement != null) {
        val coroutineScope = rememberCoroutineScope()
        AnnouncementDialog(
            announcement = announcement!!,
            onDismiss = {
                showDialog = false
            },
            onConfirm = {
                // 保存已读版本
                coroutineScope.launch {
                    userSessionStore.saveReadAnnouncementVersion(announcement!!.version)
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun AnnouncementDialog(
    announcement: AnnouncementData,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var remainingSeconds by remember { mutableStateOf(announcement.readTime) }
    val canConfirm = remainingSeconds <= 0
    
    // 倒计时
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }
    
    Dialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "公告",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownBlock(
                        content = announcement.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (canConfirm) {
                        Text("我已阅读")
                    } else {
                        Text("请阅读 (${remainingSeconds}s)")
                    }
                }
            }
        }
    }
}
