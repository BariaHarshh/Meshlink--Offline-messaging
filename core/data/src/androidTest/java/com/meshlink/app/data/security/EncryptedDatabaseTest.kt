package com.meshlink.app.data.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.entity.MessageEntity
import com.meshlink.app.data.local.entity.PendingMessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2 instrumented tests — database encryption at rest.
 *
 * These tests run on a real Android device or emulator because they exercise
 * SQLCipher, which requires a native Android environment.
 *
 * Test coverage:
 *  1. DatabaseKeyManager returns a non-empty passphrase.
 *  2. The same passphrase is returned on repeated calls (idempotent).
 *  3. A Room database opened with the correct passphrase is accessible.
 *  4. A database closed and reopened with the same passphrase retains saved data.
 *  5. A database opened with the wrong passphrase fails to open.
 *  6. The database file on disk is encrypted at rest (no plaintext SQLite magic header).
 *  7. Messages can be saved and retrieved normally through the encrypted DB.
 *  8. Pending messages can be saved and retrieved normally through the encrypted DB.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dbKeyManager: DatabaseKeyManager
    private lateinit var db: AppDatabase

    companion object {
        private const val TEST_DB_NAME = "test_meshlink_phase2.db"
    }

    @Before
    fun setUp() {
        dbKeyManager = DatabaseKeyManager(context)
        // Delete any leftover test database from a previous run
        context.deleteDatabase(TEST_DB_NAME)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        context.deleteDatabase(TEST_DB_NAME)
    }

    // ── 1. Key generation ────────────────────────────────────────────────────

    @Test
    fun `getOrCreatePassphrase returns a 32-byte passphrase`() {
        val passphrase = dbKeyManager.getOrCreatePassphrase()
        assertNotNull(passphrase)
        assertEquals(
            "Passphrase must be 32 bytes (256-bit)",
            32,
            passphrase.size
        )
    }

    @Test
    fun `getOrCreatePassphrase is idempotent - same bytes returned on second call`() {
        val first  = dbKeyManager.getOrCreatePassphrase()
        val second = dbKeyManager.getOrCreatePassphrase()
        assertArrayEquals(
            "Same passphrase must be returned on every call to reopen the DB",
            first,
            second
        )
    }

    // ── 2. Correct key → DB accessible ──────────────────────────────────────

    @Test
    fun `database opened with correct passphrase is accessible`() = runTest {
        val passphrase = dbKeyManager.getOrCreatePassphrase()
        val factory    = SupportFactory(passphrase)

        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()

        // A simple query proves the database opened correctly
        val messages = db.messageDao().getLatestMessagePerConversation().first()
        assertNotNull("Encrypted database must be queryable with the correct key", messages)
    }

    // ── 3. Reopen with same key ──────────────────────────────────────────────

    @Test
    fun `database can be closed and reopened with the same passphrase`() = runTest {
        val passphrase = dbKeyManager.getOrCreatePassphrase()
        val factory1   = SupportFactory(passphrase)

        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(factory1)
            .allowMainThreadQueries()
            .build()

        val msg = MessageEntity(
            id         = "reopen-msg-1",
            senderId   = "alice",
            receiverId = "bob",
            ciphertext = "encrypted-payload".toByteArray(),
            timestamp  = 1_000L,
            delivered  = false
        )
        db.messageDao().insert(msg)
        db.close()

        // Reopen with the same passphrase
        val factory2   = SupportFactory(passphrase)
        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(factory2)
            .allowMainThreadQueries()
            .build()

        try {
            val retrieved = reopenedDb.messageDao().getByConversation("alice").first()
            assertEquals(1, retrieved.size)
            assertEquals("reopen-msg-1", retrieved[0].id)
        } finally {
            if (reopenedDb.isOpen) reopenedDb.close()
        }
    }

    // ── 4. Wrong key → DB fails to open ─────────────────────────────────────

    @Test
    fun `database opened with wrong passphrase throws an exception`() = runTest {
        // Create the database with the correct passphrase first
        val correctPassphrase = dbKeyManager.getOrCreatePassphrase()
        val correctFactory    = SupportFactory(correctPassphrase)
        val setup = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(correctFactory)
            .allowMainThreadQueries()
            .build()
        // Force DB creation by running a query
        setup.messageDao().getLatestMessagePerConversation().first()
        setup.close()

        // Now try to reopen it with a wrong (all-zero) passphrase
        val wrongPassphrase = ByteArray(32) { 0x00 }
        val wrongFactory    = SupportFactory(wrongPassphrase)
        val wrongDb = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(wrongFactory)
            .allowMainThreadQueries()
            .build()

        var threw = false
        try {
            wrongDb.messageDao().getLatestMessagePerConversation().first()
        } catch (e: Exception) {
            threw = true
        } finally {
            if (wrongDb.isOpen) wrongDb.close()
        }

        assertTrue("Opening an encrypted database with a wrong passphrase must throw", threw)
    }

    // ── 5. Database file encryption on disk ──────────────────────────────────

    @Test
    fun `database file on disk is encrypted and does not contain plaintext sqlite header`() = runTest {
        val passphrase = dbKeyManager.getOrCreatePassphrase()
        val factory    = SupportFactory(passphrase)

        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()

        val msg = MessageEntity(
            id         = "disk-msg-1",
            senderId   = "alice",
            receiverId = "bob",
            ciphertext = "sensitive-content".toByteArray(),
            timestamp  = 1_000L,
            delivered  = false
        )
        db.messageDao().insert(msg)
        db.close()

        val dbFile = context.getDatabasePath(TEST_DB_NAME)
        assertTrue("Database file must exist on disk", dbFile.exists())
        val headerBytes = ByteArray(16)
        dbFile.inputStream().use { it.read(headerBytes) }
        val headerString = String(headerBytes, Charsets.US_ASCII)
        assertTrue(
            "SQLCipher database file header must not contain plaintext 'SQLite format 3'",
            !headerString.startsWith("SQLite format 3")
        )
    }

    // ── 6. Messages survive round-trip through encrypted DB ─────────────────

    @Test
    fun `messages can be saved and retrieved through the encrypted database`() = runTest {
        val passphrase = dbKeyManager.getOrCreatePassphrase()
        val factory    = SupportFactory(passphrase)

        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()

        val msg = MessageEntity(
            id         = "test-msg-1",
            senderId   = "alice",
            receiverId = "bob",
            ciphertext = "encrypted-payload".toByteArray(),
            timestamp  = 1_000L,
            delivered  = false
        )
        db.messageDao().insert(msg)

        val result = db.messageDao().getByConversation("alice").first()
        assertEquals(1, result.size)
        assertEquals("test-msg-1", result[0].id)
        assertEquals("alice", result[0].senderId)
    }

    // ── 7. Pending messages survive round-trip through encrypted DB ──────────

    @Test
    fun `pending messages can be saved and retrieved through the encrypted database`() = runTest {
        val passphrase = dbKeyManager.getOrCreatePassphrase()
        val factory    = SupportFactory(passphrase)

        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()

        val now    = System.currentTimeMillis()
        val in48h  = now + 48 * 60 * 60 * 1_000L
        val entity = PendingMessageEntity(
            id             = "pending-1",
            packetJson     = """{"id":"pending-1"}""",
            targetDeviceId = "device-X",
            enqueuedAt     = now,
            expiresAt      = in48h
        )
        db.pendingMessageDao().insert(entity)

        val result = db.pendingMessageDao().getPendingFor("device-X", now)
        assertEquals(1, result.size)
        assertEquals("pending-1", result[0].id)
        assertEquals("device-X", result[0].targetDeviceId)
        assertEquals(in48h, result[0].expiresAt)
    }
}

