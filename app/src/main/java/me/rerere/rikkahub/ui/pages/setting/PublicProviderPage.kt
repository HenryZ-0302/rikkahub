package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.UserSessionStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Serializable
data class PublicProviderResponse(
    val success: Boolean,
    val data: PublicProviderData? = null
)

@Serializable
data class PublicProviderData(
    val enabled: Boolean,
    val apiKey: String = "",
    val baseUrl: String = "",
    val models: List<String> = emptyList(),
    val dailyLimit: Int = 20,
    val usedToday: Int = 0,
    val remainingToday: Int = 20
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProviderPage(
    vm: SettingVM = koinViewModel()
) {
    val userSessionStore: UserSessionStore = koinInject()
    val okHttpClient: OkHttpClient = koinInject()
    val json: Json = koinInject()
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var publicProviderData by remember { mutableStateOf<PublicProviderData?>(null) }

    // Check if public provider already exists in settings
    val existingPublicProvider = remember(settings.providers) {
        settings.providers.find { it.name == "公益提供商" }
    }
    
    // Fetch public provider config
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val token = userSessionStore.getToken()
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        error = "请先登录"
                        loading = false
                    }
                    return@launch
                }
                
                val request = Request.Builder()
                    .url("https://rikkahub.zeabur.app/api/public-provider")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val result = json.decodeFromString<PublicProviderResponse>(responseBody)
                        if (result.success && result.data != null) {
                            publicProviderData = result.data
                            
                            // If enabled and not already added, automatically add to providers
                            if (result.data.enabled && existingPublicProvider == null && 
                                result.data.apiKey.isNotEmpty()) {
                                val newProvider = ProviderSetting.OpenAI(
                                    id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                                    name = "公益提供商",
                                    apiKey = result.data.apiKey,
                                    baseUrl = result.data.baseUrl.ifEmpty { "https://api.openai.com/v1" },
                                    enabled = true,
                                    models = emptyList() // User will fetch models
                                )
                                vm.updateSettings(
                                    settings.copy(
                                        providers = listOf(newProvider) + settings.providers
                                    )
                                )
                            }
                        } else {
                            error = "公益提供商未启用"
                        }
                    } else {
                        error = "加载失败: ${response.code}"
                    }
                    loading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message ?: "未知错误"
                    loading = false
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("公益提供商") },
                navigationIcon = { BackButton() }
            )
        }
    ) { padding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Lucide.Info, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            publicProviderData != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AutoAIIcon(name = "Public", modifier = Modifier.size(48.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "公益提供商",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "免费使用，每日限额",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (publicProviderData!!.enabled) {
                                    Icon(
                                        Lucide.Check, null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Usage Stats
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "今日使用情况",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("已使用", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "${publicProviderData!!.usedToday}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column {
                                        Text("剩余", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "${publicProviderData!!.remainingToday}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column {
                                        Text("每日限额", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "${publicProviderData!!.dailyLimit}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { 
                                        publicProviderData!!.usedToday.toFloat() / 
                                            publicProviderData!!.dailyLimit.toFloat() 
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    
                    // Info Card
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "使用说明",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "• 公益提供商由管理员配置\n" +
                                    "• 每日额度在北京时间0点重置\n" +
                                    "• 请先点击\"拉取模型\"获取可用模型列表\n" +
                                    "• 选择模型后即可开始使用",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    // Provider Settings Link
                    if (existingPublicProvider != null) {
                        item {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    // Navigate to provider detail
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "已添加到服务商列表",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "模型数量: ${existingPublicProvider.models.size}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Icon(Lucide.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
