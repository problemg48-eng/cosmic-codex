package ai.cosmic.data.storage

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP tool surface for [StorageGateway].
 * Exposed at `/storage` on [ai.cosmic.data.remote.AndroidLocalBridgeServer] so all agents
 * (Codex, Claude Code, OpenCode, Antigravity, etc.) can access shared Android storage
 * rootlessly without SAF folder selection, with full path validation and auditing.
 */
object StorageMcp {
    const val INSTRUCTIONS =
        "Rootless Android shared-storage gateway. Use storage_status to verify permissions and " +
        "accessible roots before working with shared storage (/sdcard, Downloads, Documents, etc.). " +
        "App-private storage (~/home, ~/projects) is always accessible. Other apps' Android/data " +
        "and Android/obb directories and system paths (/system, /proc, /data/data) are restricted."

    fun tools(): JSONArray = JSONArray().apply {
        put(tool("storage_status", "Check shared storage permission status, revocation state, and accessible roots.", emptyMap()))
        put(tool("storage_list", "List files and directories at a path.", mapOf(
            "path" to prop("string", "Absolute path to list (e.g. /sdcard/Download or ~/projects)", required = true)
        )))
        put(tool("storage_read", "Read a file's contents (UTF-8 text or base64 for binary).", mapOf(
            "path" to prop("string", "Absolute path to file", required = true),
            "encoding" to prop("string", "'utf8' (default) or 'base64'")
        )))
        put(tool("storage_write", "Write or append content to a file, creating parent directories if needed.", mapOf(
            "path" to prop("string", "Absolute path to file", required = true),
            "content" to prop("string", "File content (UTF-8 string or base64 if encoding='base64')", required = true),
            "encoding" to prop("string", "'utf8' (default) or 'base64'"),
            "append" to prop("boolean", "Append instead of overwriting (default false)")
        )))
        put(tool("storage_mkdir", "Create a directory and any missing parent directories.", mapOf(
            "path" to prop("string", "Absolute directory path", required = true)
        )))
        put(tool("storage_rename", "Rename or move a file or directory.", mapOf(
            "oldPath" to prop("string", "Existing path", required = true),
            "newPath" to prop("string", "Destination path", required = true)
        )))
        put(tool("storage_copy", "Copy a file to a new destination.", mapOf(
            "srcPath" to prop("string", "Source file path", required = true),
            "dstPath" to prop("string", "Destination file path", required = true)
        )))
        put(tool("storage_delete", "Delete a file or directory.", mapOf(
            "path" to prop("string", "Path to delete", required = true),
            "recursive" to prop("boolean", "Recursively delete non-empty directories (default false)")
        )))
        put(tool("storage_info", "Get total, free, and used storage space for a path.", mapOf(
            "path" to prop("string", "Directory path (default /sdcard)")
        )))
        put(tool("storage_audit_log", "View recent storage gateway operations and any denied/revoked attempts.", mapOf(
            "limit" to prop("integer", "Max entries to return (default 50)")
        )))
    }

