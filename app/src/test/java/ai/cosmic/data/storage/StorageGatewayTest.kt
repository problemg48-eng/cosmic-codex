package ai.cosmic.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [StorageGateway] filesystem operations, path safety, and audit logging.
 */
class StorageGatewayTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun storageInfoReturnsValidMetricsForExistingDirectory() {
        val dir = tempFolder.newFolder("workspace")
        val res = StorageGateway.getStorageInfo(dir.absolutePath)
        assertTrue("Expected Success, got $res", res is StorageGateway.FsResult.Success)
        val info = (res as StorageGateway.FsResult.Success).value
        assertTrue(info.totalBytes > 0)
        assertTrue(info.usagePercent in 0..100)
    }

    @Test
    fun storageRootModelPreservesMetadata() {
        val root = StorageGateway.StorageRoot(
            label = "Projects",
            path = "/data/user/0/ai.cosmic/files/home/projects",
            writable = true,
            source = StorageGateway.StorageSource.APP_PRIVATE
        )
        assertEquals("Projects", root.label)
        assertTrue(root.writable)
        assertEquals(StorageGateway.StorageSource.APP_PRIVATE, root.source)
    }

    @Test
    fun auditEntryTracksSuccessAndFailure() {
        val okEntry = StorageGateway.AuditEntry(
            timestamp = 1000L,
            operation = "WRITE",
            path = "/sdcard/Download/test.txt",
            success = true
        )
        val deniedEntry = StorageGateway.AuditEntry(
            timestamp = 2000L,
            operation = "READ",
            path = "/system/build.prop",
            success = false,
            error = "Access denied"
        )
        assertTrue(okEntry.success)
        assertFalse(deniedEntry.success)
        assertEquals("Access denied", deniedEntry.error)
    }
}
