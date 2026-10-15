package me.rain.git

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import me.rain.git.ui.RainApp
import me.rain.git.ui.RainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        enableEdgeToEdge()
        setContent {
            val model: RainViewModel = viewModel()

            // 权限状态：每次返回时刷新
            var hasStoragePermission by remember {
                mutableStateOf(checkStoragePermission())
            }

            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri -> uri?.let(model::chooseRoot) }

            val grantStorage = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                // 返回时重新检查
                hasStoragePermission = checkStoragePermission()
                if (!hasStoragePermission) {
                    model.showMessage("仍未授予「所有文件访问」权限")
                }
            }

            RainApp(
                model = model,
                hasStoragePermission = hasStoragePermission,
                onChooseDirectory = { picker.launch(null) },
                onGrantStorage = { openStorageSettings(grantStorage) },
                onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            )
        }
    }

    /** Android 11+ 判断是否已授予「所有文件访问」 */
    private fun checkStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    }

    /**
     * 跳系统授权页，带多 ROM 兼容 fallback。
     * 华为/小米/OPPO 等部分 ROM 对 ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION 支持不一致。
     */
    private fun openStorageSettings(
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        if (checkStoragePermission()) return

        // 优先：跳到 RainGit 专属授权页
        try {
            launcher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        } catch (_: Exception) {
        }

        // 回退 1：跳到"所有文件访问"总列表
        try {
            launcher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            return
        } catch (_: Exception) {
        }

        // 回退 2：跳到应用详情页
        try {
            launcher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
        }
    }
}
