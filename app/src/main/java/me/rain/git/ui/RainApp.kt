@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package me.rain.git.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RainApp(
    model: RainViewModel,
    hasStoragePermission: Boolean,
    onChooseDirectory: () -> Unit,
    onGrantStorage: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (model.themeMode) { 1 -> false; 2 -> true; else -> systemDark }
    RainTheme(dark) {
        TransparentSystemBars()

        
        var permissionPromptShown by androidx.compose.runtime.saveable.rememberSaveable {
            androidx.compose.runtime.mutableStateOf(false)
        }
        if (!hasStoragePermission && !permissionPromptShown) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { permissionPromptShown = true },
                title = { androidx.compose.material3.Text("需要文件访问权限") },
                text = {
                    androidx.compose.material3.Text(
                        "RainGit 需要「所有文件访问」权限才能读写仓库目录（如 /sdcard/RainGit）。" +
                        "请在下一步弹出的系统设置页中开启。"
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        permissionPromptShown = true
                        onGrantStorage()
                    }) { androidx.compose.material3.Text("去授权") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { permissionPromptShown = true }) {
                        androidx.compose.material3.Text("稍后")
                    }
                }
            )
        }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(model.message) {
            val message = model.message ?: return@LaunchedEffect
            snack.showSnackbar(message, withDismissAction = true)
            if (model.message == message) model.clearMessage()
        }
        val loggedIn = model.account != null
        val pageState = rememberSaveableStateHolder()
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        BackHandler(enabled = loggedIn) {
            model.handleBack()
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                if (loggedIn) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        navigationIcon = {
                            if (model.screen == "detail") IconButton(onClick = { model.handleBack() }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    if (model.screen == "detail") model.selectedRepo?.name ?: model.selectedDir?.name ?: "本地仓库"
                                    else when (model.screen) { "home" -> "RainGit"; "local" -> "本地仓库"; "actions" -> "构建中心"; else -> "设置" },
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (model.screen == "detail") {
                                    Text(model.selectedRepo?.owner ?: "Local Git", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (model.screen == "home") {
                                    Text("@${model.account?.login.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        actions = {
                            if (model.screen != "settings") {
                                IconButton(onClick = {
                                    when (model.screen) {
                                        "detail" -> when (model.detailTab) { 2 -> model.refreshActions(); 3 -> model.refreshCollaboration(); else -> model.refreshLocal() }
                                        "local" -> model.syncAllLocal()
                                        else -> model.refreshRepos()
                                    }
                                }, enabled = !model.busy) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新") }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (loggedIn && model.screen != "detail" && !keyboardVisible) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                        listOf(
                            Triple("home", "仓库", Icons.Rounded.Folder),
                            Triple("local", "本地", Icons.Rounded.Code),
                            Triple("actions", "构建", Icons.Rounded.PlayArrow),
                            Triple("settings", "设置", Icons.Rounded.Settings)
                        ).forEach { (page, label, icon) ->
                            NavigationBarItem(
                                selected = model.screen == page, onClick = { model.navigate(page) }, enabled = !model.busy,
                                icon = { Icon(icon, contentDescription = null) }, label = { Text(label) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                WaveLoadingBar(model.busy, Modifier.fillMaxWidth())
                if (!loggedIn) LoginScreen(model, onOpenUrl)
                else SharedTransitionLayout(Modifier.weight(1f)) {
                    AnimatedContent(
                    targetState = model.screen,
                    transitionSpec = {
                        if (targetState == "detail" || initialState == "detail") {
                            
                            fadeIn(tween(260, delayMillis = 30)) togetherWith fadeOut(tween(125))
                        } else {
                            val pages = listOf("home", "local", "actions", "settings")
                            val forward = pages.indexOf(targetState) > pages.indexOf(initialState)
                            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { if (forward) it / 12 else -it / 12 }) togetherWith
                                (fadeOut(tween(120)) + slideOutHorizontally(tween(220)) { if (forward) -it / 12 else it / 12 })
                        }
                    },
                    label = "screen", modifier = Modifier.fillMaxSize()
                ) { page ->
                    pageState.SaveableStateProvider(page) {
                        when (page) {
                            "home" -> RemoteScreen(model, actionsOnly = false, sharedScope = this@SharedTransitionLayout, visibilityScope = this@AnimatedContent)
                            "local" -> LocalScreen(model)
                            "actions" -> RemoteScreen(model, actionsOnly = true, sharedScope = this@SharedTransitionLayout, visibilityScope = this@AnimatedContent)
                            "settings" -> SettingsScreen(model, onChooseDirectory, onGrantStorage, onOpenUrl)
                            "detail" -> RepositoryScreen(model, onOpenUrl, sharedScope = this@SharedTransitionLayout, visibilityScope = this@AnimatedContent)
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(vm: RainViewModel, openUrl: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(androidx.compose.ui.res.painterResource(me.rain.git.R.drawable.ic_rain_mark), null,
                Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text("RainGit", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("代码随行，每一滴灵感都有迹可循。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("登录 GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Personal Access Token", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(token, { token = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                        visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            if (token.isNotBlank() && !vm.busy) vm.loginWithToken(token)
                        }),
                        placeholder = { Text("ghp_ / github_pat_") },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        trailingIcon = { IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (showToken) "隐藏 Token" else "显示 Token")
                        } })
                    Button(onClick = { vm.loginWithToken(token) }, enabled = !vm.busy && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (vm.busy) "正在连接…" else "登录")
                    }
                    TextButton(onClick = { openUrl("https://github.com/settings/tokens") }) {
                        Text("创建 GitHub Token"); Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
            }
            Text("凭证仅加密保存在本机。访问私有仓库与构建需要相应的仓库及 Actions 权限。",
                style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SectionHeading(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun EmptyPanel(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 45.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StateLabel(label: String, tone: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = tone.copy(alpha = 0.11f), shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = tone, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

internal data class RunAppearance(val name: String, val color: Color, val icon: ImageVector)

@Composable
internal fun runAppearance(status: String, conclusion: String): RunAppearance {
    
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val success = Color(if (dark) 0xFF3FB950 else 0xFF1A7F37)  
    val danger  = Color(if (dark) 0xFFF85149 else 0xFFCF222E)  
    val info    = Color(if (dark) 0xFF58A6FF else 0xFF0969DA)  
    val warn    = Color(if (dark) 0xFFD29922 else 0xFF9A6700)  
    val muted   = Color(if (dark) 0xFF8B949E else 0xFF57606A)  

    return when {
        conclusion == "success" -> RunAppearance("成功", success, Icons.Rounded.CheckCircle)
        conclusion == "failure" || conclusion == "timed_out" -> RunAppearance("失败", danger, Icons.Rounded.Error)
        conclusion == "skipped" -> RunAppearance("已跳过", muted, Icons.Rounded.SkipNext)
        conclusion == "neutral" -> RunAppearance("已完成", muted, Icons.Rounded.CheckCircle)
        conclusion == "cancelled" -> RunAppearance("已取消", muted, Icons.Rounded.Cancel)
        status == "in_progress" -> RunAppearance("运行中", info, Icons.Rounded.Autorenew)
        status == "queued" || status == "waiting" || status == "pending" -> RunAppearance("排队中", warn, Icons.Rounded.Schedule)
        else -> RunAppearance(conclusion.ifBlank { status.ifBlank { "未知" } }, muted, Icons.Rounded.Info)
    }
}
