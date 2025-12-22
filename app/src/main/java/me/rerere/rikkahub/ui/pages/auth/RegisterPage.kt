package me.rerere.rikkahub.ui.pages.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.UserSessionStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.setting.PublicProviderResponse
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterPage(
    viewModel: AuthViewModel = koinViewModel(),
    settingVM: SettingVM = koinViewModel()
) {
    val navController = LocalNavController.current
    val registerState by viewModel.registerState.collectAsStateWithLifecycle()
    val settings by settingVM.settings.collectAsStateWithLifecycle()
    val userSessionStore: UserSessionStore = koinInject()
    val okHttpClient: OkHttpClient = koinInject()
    val json: Json = koinInject()
    val scope = rememberCoroutineScope()
    
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(registerState) {
        if (registerState is AuthState.Success) {
            // Sync public provider config
            scope.launch(Dispatchers.IO) {
                try {
                    val token = userSessionStore.getToken()
                    if (token != null) {
                        val request = Request.Builder()
                            .url("https://rikkahub.zeabur.app/api/public-provider")
                            .addHeader("Authorization", "Bearer $token")
                            .get()
                            .build()
                        
                        val response = okHttpClient.newCall(request).execute()
                        val responseBody = response.body?.string() ?: ""
                        
                        if (response.isSuccessful) {
                            val result = json.decodeFromString<PublicProviderResponse>(responseBody)
                            if (result.success && result.data != null && result.data.enabled && 
                                result.data.apiKey.isNotEmpty()) {
                                
                                val existingProvider = settings.providers.find { it.name == "公益提供商" }
                                val existingModels = existingProvider?.models ?: emptyList()
                                
                                val newProvider = ProviderSetting.OpenAI(
                                    id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                                    name = "公益提供商",
                                    apiKey = result.data.apiKey,
                                    baseUrl = result.data.baseUrl.ifEmpty { "https://api.openai.com/v1" },
                                    enabled = true,
                                    models = existingModels // Preserve user's models
                                )
                                
                                val newProviders = if (existingProvider != null) {
                                    // Update existing
                                    settings.providers.map { 
                                        if (it.name == "公益提供商") newProvider else it 
                                    }
                                } else {
                                    // Add new at top
                                    listOf(newProvider) + settings.providers
                                }
                                
                                settingVM.updateSettings(settings.copy(providers = newProviders))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Silently fail, don't block registration flow
                }
            }
            
            // Navigate to Chat screen after successful registration
            navController.navigate(Screen.Chat(id = Uuid.random().toString())) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("注册") },
                navigationIcon = { BackButton() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )
            
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) 
                    androidx.compose.ui.text.input.VisualTransformation.None 
                else 
                    PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Lucide.EyeOff else Lucide.Eye,
                            contentDescription = null
                        )
                    }
                }
            )
            
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("确认密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                isError = confirmPassword.isNotBlank() && password != confirmPassword
            )
            
            if (registerState is AuthState.Error) {
                Text(
                    text = (registerState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Button(
                onClick = { viewModel.register(email, username, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && username.isNotBlank() && 
                         password.isNotBlank() && password == confirmPassword &&
                         registerState !is AuthState.Loading
            ) {
                if (registerState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("注册")
                }
            }
            
            TextButton(
                onClick = { navController.popBackStack() }
            ) {
                Text("已有账号？返回登录")
            }
        }
    }
}
