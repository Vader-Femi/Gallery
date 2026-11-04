package com.femi.gallery.data.locked

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.femi.gallery.core.security.LockSession
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.locked.LockedAead
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.GeneralSecurityException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/** The folder key cannot be had right now. */
sealed class LockedKeyException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The key needs the person to authenticate first (only with "require authentication" on). */
    class NeedsAuthentication(cause: Throwable?) : LockedKeyException("Authentication is needed", cause)

    /** The key is gone for good: the locked items can only be restored from a backup. */
    class Lost(cause: Throwable?) : LockedKeyException("The key of the Locked folder is lost", cause)

    /** Something else went wrong (a hiccup of the Keystore); trying again may work. */
    class Unavailable(cause: Throwable?) : LockedKeyException("The key of the Locked folder is not available", cause)
}

/**
 * The one key that seals the file keys and metadata of every locked file (the "folder key"). It is random, stored only
 * in `files/locked/key.bin` encrypted by a Keystore key, and kept in memory while it is needed; it is dropped from
 * memory when the Locked folder is locked again. With "require authentication" on, the Keystore key can only open it
 * right after the person has authenticated, which is what makes the folder's lock real and not only a screen.
 *
 * `key.bin`: one byte for the kind of Keystore key (1 plain, 2 requires authentication), then the sealed folder key.
 */
@Singleton
class LockedKeys @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:IoDispatcher io: CoroutineDispatcher,
    private val session: LockSession,
) {
    private val dir = File(context.filesDir, "locked")
    private val keyFile = File(dir, "key.bin")
    private val plainKey = AndroidKeystoreKeyEncryptor(ALIAS_PLAIN, authBound = false)
    private val authKey = AndroidKeystoreKeyEncryptor(ALIAS_AUTH, authBound = true)

    private var cached: ByteArray? = null

    private val _authRequired = MutableStateFlow(readMode() == MODE_AUTH)

    /** Whether the Locked folder's files can only be opened right after authenticating. */
    val authRequired: StateFlow<Boolean> = _authRequired.asStateFlow()

    init {
        // The folder key lives in memory only while the Locked folder is unlocked; the moment it is unlocked (the
        // person has just authenticated) it is opened, while a key that requires authentication allows it.
        CoroutineScope(SupervisorJob() + io).launch {
            session.unlocked.collect { unlocked ->
                if (unlocked) runCatching { folderKey() } else lock()
            }
        }
    }

    /** The folder key; created on first use. Throws a [LockedKeyException] when it cannot be had. */
    @Synchronized
    fun folderKey(): ByteArray {
        cached?.let { return it }
        val mode = readMode()
        val key = if (mode == null) createKey() else openKey(mode)
        cached = key
        session.setKeyLost(false)
        return key
    }

    /** Forgets the folder key held in memory (it can be opened again, which may need the person to authenticate). */
    @Synchronized
    fun lock() {
        cached = null
    }

    /**
     * Turns "require authentication" on or off by sealing the folder key with the other kind of Keystore key. Turning
     * it on needs the person to have authenticated just before.
     */
    @Synchronized
    fun setAuthRequired(enabled: Boolean) {
        val folder = folderKey()
        if (enabled == (readMode() == MODE_AUTH)) return
        val sealed = try {
            (if (enabled) authKey else plainKey).encrypt(folder)
        } catch (e: UserNotAuthenticatedException) {
            throw LockedKeyException.NeedsAuthentication(e)
        }
        writeKeyFile(if (enabled) MODE_AUTH else MODE_PLAIN, sealed)
        (if (enabled) plainKey else authKey).deleteKey()
        _authRequired.value = enabled
        Log.i(TAG, "Require authentication: $enabled")
    }

    /** Starts again with a new folder key, after the old one was lost; the old items cannot be read afterwards. */
    @Synchronized
    fun resetAfterLoss() {
        // The items sealed with the lost key cannot be opened by anything; they would only block a new key.
        dir.listFiles { f -> f.extension == "lk" || f.name.endsWith(".lk.tmp") }?.forEach { it.delete() }
        keyFile.delete()
        plainKey.deleteKey()
        authKey.deleteKey()
        cached = null
        _authRequired.value = false
        session.setKeyLost(false)
        Log.w(TAG, "The folder key was reset")
    }

    private fun readMode(): Int? = runCatching { if (keyFile.exists()) keyFile.readBytes()[0].toInt() else null }.getOrNull()

    private fun createKey(): ByteArray {
        dir.mkdirs()
        // Locked files with no key to open them: a new key would not help them, and must not look like it does.
        if (dir.listFiles { f -> f.extension == "lk" }.orEmpty().isNotEmpty()) {
            session.setKeyLost(true)
            throw LockedKeyException.Lost(null)
        }
        val key = LockedAead.newKey()
        try {
            writeKeyFile(MODE_PLAIN, plainKey.encrypt(key))
        } catch (e: GeneralSecurityException) {
            throw LockedKeyException.Unavailable(e)
        }
        return key
    }

    private fun openKey(mode: Int): ByteArray {
        val sealed = try {
            keyFile.readBytes().copyOfRange(1, keyFile.length().toInt())
        } catch (e: Exception) {
            session.setKeyLost(true)
            throw LockedKeyException.Lost(e)
        }
        return try {
            (if (mode == MODE_AUTH) authKey else plainKey).decrypt(sealed)
        } catch (e: UserNotAuthenticatedException) {
            throw LockedKeyException.NeedsAuthentication(e)
        } catch (e: KeyPermanentlyInvalidatedException) {
            lost(e)
        } catch (e: UnrecoverableKeyException) {
            lost(e)
        } catch (e: AEADBadTagException) {
            lost(e)
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Cannot open the folder key right now", e)
            throw LockedKeyException.Unavailable(e)
        }
    }

    private fun lost(cause: Throwable): Nothing {
        Log.e(TAG, "The folder key is lost", cause)
        session.setKeyLost(true)
        throw LockedKeyException.Lost(cause)
    }

    /** Writes through a temporary file, so that a crash cannot leave half a key file. */
    private fun writeKeyFile(mode: Int, sealed: ByteArray) {
        dir.mkdirs()
        val temporary = File(dir, "key.bin.tmp")
        temporary.outputStream().use { out ->
            out.write(mode)
            out.write(sealed)
            out.flush()
            out.fd.sync()
        }
        if (!temporary.renameTo(keyFile)) {
            keyFile.delete()
            check(temporary.renameTo(keyFile)) { "Cannot write the key file" }
        }
    }

    private companion object {
        const val TAG = "LockedKeys"
        const val MODE_PLAIN = 1
        const val MODE_AUTH = 2
        const val ALIAS_PLAIN = "gallery_locked_kek_v1"
        const val ALIAS_AUTH = "gallery_locked_kek_auth_v1"
    }
}
