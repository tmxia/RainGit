package me.rain.git

import android.content.Intent
import androidx.core.app.ActivityCompat
import android.Manifest
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

            
            var hasStoragePermission by remember {
                mutableStateOf(checkStoragePermission())
            }

            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri -> uri?.let(model::chooseRoot) }

            val grantStorage = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                
                hasStoragePermission = checkStoragePermission()
                if (!hasStoragePermission) {
                    model.showMessage("仍未授予「所有文件访问」权限")
                }
            }

            RainApp(
                model = model,
                hasStoragePermission = hasStoragePermission,
                onChooseDirectory = { picker.launch(null) },
                onGrantStorage = {
                    try {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        
                        try {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                            android.widget.Toast.makeText(this@MainActivity, "请在「其他权限」中寻找「所有文件访问」", android.widget.Toast.LENGTH_LONG).show()
                        } catch (e2: Exception) {}
                    }
                },
                onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            )
        }
    }

    
    private fun checkStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    }

    



    private fun openStorageSettings(
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        if (checkStoragePermission()) return

        
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

        
        try {
            launcher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            return
        } catch (_: Exception) {
        }

        
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