    fun call(context: Context, toolName: String, args: JSONObject): JSONObject {
        StorageGateway.refreshState(context)
        PermissionManager.refresh(context)

        return when (toolName) {
            "storage_status" -> {
                val st = StorageGateway.accessState.value
                val perm = PermissionManager.state.value
                val roots = JSONArray().apply {
                    st.accessibleRoots.forEach { r ->
                        put(JSONObject().apply {
                            put("label", r.label)
                            put("path", r.path)
                            put("writable", r.writable)
                            put("source", r.source.name)
                        })
                    }
                }
                ok(JSONObject().apply {
                    put("hasSharedStorageAccess", st.hasSharedStorageAccess)
                    put("hasPrivateStorageAccess", st.hasPrivateStorageAccess)
                    put("symlinksMounted", st.symlinksMounted)
                    put("permissionStatus", perm.storage.name)
                    put("permissionRevoked", perm.storage == PermissionManager.PermissionStatus.REVOKED)
                    put("statusMessage", st.statusMessage)
                    put("apiLevel", st.apiLevel)
                    put("accessibleRoots", roots)
                })
            }

            "storage_list" -> {
                val path = args.optString("path", "")
                when (val res = StorageGateway.listFiles(context, path)) {
                    is StorageGateway.FsResult.Success -> {
                        val arr = JSONArray().apply {
                            res.value.forEach { f ->
                                put(JSONObject().apply {
                                    put("name", f.name)
                                    put("path", f.path)
                                    put("isDirectory", f.isDirectory)
                                    put("size", f.size)
                                    put("lastModified", f.lastModified)
                                    put("readable", f.isReadable)
                                    put("writable", f.isWritable)
                                    put("isSymlink", f.isSymlink)
                                })
                            }
                        }
                        ok(JSONObject().put("path", path).put("entries", arr))
                    }
                    is StorageGateway.FsResult.PermissionDenied -> err("PERMISSION_DENIED: ${res.message}")
                    is StorageGateway.FsResult.Error -> err(res.message)
                }
            }

            "storage_read" -> {
                val path = args.optString("path", "")
                val encoding = args.optString("encoding", "utf8").lowercase()
                when (val res = StorageGateway.readFile(context, path)) {
                    is StorageGateway.FsResult.Success -> {
                        val content = if (encoding == "base64") {
                            Base64.encodeToString(res.value, Base64.NO_WRAP)
                        } else {
                            String(res.value, Charsets.UTF_8)
                        }
                        ok(JSONObject().apply {
                            put("path", path)
                            put("encoding", encoding)
                            put("size", res.value.size)
                            put("content", content)
                        })
                    }
                    is StorageGateway.FsResult.PermissionDenied -> err("PERMISSION_DENIED: ${res.message}")
                    is StorageGateway.FsResult.Error -> err(res.message)
                }
            }

            "storage_write" -> {
                val path = args.optString("path", "")
                val content = args.optString("content", "")
                val encoding = args.optString("encoding", "utf8").lowercase()
                val append = args.optBoolean("append", false)
                val bytes = try {
                    if (encoding == "base64") Base64.decode(content, Base64.DEFAULT)
                    else content.toByteArray(Charsets.UTF_8)
                } catch (e: Exception) {
                    return err("Invalid $encoding content: ${e.message}")
                }
                when (val res = StorageGateway.writeFile(context, path, bytes, append)) {
                    is StorageGateway.FsResult.Success -> ok(JSONObject().put("path", path).put("bytesWritten", bytes.size))
                    is StorageGateway.FsResult.PermissionDenied -> err("PERMISSION_DENIED: ${res.message}")
                    is StorageGateway.FsResult.Error -> err(res.message)
                }
            }

            "storage_mkdir" -> {
                val path = args.optString("path", "")
                when (val res = StorageGateway.createDirectory(context, path)) {
                    is StorageGateway.FsResult.Success -> ok(JSONObject().put("path", path).put("created", true))
                    is StorageGateway.FsResult.PermissionDenied -> err("PERMISSION_DENIED: ${res.message}")
                    is StorageGateway.FsResult.Error -> err(res.message)
                }
            }

            "storage_rename" -> {
                val oldPath = args.optString("oldPath", "")
                val newPath = args.optString("newPath", "")
                when (val res = StorageGateway.rename(context, oldPath, newPath)) {
                    is StorageGateway.FsResult.Success -> ok(JSONObject().put("oldPath", oldPath).put("newPath", newPath))
                    is StorageGateway.FsResult.PermissionDenied -> err("PERMISSION_DENIED: ${res.message}")
                    is StorageGateway.FsResult.Error -> err(res.message)
                }
            }

            "storage_copy" -> {
                val srcPath = args.optString("srcPath", "")
                val dstPath = args.optString("dstPath", "")
                when (val res = StorageGateway.copyFile(context, srcPath, dstPath)) {
                    is StorageGateway.FsResult.Success -> ok(JSONObject().put("srcPath", srcPath).put("dstPath", dstPath))
                    is StorageGateway.FsResult.PermissionDenied -> err("PERMISSION_DENIED: ${res.message}")
                    is StorageGateway.FsResult.Error -> err(res.message)
                }
            }

            "storage_delete" -> {
                val path = args.optString("path", "")
                val recursive = args.optBoolean("recursive", false)
                val res = if (recursive) StorageGateway.deleteRecursive(context, path)
                else StorageGateway.delete(context, path)
                when (res) {
                    is StorageGateway.FsResult.Success -> ok(JSONObject().put("path", path).put("deleted", true))
                    is StorageGateway.FsResult.PermissionDenied -> err("PERMISSION_DENIED: ${res.message}")
                    is StorageGateway.FsResult.Error -> err(res.message)
                }
            }

            "storage_info" -> {
                val path = args.optString("path", "/sdcard").ifBlank { "/sdcard" }
                when (val res = StorageGateway.getStorageInfo(path)) {
                    is StorageGateway.FsResult.Success -> ok(JSONObject().apply {
                        put("path", path)
                        put("totalBytes", res.value.totalBytes)
                        put("freeBytes", res.value.freeBytes)
                        put("usedBytes", res.value.usedBytes)
                        put("usagePercent", res.value.usagePercent)
                    })
                    is StorageGateway.FsResult.PermissionDenied -> err("PERMISSION_DENIED: ${res.message}")
                    is StorageGateway.FsResult.Error -> err(res.message)
                }
            }

            "storage_audit_log" -> {
                val limit = args.optInt("limit", 50).coerceIn(1, 500)
                val entries = StorageGateway.auditEntries.takeLast(limit)
                val arr = JSONArray().apply {
                    entries.forEach { e ->
                        put(JSONObject().apply {
                            put("timestamp", e.timestamp)
                            put("operation", e.operation)
                            put("path", e.path)
                            put("success", e.success)
                            if (e.error != null) put("error", e.error)
                        })
                    }
                }
                ok(JSONObject().put("count", entries.size).put("entries", arr))
            }

            else -> err("Unknown storage tool: $toolName")
        }
    }

    private fun ok(data: JSONObject): JSONObject = JSONObject().apply {
        put("content", JSONArray().put(JSONObject().apply {
            put("type", "text")
            put("text", data.toString(2))
        }))
        put("isError", false)
    }

    private fun err(message: String): JSONObject = JSONObject().apply {
        put("content", JSONArray().put(JSONObject().apply {
            put("type", "text")
            put("text", message)
        }))
        put("isError", true)
    }

    private data class PropSpec(val type: String, val description: String, val required: Boolean)

    private fun prop(type: String, description: String, required: Boolean = false) =
        PropSpec(type, description, required)

    private fun tool(name: String, description: String, props: Map<String, PropSpec>): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()
        for ((k, v) in props) {
            properties.put(k, JSONObject().put("type", v.type).put("description", v.description))
            if (v.required) required.put(k)
        }
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("inputSchema", JSONObject().apply {
                put("type", "object")
                put("properties", properties)
                if (required.length() > 0) put("required", required)
            })
        }
    }
}
