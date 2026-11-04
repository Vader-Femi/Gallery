package com.femi.gallery.data.locked

import android.util.Log
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.locked.ContentCipher
import com.femi.gallery.domain.locked.LockedBackupFormat
import com.femi.gallery.domain.locked.LockedItemHeader
import com.femi.gallery.domain.locked.LockedKeyKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What an import of a backup did. */
data class ImportResult(val imported: Int, val alreadyThere: Int, val failed: Int)

/**
 * Exports the Locked folder to an encrypted backup file and imports one. The files in a backup are the locked files
 * with their headers sealed under a key made from a passphrase ([LockedBackupFormat]); their contents are copied
 * without being decrypted, so a backup of many gigabytes is made without holding more than a buffer in memory.
 * Importing seals the headers with this phone's folder key again.
 */
@Singleton
class LockedBackup @Inject constructor(
    private val store: LockedStore,
    private val keys: LockedKeys,
    private val cipher: ContentCipher,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Writes every item of the folder to [out]. Returns how many were written. [onProgress] gets (done, total).
     * Cancelling stops it; what was written so far is then not a valid backup, and the caller should delete it.
     */
    suspend fun export(out: OutputStream, passphrase: CharArray, onProgress: (Int, Int) -> Unit): Int = withContext(io) {
        // Items of older versions are encrypted first, so that none is left out.
        check(store.migrateLegacy()) { "Some items could not be encrypted, so they would be missing from the backup" }
        val folderKey = keys.folderKey()
        val files = store.directory.listFiles { f -> f.extension == "lk" }.orEmpty().sortedBy { it.name }
        val writer = LockedBackupFormat.Writer(out, passphrase)
        files.forEachIndexed { index, file ->
            currentCoroutineContext().ensureActive()
            onProgress(index, files.size)
            val header = FileInputStream(file).use { LockedItemHeader.read(BufferedInputStream(it, 1024)) }
            val sealed = header.resealed(folderKey, LockedKeyKind.Passphrase, writer.key).toBytes()
            val contentLength = file.length() - header.length
            writer.addEntry(sealed.size + contentLength) { sink ->
                sink.write(sealed)
                FileInputStream(file).use { input ->
                    input.skipHeader(header.length.toLong())
                    input.copyTo(sink, BUFFER)
                }
            }
        }
        writer.finish()
        onProgress(files.size, files.size)
        Log.i(TAG, "Exported ${files.size} items")
        files.size
    }

    /**
     * Adds the items of the backup in [input] to the folder. Items already in the folder are left alone. Every item is
     * decrypted once, as it is written, to be sure that it is whole before it is kept. Throws
     * [com.femi.gallery.domain.locked.WrongPassphraseException] for a wrong passphrase and
     * [com.femi.gallery.domain.locked.BackupCorruptException] when the backup is damaged or cut short (the items read
     * before that point are kept).
     */
    suspend fun import(input: InputStream, passphrase: CharArray, onProgress: (Int) -> Unit): ImportResult = withContext(io) {
        val folderKey = keys.folderKey()
        store.directory.mkdirs()
        val reader = LockedBackupFormat.Reader(input, passphrase)
        var imported = 0
        var already = 0
        var failed = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = reader.nextEntry() ?: break
            val header = LockedItemHeader.read(entry)
            if (header.kind != LockedKeyKind.Passphrase) {
                failed++
                continue
            }
            val meta = header.openMeta(reader.key)
            val target = File(store.directory, "${meta.id}.lk")
            if (target.exists()) {
                already++
                continue
            }
            val temporary = File(store.directory, "${meta.id}.lk.tmp")
            val ok = try {
                val fileKey = header.openFileKey(reader.key)
                val local = LockedItemHeader.create(LockedKeyKind.Folder, folderKey, fileKey, meta)
                FileOutputStream(temporary).use { file ->
                    local.write(file)
                    entry.copyTo(file, BUFFER)
                    file.flush()
                    file.fd.sync()
                }
                verifyReadable(temporary, local.length, fileKey) && temporary.renameTo(target)
            } catch (e: java.io.IOException) {
                if (e is com.femi.gallery.domain.locked.BackupCorruptException) throw e
                Log.w(TAG, "Cannot import ${meta.id}", e)
                false
            } catch (e: java.security.GeneralSecurityException) {
                Log.w(TAG, "Cannot import ${meta.id}", e)
                false
            }
            if (ok) imported++ else { failed++; temporary.delete() }
            onProgress(imported + already + failed)
        }
        if (imported > 0) store.notifyChanged()
        Log.i(TAG, "Imported $imported items ($already already there, $failed failed)")
        ImportResult(imported, already, failed)
    }

    /** Reads the whole encrypted file through the decrypting stream, which checks every segment's tag. */
    private fun verifyReadable(file: File, headerLength: Int, fileKey: ByteArray): Boolean = try {
        FileInputStream(file).use { input ->
            input.skipHeader(headerLength.toLong())
            cipher.decrypt(fileKey, input).use { plain ->
                val buffer = ByteArray(BUFFER)
                while (plain.read(buffer) >= 0) { /* every segment is checked as it is read */ }
            }
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "An imported file is not readable: ${e.message}")
        false
    }

    private fun FileInputStream.skipHeader(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) throw java.io.EOFException("Cannot skip the header")
            left -= skipped
        }
    }

    private companion object {
        const val TAG = "LockedBackup"
        const val BUFFER = 256 * 1024
    }
}
