package com.femi.gallery.domain.locked

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException

private val META = LockedMeta(
    id = 42L, name = "Zażółć gęślą.jpg", mimeType = "image/jpeg", dateTakenMillis = 1_700_000_000_000L,
    dateAddedMillis = 1_700_000_100_000L, width = 4080, height = 3072, sizeBytes = 3_145_728L,
    durationMillis = 0L, relativePath = "DCIM/Camera/",
)

class LockedFormatTest {
    private val folderKey = LockedAead.newKey()

    @Test
    fun metadataSurvivesTheRoundTrip() {
        assertEquals(META, LockedMeta.fromBytes(META.toBytes()))
        assertEquals(META.copy(name = "日本語の名前.mp4"), LockedMeta.fromBytes(META.copy(name = "日本語の名前.mp4").toBytes()))
    }

    @Test
    fun sealedBytesOpenOnlyWithTheSameKeyAndData() {
        val data = "secret".toByteArray()
        val sealed = LockedAead.seal(folderKey, data, "a".toByteArray())
        assertArrayEquals(data, LockedAead.open(folderKey, sealed, "a".toByteArray()))
        assertFails { LockedAead.open(LockedAead.newKey(), sealed, "a".toByteArray()) }
        assertFails { LockedAead.open(folderKey, sealed, "b".toByteArray()) }
        val flipped = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFails { LockedAead.open(folderKey, flipped, "a".toByteArray()) }
        assertFails { LockedAead.open(folderKey, ByteArray(5), "a".toByteArray()) }
    }

    @Test
    fun sealingTwiceGivesDifferentBytes() {
        val a = LockedAead.seal(folderKey, "x".toByteArray(), ByteArray(0))
        val b = LockedAead.seal(folderKey, "x".toByteArray(), ByteArray(0))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun aHeaderWritesReadsAndOpens() {
        val fileKey = LockedAead.newKey()
        val header = LockedItemHeader.create(LockedKeyKind.Folder, folderKey, fileKey, META)
        val bytes = header.toBytes()
        assertEquals(header.length, bytes.size)
        val stream = ByteArrayInputStream(bytes + byteArrayOf(9, 9, 9))
        val read = LockedItemHeader.read(stream)
        assertEquals(LockedKeyKind.Folder, read.kind)
        assertArrayEquals(fileKey, read.openFileKey(folderKey))
        assertEquals(META, read.openMeta(folderKey))
        assertEquals(3, stream.available()) // left at the first byte after the header
    }

    @Test
    fun aHeaderDoesNotOpenWithAnotherKeyOrAfterATamper() {
        val header = LockedItemHeader.create(LockedKeyKind.Folder, folderKey, LockedAead.newKey(), META)
        assertFails { header.openFileKey(LockedAead.newKey()) }
        assertFails { header.openMeta(LockedAead.newKey()) }
        val bytes = header.toBytes()
        bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 0x40).toByte()
        assertFails { LockedItemHeader.read(ByteArrayInputStream(bytes)).openMeta(folderKey) }
    }

    @Test
    fun aHeaderOfAnotherKindDoesNotOpenAsThisOne() {
        // The kind is part of what is sealed: relabelling a folder header as a passphrase header does not work.
        val bytes = LockedItemHeader.create(LockedKeyKind.Folder, folderKey, LockedAead.newKey(), META).toBytes()
        bytes[4] = LockedKeyKind.Passphrase.code.toByte()
        assertFails { LockedItemHeader.read(ByteArrayInputStream(bytes)).openFileKey(folderKey) }
    }

    @Test
    fun garbageIsNotAHeader() {
        assertFails { LockedItemHeader.read(ByteArrayInputStream(ByteArray(100) { 7 })) }
        assertFails { LockedItemHeader.read(ByteArrayInputStream(ByteArray(3))) }
    }

    @Test
    fun resealingKeepsTheFileKeyAndMetadata() {
        val fileKey = LockedAead.newKey()
        val passphraseKey = LockedAead.newKey()
        val folder = LockedItemHeader.create(LockedKeyKind.Folder, folderKey, fileKey, META)
        val backup = folder.resealed(folderKey, LockedKeyKind.Passphrase, passphraseKey)
        assertEquals(LockedKeyKind.Passphrase, backup.kind)
        assertArrayEquals(fileKey, backup.openFileKey(passphraseKey))
        assertEquals(META, backup.openMeta(passphraseKey))
        assertFails { backup.openFileKey(folderKey) }
        val back = LockedItemHeader.read(ByteArrayInputStream(backup.toBytes())).resealed(passphraseKey, LockedKeyKind.Folder, folderKey)
        assertArrayEquals(fileKey, back.openFileKey(folderKey))
    }

    @Test
    fun passphrasesNeedLengthAndVariety() {
        assertFalse(PassphrasePolicy.isAcceptable("short"))
        assertFalse(PassphrasePolicy.isAcceptable("aaaaaaaaaaaaaaaa"))
        assertFalse(PassphrasePolicy.isAcceptable("abababababab"))
        assertTrue(PassphrasePolicy.isAcceptable("correct horse battery"))
        assertTrue(PassphrasePolicy.isAcceptable("🙂🙃🙄😀🙂🙃🙄😀🙂🙃🙄😀"))
    }

