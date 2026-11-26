package com.sonify.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonify.player.data.SubsonicServerEntity
import com.sonify.player.ui.AccountActionState
import com.sonify.player.ui.theme.LocalHiFiColors

/**
 * Subsonic / OpenSubsonic 多服务器账号登录与管理弹窗（Spotify Dark 风格）：
 * - 查看、切换、测试连接、编辑、退出删除已保存账号；
 * - 添加/编辑表单支持服务器地址、用户名、密码（Keystore AES-256-GCM 加密落盘），
 *   以及可折叠的旧版认证（p=enc:）和自签名 HTTPS 证书信任选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsonicAccountsSheet(
    servers: List<SubsonicServerEntity>,
    actionState: AccountActionState,
    initialShowAddForm: Boolean = false,
    onDismiss: () -> Unit,
    onClearMessage: () -> Unit,
    onSelectServer: (SubsonicServerEntity) -> Unit,
    onTestServer: (Long) -> Unit,
    onDeleteServer: (Long) -> Unit,
    onLoginAndSave: (
        editingId: Long?,
        displayName: String,
        baseUrl: String,
        username: String,
        password: String,
        useLegacyAuth: Boolean,
        allowSelfSignedTls: Boolean,
        onDone: () -> Unit,
    ) -> Unit,
) {
    val colors = LocalHiFiColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showForm by remember { mutableStateOf(initialShowAddForm || servers.isEmpty()) }
    var editingServer by remember { mutableStateOf<SubsonicServerEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<SubsonicServerEntity?>(null) }

    // 表单字段
    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var userInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var useLegacyAuth by remember { mutableStateOf(false) }
    var allowSelfSigned by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    fun openAddForm() {
        onClearMessage()
        editingServer = null
        nameInput = ""
        urlInput = ""
        userInput = ""
        passInput = ""
        showPassword = false
        useLegacyAuth = false
        allowSelfSigned = false
        showAdvanced = false
        showForm = true
    }

    fun openEditForm(srv: SubsonicServerEntity) {
        onClearMessage()
        editingServer = srv
        nameInput = srv.name
        urlInput = srv.baseUrl
        userInput = srv.username
        passInput = ""
        showPassword = false
        useLegacyAuth = srv.useLegacyAuth
        allowSelfSigned = srv.allowSelfSignedTls
        showAdvanced = srv.useLegacyAuth || srv.allowSelfSignedTls
        showForm = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface1,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .padding(bottom = 24.dp),
        ) {
            // 顶部标题与模式切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (!showForm) "Subsonic 媒体服务器"
                        else if (editingServer != null) "编辑服务器账号"
                        else "登录 Subsonic 媒体库",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "兼容 Navidrome / Subsonic / Airsonic / Gonic / OpenSubsonic",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
                if (!showForm) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accent)
                            .clickable { openAddForm() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = colors.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "添加账号",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onAccent,
                        )
                    }
                } else if (servers.isNotEmpty()) {
                    TextButton(onClick = {
                        onClearMessage()
                        showForm = false
                    }) {
                        Text("账号列表", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }

            // 状态反馈胶囊（测试连接 / 登录成功 / 错误提示）
            actionState.statusMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                val tint = if (actionState.isError) colors.danger else colors.exclusiveGreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.14f))
                        .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = tint,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (!showForm) {
                // ==================== 已保存账号列表 ====================
                if (servers.isEmpty()) {
                    Text(
                        text = "尚未登录任何 Subsonic 服务器账号",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    servers.forEach { srv ->
                        val isTesting = actionState.testingServerId == srv.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (srv.isActive) colors.surface2 else colors.canvas.copy(alpha = 0.55f))
                                .border(
                                    width = 1.dp,
                                    color = if (srv.isActive) colors.exclusiveGreen.copy(alpha = 0.45f) else colors.hairline,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    onSelectServer(srv)
                                    onDismiss()
                                }
                                .padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (srv.isActive) colors.exclusiveGreen else colors.textMuted)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = srv.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (srv.isActive) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "当前使用",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.exclusiveGreen,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(colors.exclusiveGreen.copy(alpha = 0.14f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${srv.username} @ ${srv.baseUrl}",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (srv.serverVersion.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = srv.serverVersion,
                                            fontSize = 11.sp,
                                            color = colors.accent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(end = 4.dp),
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onTestServer(srv.id) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.NetworkCheck,
                                            contentDescription = "测试连接",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("测试", fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { openEditForm(srv) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "编辑",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("编辑", fontSize = 12.sp, color = colors.textSecondary)
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { confirmDelete = srv }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "退出删除",
                                        tint = colors.danger,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("移除", fontSize = 12.sp, color = colors.danger)
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== 添加 / 编辑登录表单 ====================
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.hairline,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedLabelColor = colors.accent,
                    unfocusedLabelColor = colors.textSecondary,
                    cursorColor = colors.accent,
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("服务器地址 *") },
                    placeholder = { Text("https://music.example.com 或 http://192.168.1.10:4533", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    label = { Text("用户名 *") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it },
                    label = {
                        Text(
                            if (editingServer != null) "密码（留空保持原已保存密码）"
                            else "密码 *"
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                                tint = colors.textSecondary,
                            )
                        }
                    },
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("显示名称（选填）") },
                    placeholder = { Text("例如：家里 Navidrome / 私有云 NAS", fontSize = 12.sp) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                // 密钥安全说明
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.canvas.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = colors.exclusiveGreen,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "密码将使用 Android Keystore AES-256-GCM 硬件密钥加密保存在本地，请求默认采用随机盐值 MD5 Token 签名。",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        lineHeight = 15.sp,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 可折叠高级选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "高级协议与证书选项",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.textSecondary,
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface2.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text("旧版认证兼容（p=enc:）", fontSize = 13.sp, color = colors.textPrimary)
                                Text(
                                    "仅当旧版 Subsonic 或 LDAP 账号拒绝随机盐 Token 时开启",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                )
                            }
                            Switch(
                                checked = useLegacyAuth,
                                onCheckedChange = { useLegacyAuth = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accent,
                                ),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text("信任自签名 HTTPS 证书", fontSize = 13.sp, color = colors.textPrimary)
                                Text(
                                    "仅对该服务器生效，适用于自建 NAS 私有 HTTPS 反向代理",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                )
                            }
                            Switch(
                                checked = allowSelfSigned,
                                onCheckedChange = { allowSelfSigned = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accent,
                                ),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // 主操作按钮：测试连接并保存登录
                val canSubmit = !actionState.isSubmitting &&
                    urlInput.isNotBlank() &&
                    userInput.isNotBlank() &&
                    (passInput.isNotEmpty() || editingServer != null)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canSubmit) colors.accent else colors.surface3)
                        .clickable(enabled = canSubmit) {
                            onLoginAndSave(
                                editingServer?.id,
                                nameInput,
                                urlInput,
                                userInput,
                                passInput,
                                useLegacyAuth,
                                allowSelfSigned,
                            ) {
                                showForm = false
                                editingServer = null
                                onDismiss()
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (actionState.isSubmitting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = colors.onAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "正在校验连接并加密保存…",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onAccent,
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (canSubmit) colors.onAccent else colors.textMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (editingServer != null) "校验并更新账号" else "测试连接并保存登录",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canSubmit) colors.onAccent else colors.textMuted,
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = colors.surface1,
            title = { Text("退出并移除账号", color = colors.textPrimary) },
            text = {
                Text(
                    "确定移除「${target.name}」（${target.username}）吗？这将清除本地已加密保存的凭据及该服务器的远程元数据与封面缓存，不影响服务器上的音乐文件。",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteServer(target.id)
                        confirmDelete = null
                    }
                ) {
                    Text("确认移除", color = colors.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("取消", color = colors.textSecondary)
                }
            },
        )
    }
}
