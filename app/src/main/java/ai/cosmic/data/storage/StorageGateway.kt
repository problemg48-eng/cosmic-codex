package ai.cosmic.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.content.ContextCompat
import ai.cosmic.data.local.LocalEnvironmentManager
import ai.cosmic.data.local.TermuxStorageSetupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * Centralized storage gateway for all agent filesystem operations.
 *
 * Provides a unified API for agents (Codex, Claude Code, OpenCode, Antigravity)
 * to perform filesystem operations on Android shared storage without root access.
 *
 * Access model:
 * - Android 11+ (API 30+): uses MANAGE_EXTERNAL_STORAGE for broad file access
 * - Android 10 and below: uses READ/WRITE_EXTERNAL_STORAGE
 * - App-private storage (Termux home, ~/projects): always accessible, no permission needed
 *
 * This gateway enforces path validation, permission checks, and operation auditing.
 * It never claims access to Android-private app directories or restricted system locations.
 */
object StorageGateway {
    private const val TAG = "StorageGateway"

    // --- Observable permission state ---

    private val _accessState = MutableStateFlow(StorageAccessState())
    val accessState: StateFlow<StorageAccessState> = _accessState.asStateFlow()

    /** Paths that are always off-limits regardless of permissions. */
    private val RESTRICTED_PATHS = listOf(
        "/proc", "/sys", "/dev", "/system", "/vendor", "/apex",
        "/data/data", "/data/user/0", "/data/app",
        "/data/system", "/data/misc"
    )

    /** App-external directories on /data that belong to other apps. */
    private const val ANDROID_DATA_PREFIX = "/storage/emulated/0/Android/data/"
    private const val ANDROID_OBB_PREFIX = "/storage/emulated/0/Android/obb/"

    // --- Access state data class ---

    data class StorageAccessState(
        /** Whether broad shared-storage access is granted. */
        val hasSharedStorageAccess: Boolean = false,
        /** Whether app-private storage (Termux home) is accessible (always true). */
        val hasPrivateStorageAccess: Boolean = true,
        /** Whether symlinks to shared storage have been set up. */
        val symlinksMounted: Boolean = false,
        /** Human-readable status message. */
        val statusMessage: String = "Not initialized",
        /** Accessible root paths the agent can use. */
        val accessibleRoots: List<StorageRoot> = emptyList(),
        /** Android API level, for diagnostics. */
        val apiLevel: Int = Build.VERSION.SDK_INT
    )

    data class StorageRoot(
        val label: String,
        val path: String,
        val writable: Boolean,
        val source: StorageSource
    )

    enum class StorageSource {
        /** App-private (always accessible): ~/home, ~/projects */
        APP_PRIVATE,
        /** Shared storage via MANAGE_EXTERNAL_STORAGE or READ/WRITE */
        SHARED_STORAGE,
        /** Symlinked directory under ~/storage/ */
        SYMLINK,
        /** External SD card or USB storage */
        REMOVABLE
    }

    // --- Permission checking ---

    /**
     * Returns true if the app has broad shared-storage access.
     * On Android 11+, checks MANAGE_EXTERNAL_STORAGE.
     * On Android 10 and below, checks READ/WRITE_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns true if the app has at minimum read access to shared storage.
     * Some operations (listing files, reading) only need read.
     */
    fun hasReadAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // --- Initialization and state refresh ---

