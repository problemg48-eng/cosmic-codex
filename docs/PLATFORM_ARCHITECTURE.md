# Platform Architecture & Transformation Notes

## 1. Branding & Identity (`BrandConfig`)
- **Centralized configuration**: `app/src/main/java/ai/antigem/theme/BrandConfig.kt` holds `productName`, `tagline`, and dynamic theme-linked accent tokens.
- **Preserved identifiers**: `applicationId` (`ai.cosmic` / `ai.antidev` in debug), `android:sharedUserId="${applicationId}"`, signing configuration, `version.json` update channels, and user-data paths (`filesDir/home`, `filesDir/home/projects`) are preserved to avoid breaking existing installations and Termux:API / Termux:X11 shared-UID integration.
- **Migration plan**: If `applicationId` is ever changed in a future major release, it requires a new signing key or a one-time data export/import migration (`backup_projects` / `restore_projects`) because Android prohibits in-place updates across different `applicationId` or `sharedUserId` values.

## 2. Rootless Shared-Storage Gateway (`StorageGateway` & `/storage` MCP)
- **Files**:
  - `app/src/main/java/ai/antigem/data/storage/StorageGateway.kt`
  - `app/src/main/java/ai/antigem/data/storage/StorageMcp.kt`
  - Registered at `/storage` in `app/src/main/java/ai/antigem/data/remote/AndroidLocalBridgeServer.kt`.
- **Behavior**:
  - No mandatory SAF folder picker, no root, no Shizuku/ADB required for shared storage (`/sdcard`, `Downloads`, `Documents`, etc.).
  - Uses `MANAGE_EXTERNAL_STORAGE` on Android 11+ (API 30+) and `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` on Android 7–10.
  - Preserves existing default workspaces (`~/home`, `~/projects`) and automatically refreshes `~/storage/*` symlinks via `TermuxStorageSetupManager`.
  - Enforces path validation: blocks restricted system paths (`/proc`, `/sys`, `/dev`, `/system`, `/data/data`, `/data/user/0`) and other apps' `Android/data` and `Android/obb` directories on Android 11+.
  - Records every operation in an in-memory bounded audit log (`storage_audit_log`).

## 3. Permissions, Background Execution & Notifications
- **`PermissionManager`** (`app/src/main/java/ai/antigem/data/storage/PermissionManager.kt`): Queries real Android OS permission states (`Storage`, `POST_NOTIFICATIONS`, `RECORD_AUDIO`, `PowerManager.isIgnoringBatteryOptimizations`) and detects permission revocation on `ON_RESUME`.
- **`AgentNotificationManager`** (`app/src/main/java/ai/antigem/data/storage/AgentNotificationManager.kt`): Creates 5 notification channels under the `AI Agent Tasks` group:
  1. `agent_activity` (Agent Activity)
  2. `task_progress` (Task Progress)
  3. `task_completed` (Task Completed)
  4. `task_failed` (Task Failed)
  5. `security_permissions` (Security & Permissions)
- **Settings → Permissions** (`app/src/main/java/ai/antigem/ui/settings/PermissionsScreen.kt`): Live permission status, accessible storage roots, battery exemption action, foreground service status, and audit log viewer.
- **First-Launch Onboarding** (`app/src/main/java/ai/antigem/ui/components/AppPermissionsDialog.kt`): Contextual setup for Notifications, Shared Storage, Battery Optimization / Background Execution, and Microphone, allowing users to continue when optional permissions are skipped while explaining capability limits.

## 4. Standardized Multi-Agent Adapter Architecture
- **Files**:
  - `app/src/main/java/ai/antigem/data/agent/adapter/AgentAdapter.kt`
  - `app/src/main/java/ai/antigem/data/agent/adapter/AgentAdapterRegistry.kt`
  - `app/src/main/java/ai/antigem/ui/agents/AgentsHubScreen.kt`
- **Adapters**:
  - Built-in bridge-backed: Antigravity, OpenAI Codex, Anthropic Claude Code, OpenCode.
  - Termux CLI adapters (probed for real executable binaries in Termux `PATH`): Gemini CLI, Aider, Goose.
  - Honest desktop-only reporting: Cline and Roo Code are marked `IDE_EXTENSION_ONLY` (`UNSUPPORTED_ON_ANDROID`) rather than faking native compatibility.

## 5. Autonomous Task Engine & Workspace UI
- **Orchestration**: `TaskModels.kt` & `AutonomousTaskEngine.kt` in `app/src/main/java/ai/antigem/data/orchestration/` provide role-based planning (`ARCHITECT`, `DEVELOPER`, `TESTER`, `REVIEWER`, etc.), `UserConfirm` approval gates, `FileLock` conflict prevention, disk persistence (`filesDir/tasks/tasks.json`) for process-death recovery, and verification before completion.
- **Primary Screens**:
  - `WorkspaceHomeScreen.kt` (`AppViewMode.HOME`)
  - `ChatScreen.kt` (`AppViewMode.CHAT`)
  - `AgentsHubScreen.kt` (`AppViewMode.AGENTS`)
  - `IdeScreen.kt` (`AppViewMode.IDE`)
  - `MissionsScreen.kt` (`AppViewMode.MISSIONS`)
  - `BrowserScreen.kt` (`AppViewMode.BROWSER`)
  - `SettingsDialog.kt` (including `SettingsSection.PERMISSIONS`)

## 6. Build & Verification
```bash
# Kotlin/Android unit tests and debug APK (requires JDK 17+ & Android SDK)
./gradlew testDebugUnitTest assembleDebug

# Browser extension Node.js tests
node --experimental-vm-modules --test app/src/test/browserextensions/*.test.mjs

# Go bridge tests (requires Go toolchain)
cd agy_ide_bridge && go test ./...
```
