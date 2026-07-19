package com.juhao.murexide.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.userDataStore by preferencesDataStore(name = "user_data")

@Serializable
data class UserAccount(
    val username: String = "用户",
    val avatar: String = "",
    val id: String = "1",
    val token: String = "",
    val isValidated: Boolean = false
)

class AccountStorage(private val context: Context) {
    companion object {
        private val ACCOUNTS_KEY = stringPreferencesKey("user_accounts")
        private val CURRENT_USER_ID_KEY = stringPreferencesKey("current_user_id")
        private const val KEY_ALIAS = "account_encryption_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    // 内存缓存，提升性能
    private val _currentUserId = MutableStateFlow<String?>(null)
    private val _accountsCache = MutableStateFlow<List<UserAccount>>(emptyList())
    private var isCacheValid = false

    // ========== 加密相关 ==========

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(value: String): String {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val secretKey = getSecretKey()
        val combined = android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    // ========== 缓存管理 ==========

    private suspend fun refreshCache() {
        if (!isCacheValid) {
            val accounts = loadAccountsFromStorage()
            _accountsCache.value = accounts
            _currentUserId.value = loadCurrentUserId()
            isCacheValid = true
        }
    }

    private suspend fun loadAccountsFromStorage(): List<UserAccount> {
        return try {
            val encryptedJson = context.userDataStore.data
                .map { preferences -> preferences[ACCOUNTS_KEY] }
                .firstOrNull()

            if (!encryptedJson.isNullOrEmpty()) {
                val jsonString = decrypt(encryptedJson)
                json.decodeFromString(jsonString)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            clearAccounts()
            emptyList()
        }
    }

    private suspend fun loadCurrentUserId(): String? {
        return context.userDataStore.data
            .map { preferences -> preferences[CURRENT_USER_ID_KEY] }
            .firstOrNull()
    }

    private suspend fun saveCurrentUserId(userId: String?) {
        context.userDataStore.edit { preferences ->
            if (userId != null) {
                preferences[CURRENT_USER_ID_KEY] = userId
            } else {
                preferences.remove(CURRENT_USER_ID_KEY)
            }
        }
        _currentUserId.value = userId
    }

    private fun invalidateCache() {
        isCacheValid = false
    }

    // ========== 账户存储相关 ==========

   suspend fun getDefaultAccount(): UserAccount {
        refreshCache()
        // 如果有当前用户ID，优先返回当前用户
        val currentId = _currentUserId.value
        if (currentId != null) {
            return _accountsCache.value.find { it.id == currentId }
                ?: _accountsCache.value.firstOrNull()
                ?: UserAccount()
        }
        return _accountsCache.value.firstOrNull() ?: UserAccount()
    }

    suspend fun saveAccounts(accounts: List<UserAccount>) {
        val jsonString = json.encodeToString(accounts)
        val encryptedJson = encrypt(jsonString)
        context.userDataStore.edit { preferences ->
            preferences[ACCOUNTS_KEY] = encryptedJson
        }
        // 更新缓存
        _accountsCache.value = accounts
        isCacheValid = true

        // 如果当前用户ID不在新列表中，清除
        val currentId = _currentUserId.value
        if (currentId != null && accounts.none { it.id == currentId }) {
            saveCurrentUserId(null)
        }
    }

    suspend fun addAccount(account: UserAccount) {
        refreshCache()
        val currentAccounts = _accountsCache.value
        if (currentAccounts.any { it.id == account.id }) {
            return
        }
        val updatedAccounts = currentAccounts + account
        saveAccounts(updatedAccounts)
        if (currentAccounts.isEmpty()) {
            setCurrentUser(account.id)
        }
    }

    suspend fun updateAccount(account: UserAccount) {
        refreshCache()
        val currentAccounts = _accountsCache.value
        val updatedAccounts = currentAccounts.map {
            if (it.id == account.id) account else it
        }
        saveAccounts(updatedAccounts)
    }

    suspend fun validateAccount(newAccount: UserAccount): Boolean {
        refreshCache()

        val currentAccount = getCurrentAccount() ?: return false
        val oldId = currentAccount.id

        if (newAccount.id != oldId) {
            val accounts = _accountsCache.value.toMutableList()
            accounts.removeAll { it.id == oldId }
            accounts.add(newAccount.copy(isValidated = true, token = currentAccount.token))
            saveAccounts(accounts)
            setCurrentUser(newAccount.id)
        } else {
            updateAccount(newAccount.copy(isValidated = true, token = currentAccount.token))
        }

        return true
    }

    suspend fun removeAccount(accountId: String) {
        refreshCache()
        val currentAccounts = _accountsCache.value
        val updatedAccounts = currentAccounts.filter { it.id != accountId }
        saveAccounts(updatedAccounts)

        // 如果移除的是当前用户，清除当前用户ID
        if (_currentUserId.value == accountId) {
            saveCurrentUserId(null)
        }
    }

    suspend fun clearAccounts() {
        context.userDataStore.edit { preferences ->
            preferences.remove(ACCOUNTS_KEY)
            preferences.remove(CURRENT_USER_ID_KEY)
        }
        _accountsCache.value = emptyList()
        _currentUserId.value = null
        isCacheValid = true
    }

    // 直接从存储加载（跳过缓存）
    suspend fun getAccounts(): List<UserAccount> {
        refreshCache()
        return _accountsCache.value
    }

    // Flow方式获取账户列表
    val userAccountsFlow: Flow<List<UserAccount>> = context.userDataStore.data.map { preferences ->
        try {
            val encryptedJson = preferences[ACCOUNTS_KEY]
            val accounts: List<UserAccount> = if (!encryptedJson.isNullOrEmpty()) {
                val jsonString = decrypt(encryptedJson)
                json.decodeFromString(jsonString)
            } else {
                emptyList()
            }
            // 更新缓存
            _accountsCache.value = accounts
            isCacheValid = true
            accounts
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ========== 当前用户管理 ==========

    /**
     * 设置当前用户
     */
    suspend fun setCurrentUser(accountId: String) {
        val accounts = getAccounts()
        val account = accounts.find { it.id == accountId }
        if (account != null) {
            saveCurrentUserId(accountId)
            // 将当前用户移到列表首位
            val updatedAccounts = listOf(account) + accounts.filter { it.id != accountId }
            saveAccounts(updatedAccounts)
        }
    }

    suspend fun removeCurrentUser() {
        refreshCache()
        saveCurrentUserId(null)
    }


    /**
     * 获取当前用户ID
     */
    suspend fun getCurrentUserId(): String? {
        refreshCache()
        return _currentUserId.value
    }

    /**
     * 获取当前用户ID（Flow）
     */
    val currentUserIdFlow: Flow<String?> = context.userDataStore.data
        .map { preferences -> preferences[CURRENT_USER_ID_KEY] }

    /**
     * 获取当前账户
     */
    suspend fun getCurrentAccount(): UserAccount? {
        refreshCache()
        val currentId = _currentUserId.value
        return if (currentId != null) {
            _accountsCache.value.find { it.id == currentId }
        } else {
            null
        }
    }

    /**
     * 获取当前用户Token
     */
    suspend fun getCurrentToken(): String? {
        return getCurrentAccount()?.token?.takeIf { it.isNotEmpty() }
    }

    /**
     * 获取当前用户Token（Flow）
     */
    val currentTokenFlow: Flow<String?> = currentUserIdFlow.combine(userAccountsFlow) { userId, accounts ->
        if (userId != null) {
            accounts.find { it.id == userId }?.token
        } else {
            accounts.firstOrNull()?.token
        }
    }

    /**
     * 获取当前用户ID（同步方式，可能返回过期值）
     */
    fun getCurrentUserIdSync(): String? {
        return _currentUserId.value
    }

    /**
     * 更新当前用户Token
     */
    suspend fun updateCurrentToken(token: String) {
        val currentAccount = getCurrentAccount() ?: return
        val updatedAccount = currentAccount.copy(token = token)
        updateAccount(updatedAccount)
    }

    // ========== 便捷方法 ==========

    suspend fun getAccountById(accountId: String): UserAccount? {
        refreshCache()
        return _accountsCache.value.find { it.id == accountId }
    }

    suspend fun accountExists(accountId: String): Boolean {
        refreshCache()
        return _accountsCache.value.any { it.id == accountId }
    }

    suspend fun getAccountCount(): Int {
        refreshCache()
        return _accountsCache.value.size
    }

    suspend fun getToken(accountId: String): String? {
        return getAccountById(accountId)?.token
    }

    suspend fun updateToken(token: String, accountId: String? = null) {
        val targetId = accountId ?: getCurrentUserId() ?: getDefaultAccount().id
        val account = getAccountById(targetId) ?: return
        val updatedAccount = account.copy(token = token)
        updateAccount(updatedAccount)
    }

    suspend fun switchAccount(accountId: String) {
        setCurrentUser(accountId)
    }

    /**
     * 获取当前用户的用户名
     */
    suspend fun getCurrentUsername(): String? {
        return getCurrentAccount()?.username
    }

    /**
     * 获取当前用户的头像
     */
    suspend fun getCurrentAvatar(): String? {
        return getCurrentAccount()?.avatar
    }

    /**
     * 获取当前用户的完整信息（包含ID和Token）
     */
    suspend fun getCurrentUserInfo(): UserAccount? {
        return getCurrentAccount()
    }
}