    /**
     * Refreshes the storage access state. Call on app start, on resume, and
     * after the user returns from the system settings permission screen.
     */
    fun refreshState(context: Context) {
        val hasShared = hasStoragePermission(context)
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val storageDir = File(homeDir, "storage")
        val symlinksExist = storageDir.exists() &&
            File(storageDir, "shared").let { it.exists() || isSymlink(it) }

        val roots = mutableListOf<StorageRoot>()

        // App-private storage (always accessible)
        roots.add(StorageRoot(
            label = "Home",
            path = homeDir.absolutePath,
            writable = true,
            source = StorageSource.APP_PRIVATE
        ))
        val projectsDir = LocalEnvironmentManager.getProjectsDir(context)
        if (projectsDir.exists()) {
            roots.add(StorageRoot(
                label = "Projects",
                path = projectsDir.absolutePath,
                writable = true,
                source = StorageSource.APP_PRIVATE
            ))
        }

        // Shared storage (requires permission)
        if (hasShared) {
            val sharedDir = Environment.getExternalStorageDirectory()
            roots.add(StorageRoot(
                label = "Shared Storage",
                path = sharedDir.absolutePath,
                writable = true,
                source = StorageSource.SHARED_STORAGE
            ))
        }

        // Symlinked directories
        if (symlinksExist) {
            val symlinkNames = listOf(
                "shared" to "Shared",
                "downloads" to "Downloads",
                "dcim" to "DCIM",
                "pictures" to "Pictures",
                "music" to "Music",
                "movies" to "Movies",
                "documents" to "Documents"
            )
            for ((name, label) in symlinkNames) {
                val link = File(storageDir, name)
                if (link.exists() || isSymlink(link)) {
                    roots.add(StorageRoot(
                        label = label,
                        path = link.absolutePath,
                        writable = hasShared,
                        source = StorageSource.SYMLINK
                    ))
                }
            }
        }

        // Removable storage (SD cards)
        try {
            val extDirs = context.getExternalFilesDirs(null)
            if (extDirs != null && extDirs.size > 1) {
                for (i in 1 until extDirs.size) {
                    val dir = extDirs[i] ?: continue
                    roots.add(StorageRoot(
                        label = "SD Card ${i}",
                        path = dir.absolutePath,
                        writable = true,
                        source = StorageSource.REMOVABLE
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enumerate removable storage: ${e.message}")
        }

        val statusMessage = when {
            hasShared && symlinksExist -> "Full access — shared storage and symlinks active"
            hasShared -> "Shared storage access granted"
            else -> "Limited to app-private storage — grant storage permission for full access"
        }

        _accessState.value = StorageAccessState(
            hasSharedStorageAccess = hasShared,
            hasPrivateStorageAccess = true,
            symlinksMounted = symlinksExist,
            statusMessage = statusMessage,
            accessibleRoots = roots,
            apiLevel = Build.VERSION.SDK_INT
        )

        Log.i(TAG, "Storage state refreshed: shared=$hasShared, symlinks=$symlinksExist, roots=${roots.size}")
    }

    /**
     * Sets up storage symlinks and refreshes state.
     * Should be called after storage permission is granted.
     */
    fun setupAndRefresh(context: Context): Boolean {
        val result = TermuxStorageSetupManager.setupStorageSymlinks(context)
        refreshState(context)
        return result
    }

    // --- Path validation ---

    /**
     * Validates that a path is accessible to agents.
     * Returns null if valid, or an error message if the path is restricted.
     */
    fun validatePath(context: Context, path: String): String? {
        val resolved = File(path).canonicalPath

        // Check restricted system paths
        for (restricted in RESTRICTED_PATHS) {
            if (resolved == restricted || resolved.startsWith("$restricted/")) {
                return "Access denied: $restricted is a restricted system location"
            }
        }

        // Check other apps' private data (Android/data/*, Android/obb/*)
        // On Android 11+, even with MANAGE_EXTERNAL_STORAGE these are off-limits
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (resolved.startsWith(ANDROID_DATA_PREFIX) || resolved.startsWith(ANDROID_OBB_PREFIX)) {
                // Allow access to our own app's directories
                val ownPkg = context.packageName
                val ownData = "$ANDROID_DATA_PREFIX$ownPkg"
                val ownObb = "$ANDROID_OBB_PREFIX$ownPkg"
                if (!resolved.startsWith("$ownData/") && resolved != ownData &&
                    !resolved.startsWith("$ownObb/") && resolved != ownObb) {
                    return "Access denied: other apps' private directories are protected by Android"
                }
            }
        }

        // Check if path requires shared storage permission
        val homeDir = LocalEnvironmentManager.getHomeDir(context)
        val isAppPrivate = resolved.startsWith(homeDir.canonicalPath)
        if (!isAppPrivate && !hasStoragePermission(context)) {
            return "Storage permission required: grant All Files Access in system settings"
        }

        return null // Path is valid
    }

    /**
     * Checks if a path is within app-private storage (always accessible).
     */
    fun isAppPrivatePath(context: Context, path: String): Boolean {
        return try {
            val resolved = File(path).canonicalPath
            val homeDir = LocalEnvironmentManager.getHomeDir(context).canonicalPath
            resolved.startsWith(homeDir)
        } catch (_: IOException) {
            false
        }
    }

    // --- Filesystem operations with auditing ---

    sealed class FsResult<out T> {
        data class Success<T>(val value: T) : FsResult<T>()
        data class PermissionDenied(val message: String) : FsResult<Nothing>()
        data class Error(val message: String, val cause: Throwable? = null) : FsResult<Nothing>()
    }

    /**
     * Lists files in a directory with permission checking.
     */
    fun listFiles(context: Context, path: String): FsResult<List<FileInfo>> {
        val error = validatePath(context, path)
        if (error != null) return FsResult.PermissionDenied(error)

        return try {
            val dir = File(path)
            if (!dir.exists()) return FsResult.Error("Directory does not exist: $path")
            if (!dir.isDirectory) return FsResult.Error("Not a directory: $path")

            val files = dir.listFiles()?.map { file ->
                FileInfo(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified(),
                    isReadable = file.canRead(),
                    isWritable = file.canWrite(),
                    isSymlink = isSymlink(file)
                )
            }?.sortedWith(compareBy<FileInfo> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?: emptyList()

            auditLog("LIST", path, true)
            FsResult.Success(files)
        } catch (e: SecurityException) {
            auditLog("LIST", path, false, e.message)
            FsResult.PermissionDenied("Android denied access to $path: ${e.message}")
        } catch (e: Exception) {
            auditLog("LIST", path, false, e.message)
            FsResult.Error("Failed to list $path: ${e.message}", e)
        }
    }

    /**
     * Reads a file with permission checking.
     */
    fun readFile(context: Context, path: String): FsResult<ByteArray> {
        val error = validatePath(context, path)
        if (error != null) return FsResult.PermissionDenied(error)

        return try {
            val file = File(path)
            if (!file.exists()) return FsResult.Error("File does not exist: $path")
            if (!file.isFile) return FsResult.Error("Not a file: $path")
            if (!file.canRead()) return FsResult.PermissionDenied("File is not readable: $path")

            val bytes = file.readBytes()
            auditLog("READ", path, true)
            FsResult.Success(bytes)
        } catch (e: SecurityException) {
            auditLog("READ", path, false, e.message)
            FsResult.PermissionDenied("Android denied read access to $path: ${e.message}")
        } catch (e: Exception) {
            auditLog("READ", path, false, e.message)
            FsResult.Error("Failed to read $path: ${e.message}", e)
        }
    }

    /**
     * Writes content to a file with permission checking.
     * Creates parent directories if needed. Preserves existing file permissions.
     */
    fun writeFile(context: Context, path: String, content: ByteArray, append: Boolean = false): FsResult<Unit> {
        val error = validatePath(context, path)
        if (error != null) return FsResult.PermissionDenied(error)

        return try {
            val file = File(path)
            // Preserve permissions of existing file
            val existingPerms = if (file.exists()) {
                Triple(file.canRead(), file.canWrite(), file.canExecute())
            } else null

            file.parentFile?.mkdirs()

            if (append) {
                file.appendBytes(content)
            } else {
                file.writeBytes(content)
            }

            // Restore permissions
            if (existingPerms != null) {
                file.setReadable(existingPerms.first)
                file.setWritable(existingPerms.second)
                file.setExecutable(existingPerms.third)
            }

            auditLog("WRITE", path, true)
            FsResult.Success(Unit)
        } catch (e: SecurityException) {
            auditLog("WRITE", path, false, e.message)
            FsResult.PermissionDenied("Android denied write access to $path: ${e.message}")
        } catch (e: Exception) {
            auditLog("WRITE", path, false, e.message)
            FsResult.Error("Failed to write $path: ${e.message}", e)
        }
    }

    /**
     * Creates a directory (and parents) with permission checking.
     */
    fun createDirectory(context: Context, path: String): FsResult<Unit> {
        val error = validatePath(context, path)
        if (error != null) return FsResult.PermissionDenied(error)

        return try {
            val dir = File(path)
            if (dir.exists()) {
                if (dir.isDirectory) return FsResult.Success(Unit)
                return FsResult.Error("Path exists but is not a directory: $path")
            }
            if (!dir.mkdirs()) {
                return FsResult.Error("Failed to create directory: $path")
            }
            auditLog("MKDIR", path, true)
            FsResult.Success(Unit)
        } catch (e: SecurityException) {
            auditLog("MKDIR", path, false, e.message)
            FsResult.PermissionDenied("Android denied directory creation at $path: ${e.message}")
        } catch (e: Exception) {
            auditLog("MKDIR", path, false, e.message)
            FsResult.Error("Failed to create directory $path: ${e.message}", e)
        }
    }

    /**
     * Deletes a file or empty directory with permission checking.
     * Does not recursively delete directories for safety.
     */
    fun delete(context: Context, path: String): FsResult<Unit> {
        val error = validatePath(context, path)
        if (error != null) return FsResult.PermissionDenied(error)

        return try {
            val file = File(path)
            if (!file.exists()) return FsResult.Error("Path does not exist: $path")

            // Safety: refuse to delete protected paths
            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            val protectedPaths = listOf(
                "/", "/storage", "/storage/emulated", "/storage/emulated/0", "/sdcard",
                homeDir.absolutePath, homeDir.parentFile?.absolutePath ?: "",
                LocalEnvironmentManager.getProjectsDir(context).absolutePath
            )
            if (file.canonicalPath in protectedPaths.map { File(it).canonicalPath }) {
                return FsResult.PermissionDenied("Cannot delete protected path: $path")
            }

            if (file.isDirectory && (file.listFiles()?.isNotEmpty() == true)) {
                return FsResult.Error("Directory is not empty: $path — use deleteRecursive for non-empty directories")
            }

            if (!file.delete()) {
                return FsResult.Error("Failed to delete: $path")
            }

            auditLog("DELETE", path, true)
            FsResult.Success(Unit)
        } catch (e: SecurityException) {
            auditLog("DELETE", path, false, e.message)
            FsResult.PermissionDenied("Android denied delete access to $path: ${e.message}")
        } catch (e: Exception) {
            auditLog("DELETE", path, false, e.message)
            FsResult.Error("Failed to delete $path: ${e.message}", e)
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     * Protected paths are still refused.
     */
    fun deleteRecursive(context: Context, path: String): FsResult<Unit> {
        val error = validatePath(context, path)
        if (error != null) return FsResult.PermissionDenied(error)

        return try {
            val file = File(path)
            if (!file.exists()) return FsResult.Error("Path does not exist: $path")

            val homeDir = LocalEnvironmentManager.getHomeDir(context)
            val protectedPaths = listOf(
                "/", "/storage", "/storage/emulated", "/storage/emulated/0", "/sdcard",
                homeDir.absolutePath, homeDir.parentFile?.absolutePath ?: "",
                LocalEnvironmentManager.getProjectsDir(context).absolutePath
            )
            if (file.canonicalPath in protectedPaths.map { File(it).canonicalPath }) {
                return FsResult.PermissionDenied("Cannot delete protected path: $path")
            }

            if (!file.deleteRecursively()) {
                return FsResult.Error("Failed to recursively delete: $path")
            }

            auditLog("DELETE_RECURSIVE", path, true)
            FsResult.Success(Unit)
        } catch (e: SecurityException) {
            auditLog("DELETE_RECURSIVE", path, false, e.message)
            FsResult.PermissionDenied("Android denied delete access to $path: ${e.message}")
        } catch (e: Exception) {
            auditLog("DELETE_RECURSIVE", path, false, e.message)
            FsResult.Error("Failed to recursively delete $path: ${e.message}", e)
        }
    }

    /**
     * Renames or moves a file/directory.
     */
    fun rename(context: Context, oldPath: String, newPath: String): FsResult<Unit> {
        val oldError = validatePath(context, oldPath)
        if (oldError != null) return FsResult.PermissionDenied(oldError)
        val newError = validatePath(context, newPath)
        if (newError != null) return FsResult.PermissionDenied(newError)

        return try {
            val oldFile = File(oldPath)
            val newFile = File(newPath)
            if (!oldFile.exists()) return FsResult.Error("Source does not exist: $oldPath")
            if (newFile.exists()) return FsResult.Error("Destination already exists: $newPath")

            newFile.parentFile?.mkdirs()

            if (!oldFile.renameTo(newFile)) {
                return FsResult.Error("Failed to rename $oldPath to $newPath")
            }

            auditLog("RENAME", "$oldPath -> $newPath", true)
            FsResult.Success(Unit)
        } catch (e: SecurityException) {
            auditLog("RENAME", "$oldPath -> $newPath", false, e.message)
            FsResult.PermissionDenied("Android denied rename: ${e.message}")
        } catch (e: Exception) {
            auditLog("RENAME", "$oldPath -> $newPath", false, e.message)
            FsResult.Error("Failed to rename: ${e.message}", e)
        }
    }

    /**
     * Copies a file. Does not copy directories (use the bridge for recursive copy).
     */
    fun copyFile(context: Context, srcPath: String, dstPath: String): FsResult<Unit> {
        val srcError = validatePath(context, srcPath)
        if (srcError != null) return FsResult.PermissionDenied(srcError)
        val dstError = validatePath(context, dstPath)
        if (dstError != null) return FsResult.PermissionDenied(dstError)

        return try {
            val src = File(srcPath)
            val dst = File(dstPath)
            if (!src.exists()) return FsResult.Error("Source does not exist: $srcPath")
            if (!src.isFile) return FsResult.Error("Source is not a file: $srcPath")

            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = false)

            auditLog("COPY", "$srcPath -> $dstPath", true)
            FsResult.Success(Unit)
        } catch (e: SecurityException) {
            auditLog("COPY", "$srcPath -> $dstPath", false, e.message)
            FsResult.PermissionDenied("Android denied copy: ${e.message}")
        } catch (e: FileAlreadyExistsException) {
            FsResult.Error("Destination already exists: $dstPath")
        } catch (e: Exception) {
            auditLog("COPY", "$srcPath -> $dstPath", false, e.message)
            FsResult.Error("Failed to copy: ${e.message}", e)
        }
    }

    // --- Storage info ---

    data class StorageInfo(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val usagePercent: Int
    )

    /**
     * Returns storage space information for the given path.
     */
    fun getStorageInfo(path: String): FsResult<StorageInfo> {
        return try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = total - free
            val percent = if (total > 0) ((used * 100) / total).toInt() else 0

            FsResult.Success(StorageInfo(total, free, used, percent))
        } catch (e: Exception) {
            FsResult.Error("Failed to get storage info for $path: ${e.message}", e)
        }
    }

    // --- File info ---

    data class FileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val isReadable: Boolean,
        val isWritable: Boolean,
        val isSymlink: Boolean
    )

    // --- Audit logging ---

    private val _auditLog = mutableListOf<AuditEntry>()
    private const val MAX_AUDIT_ENTRIES = 500

    data class AuditEntry(
        val timestamp: Long,
        val operation: String,
        val path: String,
        val success: Boolean,
        val error: String? = null
    )

    /** Recent audit log entries. Thread-safe copy. */
    val auditEntries: List<AuditEntry>
        get() = synchronized(_auditLog) { _auditLog.toList() }

    private fun auditLog(operation: String, path: String, success: Boolean, error: String? = null) {
        synchronized(_auditLog) {
            _auditLog.add(AuditEntry(
                timestamp = System.currentTimeMillis(),
                operation = operation,
                path = path,
                success = success,
                error = error
            ))
            if (_auditLog.size > MAX_AUDIT_ENTRIES) {
                _auditLog.removeAt(0)
            }
        }
        if (!success) {
            Log.w(TAG, "AUDIT [$operation] DENIED $path: $error")
        }
    }

    // --- Helpers ---

    private fun isSymlink(file: File): Boolean {
        return try {
            val canon = if (file.parent == null) file else File(file.parentFile?.canonicalFile, file.name)
            canon.canonicalFile != canon.absoluteFile
        } catch (_: Exception) {
            false
        }
    }
}