    @Test
    fun theKeyOfAPassphraseDependsOnPassphraseSaltAndRounds() {
        val salt = ByteArray(16) { it.toByte() }
        val a = PassphraseKdf.deriveKey("one passphrase".toCharArray(), salt, 1000)
        assertArrayEquals(a, PassphraseKdf.deriveKey("one passphrase".toCharArray(), salt, 1000))
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(PassphraseKdf.deriveKey("two passphrase".toCharArray(), salt, 1000)))
        assertFalse(a.contentEquals(PassphraseKdf.deriveKey("one passphrase".toCharArray(), ByteArray(16), 1000)))
        assertFalse(a.contentEquals(PassphraseKdf.deriveKey("one passphrase".toCharArray(), salt, 1001)))
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            fail("Expected a failure")
        } catch (e: GeneralSecurityException) {
            // expected
        } catch (e: IOException) {
            // expected
        }
    }
}

class LockedBackupFormatTest {
    private val passphrase = "correct horse battery".toCharArray()
    private val rounds = 1000

    private fun build(entries: List<ByteArray>, finish: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = LockedBackupFormat.Writer(out, passphrase, rounds)
        entries.forEach { e -> writer.addEntry(e.size.toLong()) { it.write(e) } }
        if (finish) writer.finish()
        return out.toByteArray()
    }

    private fun readAll(bytes: ByteArray, pass: CharArray = passphrase): List<ByteArray> {
        val reader = LockedBackupFormat.Reader(ByteArrayInputStream(bytes), pass)
        val result = ArrayList<ByteArray>()
        while (true) result.add((reader.nextEntry() ?: break).readBytes())
        return result
    }

    @Test
    fun entriesComeBackInOrderWhatEverTheirSize() {
        val entries = listOf(ByteArray(0), ByteArray(1) { 5 }, ByteArray(100_000) { (it % 251).toByte() }, "end".toByteArray())
        val read = readAll(build(entries))
        assertEquals(entries.size, read.size)
        entries.indices.forEach { assertArrayEquals(entries[it], read[it]) }
    }

    @Test
    fun anEmptyBackupIsValid() {
        assertTrue(readAll(build(emptyList())).isEmpty())
    }

    @Test
    fun aWrongPassphraseIsRecognisedAtOnce() {
        try {
            readAll(build(listOf(ByteArray(10))), "not the passphrase".toCharArray())
            fail("Expected WrongPassphraseException")
        } catch (e: WrongPassphraseException) {
            // expected
        }
    }

    @Test
    fun aBackupThatWasCutShortIsRefused() {
        val bytes = build(listOf(ByteArray(5000) { 1 }, ByteArray(5000) { 2 }))
        for (cut in listOf(bytes.size - 1, bytes.size - 20, bytes.size / 2, 60)) {
            try {
                readAll(bytes.copyOf(cut))
                fail("Expected a failure for a cut at $cut")
            } catch (e: BackupCorruptException) {
                // expected
            }
        }
    }

    @Test
    fun aBackupWithoutItsEndIsRefused() {
        try {
            readAll(build(listOf(ByteArray(10)), finish = false))
            fail("Expected BackupCorruptException")
        } catch (e: BackupCorruptException) {
            // expected
        }
    }

    @Test
    fun aChangedEndOrAMissingEntryIsRefused() {
        val two = build(listOf(ByteArray(10) { 1 }, ByteArray(10) { 2 }))
        val flipped = two.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        try { readAll(flipped); fail("Expected a failure") } catch (e: BackupCorruptException) { /* expected */ }

        // The first entry cut out of the file: its marker, length and 10 bytes follow the check value.
        val headerLength = two.size - (1 + 8 + 10) * 2 - (1 + 2 + LockedAead.OVERHEAD_BYTES + 12)
        val withoutFirst = two.copyOfRange(0, headerLength) + two.copyOfRange(headerLength + 19, two.size)
        try { readAll(withoutFirst); fail("Expected a failure") } catch (e: BackupCorruptException) { /* expected */ }
    }

    @Test
    fun anEntryThatIsNotReadToItsEndIsSkipped() {
        val bytes = build(listOf(ByteArray(300) { 1 }, ByteArray(20) { 2 }))
        val reader = LockedBackupFormat.Reader(ByteArrayInputStream(bytes), passphrase)
        val first = reader.nextEntry()!!
        first.read(ByteArray(10))
        val second = reader.nextEntry()!!
        assertArrayEquals(ByteArray(20) { 2 }, second.readBytes())
        assertEquals(null, reader.nextEntry())
    }

    @Test
    fun theHeadersOfFilesCanBeResealedWithTheBackupKey() {
        val folderKey = LockedAead.newKey()
        val fileKey = LockedAead.newKey()
        val out = ByteArrayOutputStream()
        val writer = LockedBackupFormat.Writer(out, passphrase, rounds)
        val header = LockedItemHeader.create(LockedKeyKind.Folder, folderKey, fileKey, META).resealed(folderKey, LockedKeyKind.Passphrase, writer.key)
        val headerBytes = header.toBytes()
        val stream = ByteArray(1000) { 3 }
        writer.addEntry((headerBytes.size + stream.size).toLong()) { it.write(headerBytes); it.write(stream) }
        writer.finish()

        val reader = LockedBackupFormat.Reader(ByteArrayInputStream(out.toByteArray()), passphrase)
        val entry = reader.nextEntry()!!
        val read = LockedItemHeader.read(entry)
        assertArrayEquals(fileKey, read.openFileKey(reader.key))
        assertEquals(META, read.openMeta(reader.key))
        assertArrayEquals(stream, entry.readBytes())
        assertNotEquals(null, read)
    }
}
