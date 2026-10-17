package me.rain.git.auth

import android.content.Context
import android.os.Environment
import android.os.Build
import android.provider.DocumentsContract
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("rain_git_settings", Context.MODE_PRIVATE)
    private val keyAlias = "rain_git_token_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    fun token(): String? {
        return try {
            val encoded = prefs.getString("secret", null) ?: return null
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun saveToken(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("secret", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun logout() { prefs.edit().remove("secret").remove("cached_login").remove("cached_name").remove("cached_id").apply() }

    fun cacheAccount(account: me.rain.git.github.Account) {
        prefs.edit().putString("cached_login", account.login).putString("cached_name", account.displayName)
            .putLong("cached_id", account.id).apply()
    }

    fun cachedAccount(): me.rain.git.github.Account? {
        val login = prefs.getString("cached_login", null) ?: return null
        val name = prefs.getString("cached_name", null)
            ?.takeUnless { it.isBlank() || it == "null" }
            ?: login
        return me.rain.git.github.Account(login, name, "", prefs.getLong("cached_id", 0))
    }

    var favoriteRepos: Set<String>
        get() = prefs.getStringSet("favorite_repos", emptySet()).orEmpty().toSet()
        set(value) { prefs.edit().putStringSet("favorite_repos", value.toSet()).apply() }

    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(v) { prefs.edit().putInt("theme_mode", v.coerceIn(0, 2)).apply() }

    var author: String
        get() = prefs.getString("author", "").orEmpty()
        set(v) { prefs.edit().putString("author", v).apply() }

    var email: String
        get() = prefs.getString("email", "").orEmpty()
        set(v) { prefs.edit().putString("email", v).apply() }

    private val defaultRoot: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "repositories")

    fun root(): File {
        val saved = prefs.getString("root", null)?.let(::File)
        val dir = if (saved != null) saved else defaultRoot
        
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw SecurityException("无法创建或访问目录：${dir.path}。请确保已授予「所有文件访问权限」且 U盘/SD卡 已挂载。")
        }
        if (!dir.canWrite()) {
            throw SecurityException("目录不可写：${dir.path}")
        }
        return dir.canonicalFile
    }

    fun setRootFromTree(uri: Uri): File {
        require(Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) { "请先在系统设置授予所有文件访问权限" }
        // 放宽校验：允许任何 provider（比如某些系统下的 U 盘、SD 卡 provider）
        // require(uri.authority == "com.android.externalstorage.documents") { "仅支持设备存储目录（请从「系统选择」进入）" }

        val id = DocumentsContract.getTreeDocumentId(uri)
        val colon = id.indexOf(':')
        require(colon > 0) { "无法识别所选目录，请尝试直接输入路径（如 /storage/BA73-022B/RainGit）" }

        val volumeId = id.substring(0, colon)
        val relative = id.substring(colon + 1)

        
        val base: File = if (volumeId.equals("primary", ignoreCase = true)) {
            File("/storage/emulated/0")
        } else {
            File("/storage", volumeId)
        }.canonicalFile

        require(base.isDirectory) { "存储卷不可访问：${base.path}" }

        val dir = File(base, relative).canonicalFile
        require(dir.path == base.path || dir.path.startsWith(base.path + File.separator)) { "目录超出允许范围" }
        require(dir.isDirectory || dir.mkdirs()) { "无法创建目录：${dir.path}" }
        require(dir.canWrite()) { "文件夹不可写：${dir.path}" }

        prefs.edit().putString("root", dir.path).apply()
        return dir
    }

    fun resetRoot() { prefs.edit().remove("root").apply() }

    




    fun setRootPath(path: String): File {
        val trimmed = path.trim()
        require(trimmed.isNotBlank()) { "路径不能为空" }
        val candidate = File(trimmed)
        require(candidate.isAbsolute) { "请填写绝对路径，例如 /sdcard/RainGit" }
        if (!candidate.isDirectory && !candidate.mkdirs()) {
            throw IllegalArgumentException("无法创建或写入目录：$trimmed（请检查路径，或授予「所有文件访问」权限）")
        }
        require(candidate.canWrite()) { "目录不可写：$trimmed" }
        val canonical = candidate.canonicalFile
        prefs.edit().putString("root", canonical.path).apply()
        return canonical
    }
}
