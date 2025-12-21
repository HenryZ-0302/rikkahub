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
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPage(viewModel: AdminViewModel = koinViewModel()) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val userConversations by viewModel.userConversations.collectAsStateWithLifecycle()
    
    var selectedUser by remember { mutableStateOf<AdminUser?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.loadUsers()
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
                        items(state.data) { user ->
                            UserCard(
                                user = user,
                                onClick = {
                                    selectedUser = user
                                    viewModel.loadUserConversations(user.id)
                                }
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
                                        Card(modifier = Modifier.fillMaxWidth()) {
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
                                                Card(modifier = Modifier.fillMaxWidth()) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(conv.title, maxLines = 2)
                                                        Text(conv.updatedAt.take(10),
                                                            style = MaterialTheme.typography.labelSmall)
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
}

@Composable
private fun UserCard(user: AdminUser, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Lucide.Users, null)
            Column(modifier = Modifier.weight(1f)) {
                Text(user.username, fontWeight = FontWeight.Medium)
                Text(user.email, style = MaterialTheme.typography.bodySmall)
            }
            Text("${user.conversationCount} 对话", style = MaterialTheme.typography.labelMedium)
        }
    }
}
