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
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterPage(viewModel: AuthViewModel = koinViewModel()) {
    val navController = LocalNavController.current
    val registerState by viewModel.registerState.collectAsStateWithLifecycle()
    
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(registerState) {
        if (registerState is AuthState.Success) {
            navController.popBackStack()
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
