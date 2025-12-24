package me.rerere.rikkahub.ui.pages.admin

import androidx.compose.foundation.clickable
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
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.UserX
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPage(viewModel: AdminViewModel = koinViewModel()) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val userConversations by viewModel.userConversations.collectAsStateWithLifecycle()
    val allowRegistration by viewModel.allowRegistration.collectAsStateWithLifecycle()
    val conversationMessages by viewModel.conversationMessages.collectAsStateWithLifecycle()
    
    var selectedUser by remember { mutableStateOf<AdminUser?>(null) }
    var selectedConversation by remember { mutableStateOf<AdminConversation?>(null) }
    var userToDelete by remember { mutableStateOf<AdminUser?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    
    // Delete user confirmation dialog
    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("删除用户") },
            text = { Text("确定要删除用户 ${userToDelete?.username} 吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        userToDelete?.let { viewModel.deleteUser(it.id) }
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("取消") }
            }
        )
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadUsers()
        viewModel.loadConfig()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理面板") },
                navigationIcon = { BackButton() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = users) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Registration toggle
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("允许新用户注册", fontWeight = FontWeight.Medium)
                                        Text(
                                            if (allowRegistration) "新用户可以注册账户" else "注册已关闭",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Switch(
                                        checked = allowRegistration,
                                        onCheckedChange = { viewModel.toggleRegistration() }
                                    )
                                }
                            }
                        }
                        
                        // Public provider config
                        item {
                            PublicProviderConfigCard(viewModel = viewModel)
                        }
                        
                        item {
                            Text(
                                "用户列表 (${state.data.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        items(state.data) { user ->
                            UserCard(
                                user = user,
                                onClick = {
                                    selectedUser = user
                                    viewModel.loadUserConversations(user.id)
                                },
                                onToggleStatus = { viewModel.toggleUserStatus(user.id) },
                                onDelete = { userToDelete = user }
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("加载失败: ${state.error.message}")
                            Button(onClick = { viewModel.loadUsers() }) {
                                Text("重试")
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
    
    // User conversations dialog
    if (selectedUser != null) {
        var showClearActiveDialog by remember { mutableStateOf(false) }
        var showClearDeletedDialog by remember { mutableStateOf(false) }

        if (showClearActiveDialog) {
            AlertDialog(
                onDismissRequest = { showClearActiveDialog = false },
                title = { Text("清空活跃对话") },
                text = { Text("确定要清空该用户的所有活跃对话吗？") },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedUser?.let { viewModel.clearConversations(it.id, "active") }
                            showClearActiveDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("清空") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearActiveDialog = false }) { Text("取消") }
                }
            )
        }

        if (showClearDeletedDialog) {
            AlertDialog(
                onDismissRequest = { showClearDeletedDialog = false },
                title = { Text("永久清空回收站") },
                text = { Text("确定要永久清空该用户的所有已删除对话吗？") },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedUser?.let { viewModel.clearConversations(it.id, "deleted") }
                            showClearDeletedDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("永久清空") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDeletedDialog = false }) { Text("取消") }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { 
                selectedUser = null
                viewModel.clearUserConversations()
            },
            title = { Text("${selectedUser?.username} 的对话") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    when (val convState = userConversations) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                        is UiState.Success -> {
                            val activeConversations = convState.data.filter { !it.isDeleted }
                            val deletedConversations = convState.data.filter { it.isDeleted }
                            var showDeletedExpanded by remember { mutableStateOf(false) }
                            
                            if (convState.data.isEmpty()) {
                                Text("该用户没有对话记录", modifier = Modifier.padding(16.dp))
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (activeConversations.isNotEmpty()) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("活跃对话 (${activeConversations.size})", 
                                                    style = MaterialTheme.typography.titleSmall)
                                                IconButton(
                                                    onClick = { showClearActiveDialog = true },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Lucide.Trash2, null, 
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    items(activeConversations) { conv ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                selectedConversation = conv
                                                viewModel.loadConversationMessages(conv.id)
                                            }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(conv.title, fontWeight = FontWeight.Medium, maxLines = 2)
                                                Text(conv.updatedAt.take(10), 
                                                    style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    if (deletedConversations.isNotEmpty()) {
                                        item {
                                            Card(
                                                modifier = Modifier.fillMaxWidth()
                                                    .clickable { showDeletedExpanded = !showDeletedExpanded }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("已删除 (${deletedConversations.size})", 
                                                        modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = { showClearDeletedDialog = true },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Lucide.Trash2, null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp))
                                                    }
                                                    Icon(
                                                        if (showDeletedExpanded) Lucide.ChevronUp 
                                                        else Lucide.ChevronDown, null)
                                                }
                                            }
                                        }
                                        if (showDeletedExpanded) {
                                            items(deletedConversations) { conv ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth().clickable {
                                                        selectedConversation = conv
                                                        viewModel.loadConversationMessages(conv.id)
                                                    },
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(conv.title, maxLines = 2,
                                                            color = MaterialTheme.colorScheme.onErrorContainer)
                                                        Text(conv.updatedAt.take(10),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is UiState.Error -> {
                            Text("加载失败: ${convState.error.message}", 
                                color = MaterialTheme.colorScheme.error)
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    selectedUser = null
                    viewModel.clearUserConversations()
                }) { Text("关闭") }
            }
        )
    }
    
    // Conversation messages dialog
    if (selectedConversation != null) {
        AlertDialog(
            onDismissRequest = { 
                selectedConversation = null
                viewModel.clearConversationMessages()
            },
            title = { 
                Text(
                    text = selectedConversation?.title ?: "对话详情",
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 450.dp)) {
                    when (val msgState = conversationMessages) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                        is UiState.Success -> {
                            if (msgState.data.isEmpty()) {
                                Text("该对话没有消息", modifier = Modifier.padding(16.dp))
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(msgState.data) { msg ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (msg.role == "user") 
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = if (msg.role == "user") "用户" else "助手",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = try {
                                                            // Try to parse as ISO date
                                                            if (msg.createdAt.contains("-")) {
                                                                msg.createdAt.take(10)
                                                            } else {
                                                                // Parse as timestamp and format
                                                                val timestamp = msg.createdAt.toLongOrNull() ?: 0L
                                                                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                                                    .format(java.util.Date(timestamp))
                                                            }
                                                        } catch (e: Exception) {
                                                            msg.createdAt.take(10)
                                                        },
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                // Display parts: text and images
                                                if (msg.parts.isNotEmpty()) {
                                                    msg.parts.forEach { part ->
                                                        when (part.type) {
                                                            "text" -> {
                                                                part.text?.takeIf { it.trim().isNotEmpty() }?.let { text ->
                                                                    Text(
                                                                        text = text.trim(),
                                                                        style = MaterialTheme.typography.bodySmall
                                                                    )
                                                                }
                                                            }
                                                            "image" -> {
                                                                part.url?.let { url ->
                                                                    AsyncImage(
                                                                        model = url,
                                                                        contentDescription = null,
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .heightIn(max = 200.dp)
                                                                            .padding(vertical = 4.dp)
                                                                            .clip(RoundedCornerShape(8.dp))
                                                                            .clickable { previewImageUrl = url },
                                                                        contentScale = ContentScale.Fit
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else if (msg.content.trim().isNotEmpty()) {
                                                    // Fallback to content field for backward compatibility
                                                    Text(
                                                        text = msg.content.trim(),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is UiState.Error -> {
                            Text("加载失败: ${msgState.error.message}", 
                                color = MaterialTheme.colorScheme.error)
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    selectedConversation = null
                    viewModel.clearConversationMessages()
                }) { Text("关闭") }
            }
        )
    }

    if (previewImageUrl != null) {
        ImagePreviewDialog(
            images = listOf(previewImageUrl!!),
            onDismissRequest = { previewImageUrl = null }
        )
    }
}

@Composable
private fun UserCard(
    user: AdminUser, 
    onClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (user.isDisabled) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (user.isAdmin) Lucide.Shield 
                else if (user.isDisabled) Lucide.UserX 
                else Lucide.Users, 
                null,
                tint = when {
                    user.isAdmin -> MaterialTheme.colorScheme.primary
                    user.isDisabled -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.username, fontWeight = FontWeight.Medium)
                    if (user.isAdmin) {
                        Text(" (管理员)", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (user.isDisabled) {
                        Text(" (已禁用)", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(user.email, style = MaterialTheme.typography.bodySmall)
            }
            Text("${user.conversationCount} 对话", style = MaterialTheme.typography.labelMedium)
            // Don't show buttons for admin users
            if (!user.isAdmin) {
                IconButton(
                    onClick = onToggleStatus,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (user.isDisabled) Lucide.Check else Lucide.Ban,
                        contentDescription = if (user.isDisabled) "启用" else "禁用",
                        tint = if (user.isDisabled) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = "删除用户",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicProviderConfigCard(viewModel: AdminViewModel) {
    val publicProviderEnabled by viewModel.publicProviderEnabled.collectAsStateWithLifecycle()
    val apiKey by viewModel.publicProviderApiKey.collectAsStateWithLifecycle()
    val baseUrl by viewModel.publicProviderBaseUrl.collectAsStateWithLifecycle()
    
    var editedApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var editedBaseUrl by remember(baseUrl) { mutableStateOf(baseUrl) }
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("公益提供商", fontWeight = FontWeight.Medium)
                    Text(
                        if (publicProviderEnabled) "已启用 - 用户可使用公益API" else "已关闭",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = publicProviderEnabled,
                    onCheckedChange = { viewModel.togglePublicProvider() }
                )
            }
            
            if (publicProviderEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = editedApiKey,
                    onValueChange = { editedApiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (editedApiKey != apiKey) {
                            TextButton(onClick = { viewModel.updatePublicProviderApiKey(editedApiKey) }) {
                                Text("保存")
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = editedBaseUrl,
                    onValueChange = { editedBaseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (editedBaseUrl != baseUrl) {
                            TextButton(onClick = { viewModel.updatePublicProviderBaseUrl(editedBaseUrl) }) {
                                Text("保存")
                            }
                        }
                    }
                )
            }
        }
    }
}
