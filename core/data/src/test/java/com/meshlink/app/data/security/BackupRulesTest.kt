package com.meshlink.app.data.security

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 2 JVM unit tests — backup protection rules.
 *
 * Verifies that [data_extraction_rules.xml] and [backup_rules.xml] contain
 * explicit exclusions for all sensitive MeshLink data paths.
 *
 * These are pure XML-content tests — no Android runtime or device needed.
 */
class BackupRulesTest {

    /**
     * Locates an XML resource file by searching upward from the compiled class
     * output directory to the project root, then navigating to the known path.
     * Works regardless of the current working directory during test execution.
     */
    private fun findXmlResource(relativePath: String): File {
        // Walk upward from the class output dir until we find the project root
        // (identified by the presence of settings.gradle.kts)
        var dir = File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile
        repeat(10) {
            if (File(dir, "settings.gradle.kts").exists()) return@repeat
            dir = dir.parentFile ?: return@repeat
        }
        return File(dir, relativePath)
    }

    private val dataExtractionRules by lazy {
        findXmlResource("app/src/main/res/xml/data_extraction_rules.xml")
    }

    private val backupRules by lazy {
        findXmlResource("app/src/main/res/xml/backup_rules.xml")
    }

    // ── data_extraction_rules.xml (API 31+) ──────────────────────────────────

    @Test
    fun `data_extraction_rules - excludes meshlink database from cloud-backup`() {
        val content = dataExtractionRules.readText()
        assertTrue(
            "data_extraction_rules.xml must exclude meshlink.db from cloud-backup",
            content.contains("meshlink.db") && content.contains("cloud-backup")
        )
    }

    @Test
    fun `data_extraction_rules - excludes database key sharedpref from cloud-backup`() {
        val content = dataExtractionRules.readText()
        assertTrue(
            "data_extraction_rules.xml must exclude meshlink_db_key_v1.xml from cloud-backup",
            content.contains("meshlink_db_key_v1")
        )
    }

    @Test
    fun `data_extraction_rules - excludes identity key sharedpref from cloud-backup`() {
        val content = dataExtractionRules.readText()
        assertTrue(
            "data_extraction_rules.xml must exclude meshlink_identity_v1.xml from cloud-backup",
            content.contains("meshlink_identity_v1")
        )
    }

    @Test
    fun `data_extraction_rules - excludes sensitive data from device-transfer`() {
        val content = dataExtractionRules.readText()
        assertTrue(
            "data_extraction_rules.xml must have device-transfer exclusions",
            content.contains("device-transfer")
        )
    }

    // ── backup_rules.xml (API 23-30) ─────────────────────────────────────────

    @Test
    fun `backup_rules - excludes meshlink database`() {
        val content = backupRules.readText()
        assertTrue(
            "backup_rules.xml must exclude meshlink.db",
            content.contains("meshlink.db")
        )
    }

    @Test
    fun `backup_rules - excludes database key sharedpref`() {
        val content = backupRules.readText()
        assertTrue(
            "backup_rules.xml must exclude meshlink_db_key_v1",
            content.contains("meshlink_db_key_v1")
        )
    }

    @Test
    fun `backup_rules - excludes identity key sharedpref`() {
        val content = backupRules.readText()
        assertTrue(
            "backup_rules.xml must exclude meshlink_identity_v1",
            content.contains("meshlink_identity_v1")
        )
    }
}
