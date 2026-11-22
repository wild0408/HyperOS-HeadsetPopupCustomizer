package com.wild0408.headsetpopup.trigger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface RootCheckResult {
    data object Granted : RootCheckResult
    data class Denied(val message: String) : RootCheckResult
}

sealed interface BluetoothRestartResult {
    data class Success(val message: String) : BluetoothRestartResult
    data class NoRoot(val message: String) : BluetoothRestartResult
    data class Failed(val message: String) : BluetoothRestartResult
}

object BluetoothProcessController {
    const val TARGET_PACKAGE = RealFastConnectTrigger.TARGET_PACKAGE

    private const val ROOT_CHECK_TIMEOUT_SEC = 5L
    private const val RESTART_TIMEOUT_SEC = 8L

    suspend fun checkRootAccess(): RootCheckResult = withContext(Dispatchers.IO) {
        val result = runSuCommand("id", ROOT_CHECK_TIMEOUT_SEC)
        when {
            result.ioError != null -> RootCheckResult.Denied("未检测到 su 命令或设备尚未 Root")
            result.timedOut -> RootCheckResult.Denied("Root 授权检测超时，请检查 Root 管理器状态")
            result.exitCode == 0 && result.output.contains("uid=0") -> RootCheckResult.Granted
            else -> RootCheckResult.Denied("未获取 Root 权限，请在 Magisk / KernelSU / APatch 中为本应用授权后重试")
        }
    }

    suspend fun restartBluetoothProcess(): BluetoothRestartResult = withContext(Dispatchers.IO) {
        when (val root = checkRootAccess()) {
            is RootCheckResult.Denied -> return@withContext BluetoothRestartResult.NoRoot(root.message)
            RootCheckResult.Granted -> Unit
        }

        val command = "am force-stop $TARGET_PACKAGE || exit \$?; " +
            "pids=\$(pidof $TARGET_PACKAGE $TARGET_PACKAGE:ui 2>/dev/null); " +
            "if [ -n \"\$pids\" ]; then kill -9 \$pids 2>/dev/null || true; fi"
        val exec = runSuCommand(command, RESTART_TIMEOUT_SEC)
        when {
            exec.ioError != null -> BluetoothRestartResult.Failed("执行重启命令失败：${exec.ioError}")
            exec.timedOut -> BluetoothRestartResult.Failed("重启蓝牙进程超时，请稍后重试")
            exec.exitCode == 0 -> BluetoothRestartResult.Success("已重启 $TARGET_PACKAGE，模块变更将在下次弹窗生效")
            else -> {
                val detail = exec.output.lineSequence().firstOrNull()?.take(120).orEmpty()
                val suffix = if (detail.isNotBlank()) "：$detail" else ""
                BluetoothRestartResult.Failed("重启失败（code=${exec.exitCode}）$suffix")
            }
        }
    }

    private data class CommandOutput(
        val exitCode: Int = -1,
        val output: String = "",
        val timedOut: Boolean = false,
        val ioError: String? = null,
    )

    private fun runSuCommand(shellCommand: String, timeoutSeconds: Long): CommandOutput {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", shellCommand)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                CommandOutput(timedOut = true)
            } else {
                val out = runCatching {
                    process.inputStream.bufferedReader().use { it.readText().trim() }
                }.getOrDefault("")
                CommandOutput(exitCode = process.exitValue(), output = out)
            }
        } catch (error: IOException) {
            CommandOutput(ioError = error.message ?: "无法调用 su")
        } catch (error: Throwable) {
            CommandOutput(ioError = error.message ?: error.javaClass.simpleName)
        } finally {
            runCatching { process?.destroy() }
        }
    }
}
