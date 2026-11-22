package com.wild0408.headsetpopup.trigger

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

enum class TriggerDevicePreset(
    val deviceId: String,
    val displayName: String,
    val note: String,
) {
    REDMI_BUDS_8_PRO("01013700", "REDMI Buds 8 Pro", "本机已缓存完整资源"),
    XIAOMI_AIR_4_SE("01011E03", "小米 Air4 SE", "本机已缓存完整资源"),
    XIAOMI_EXPLORE("01011101", "Xiaomi Earphones Explore", "本机已缓存完整资源"),
    XIAOMI_AIR_2S("01010101", "Air 2s", "系统默认兼容预设");

    val label: String
        get() = "$displayName ($deviceId)"

    companion object {
        fun fromDeviceId(deviceId: String?): TriggerDevicePreset =
            entries.firstOrNull { it.deviceId.equals(deviceId, ignoreCase = true) }
                ?: REDMI_BUDS_8_PRO
    }
}

enum class TriggerScenario(
    val key: String,
    val label: String,
    val summary: String,
) {
    DISCOVERY(
        key = "discovery",
        label = "发现设备",
        summary = "触发首次发现 / 一键连接弹窗，完整进入原生配对界面",
    ),
    CONNECTING(
        key = "connecting",
        label = "正在连接",
        summary = "触发连接中状态，预览自定义连接中提示与循环动图/视频",
    ),
    CONNECTED_BATTERY(
        key = "connected",
        label = "已连接（电量）",
        summary = "触发已连接状态并展示左耳、右耳与充电盒电量",
    ),
    CONNECT_FAIL(
        key = "fail",
        label = "连接失败",
        summary = "触发连接失败与重试按钮状态，预览失败文案与动图",
    ),
    OTHER_DEVICE(
        key = "other_device",
        label = "他人设备",
        summary = "触发非本机耳机提示与长按进入配对状态弹窗",
    );

    companion object {
        fun fromKey(key: String?): TriggerScenario =
            entries.firstOrNull { it.key == key } ?: DISCOVERY
    }
}

data class TriggerAvailability(
    val available: Boolean,
    val exported: Boolean,
    val bluetoothEnabled: Boolean,
    val summary: String,
)

sealed interface TriggerResult {
    data class Launched(
        val preset: TriggerDevicePreset,
        val scenario: TriggerScenario,
    ) : TriggerResult

    data class Failed(
        val reason: String,
    ) : TriggerResult
}

object RealFastConnectTrigger {
    const val TARGET_PACKAGE = "com.xiaomi.bluetooth"
    const val TARGET_ACTIVITY = "com.android.bluetooth.ble.app.fastconnect.MiuiFastConnectActivity"
    const val ACTION_FAST_CONNECT_DEVICE = "com.android.bluetooth.FAST_CONNECT_DEVICE"
    const val EXTRA_ACTIVE_TRIGGER = "com.wild0408.headsetpopup.extra.ACTIVE_TRIGGER"
    const val EXTRA_ACTIVE_TRIGGER_STAGE = "com.wild0408.headsetpopup.extra.ACTIVE_TRIGGER_STAGE"

    private const val EXTRA_BLUETOOTH_DEVICE = "android.bluetooth.device.extra.DEVICE"
    private const val EXTRA_HEADSET_ADDRESSES = "headset_addresses"
    private const val EXTRA_HEADSET_MIUI_DATA = "headset_miui_data"
    private const val EXTRA_HEADSET_ADV_ROW_BYTES = "headset_adv_row_bytes"
    private const val EXTRA_HEADSET_EXTRA_DATA = "headset_extra_data"
    private const val EXTRA_CURRENT_A2DP_DEVICES = "current_a2dp_devices"
    private const val EXTRA_AURACAST_ENABLE = "DATA_AURACAST_ENABLE"
    private const val EXTRA_BASS_DEVICE_LIST_SIZE = "DATA_BASS_DEVICE_LIST_SIZE"
    private const val EXTRA_DEVICE_NAME_OVERWRITE = "device_name_over_write"

    private const val SYNTHETIC_RIGHT_MAC = "02:11:22:33:44:55"
    private const val SYNTHETIC_LEFT_MAC = "02:11:22:33:44:56"
    private const val CONNECTED_HOLD_PRIMARY_ADDR = "LOCAL_FASTCONNECT_PRIMARY"
    private const val CONNECTED_HOLD_PEER_ADDR = "LOCAL_FASTCONNECT_PEER"

    private const val LAUNCH_FLAGS =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION

    fun checkAvailability(context: Context): TriggerAvailability {
        val btEnabled = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BLUETOOTH_ON, 0) != 0
        }.getOrDefault(true)

        val probe = Intent(ACTION_FAST_CONNECT_DEVICE).setComponent(
            ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY),
        )
        val resolveInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    probe,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrNull()

        val activityInfo = resolveInfo?.activityInfo
        if (activityInfo == null) {
            return TriggerAvailability(
                available = false,
                exported = false,
                bluetoothEnabled = btEnabled,
                summary = "未检测到 com.xiaomi.bluetooth / MiuiFastConnectActivity",
            )
        }
        if (!activityInfo.exported) {
            return TriggerAvailability(
                available = false,
                exported = false,
                bluetoothEnabled = btEnabled,
                summary = "目标 MiuiFastConnectActivity 未导出，无法直接调起",
            )
        }
        val btNote = if (btEnabled) "蓝牙已开启" else "蓝牙当前关闭（部分版本可能直接退出弹窗）"
        return TriggerAvailability(
            available = true,
            exported = true,
            bluetoothEnabled = btEnabled,
            summary = "已就绪 · 目标已导出 · $btNote",
        )
    }

    fun buildIntent(
        preset: TriggerDevicePreset,
        scenario: TriggerScenario,
    ): Intent {
        return buildIntent(
            deviceId = preset.deviceId,
            displayName = preset.displayName,
            scenario = scenario,
        )
    }

    fun buildIntent(
        deviceId: String,
        displayName: String,
        scenario: TriggerScenario,
    ): Intent {
        val advData = buildMiotV1AdvData(deviceId, scenario)
        val bondConnectFlags = when (scenario) {
            TriggerScenario.DISCOVERY,
            TriggerScenario.CONNECTING,
            TriggerScenario.CONNECT_FAIL,
            TriggerScenario.OTHER_DEVICE,
            -> 0
            TriggerScenario.CONNECTED_BATTERY -> 0x0F
        }
        val addresses = when (scenario) {
            // Non-MAC sentinel bypasses IFastConnectService.getDeviceConnectState() overwrite so
            // the connected battery dialog remains stable during active trigger testing.
            TriggerScenario.CONNECTED_BATTERY -> arrayOf(
                CONNECTED_HOLD_PRIMARY_ADDR,
                CONNECTED_HOLD_PEER_ADDR,
            )
            TriggerScenario.DISCOVERY,
            TriggerScenario.CONNECTING,
            TriggerScenario.CONNECT_FAIL,
            TriggerScenario.OTHER_DEVICE,
            -> arrayOf(SYNTHETIC_RIGHT_MAC, SYNTHETIC_LEFT_MAC)
        }
        val bleDevice = runCatching {
            BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(SYNTHETIC_RIGHT_MAC)
        }.getOrNull()

        return Intent(ACTION_FAST_CONNECT_DEVICE).apply {
            component = ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY)
            flags = LAUNCH_FLAGS
            if (bleDevice != null) {
                putExtra(EXTRA_BLUETOOTH_DEVICE, bleDevice)
            }
            putExtra(EXTRA_HEADSET_ADDRESSES, addresses)
            putExtra(EXTRA_HEADSET_MIUI_DATA, advData)
            putExtra(EXTRA_HEADSET_ADV_ROW_BYTES, ByteArray(0))
            // [0]=rssi, [1]=bond/connect bitmask, [2]=enableDynamicResource(1),
            // [3]=protocolVersion(1 = V1), [4]=pluginMoreSettingEnable(0)
            putExtra(EXTRA_HEADSET_EXTRA_DATA, intArrayOf(-45, bondConnectFlags, 1, 1, 0))
            putExtra(EXTRA_CURRENT_A2DP_DEVICES, 0)
            putExtra(EXTRA_AURACAST_ENABLE, false)
            putExtra(EXTRA_BASS_DEVICE_LIST_SIZE, 0)
            putExtra(EXTRA_DEVICE_NAME_OVERWRITE, displayName)
            putExtra(EXTRA_ACTIVE_TRIGGER, true)
            when (scenario) {
                TriggerScenario.CONNECTING -> putExtra(EXTRA_ACTIVE_TRIGGER_STAGE, "connecting")
                TriggerScenario.CONNECT_FAIL -> putExtra(EXTRA_ACTIVE_TRIGGER_STAGE, "fail")
                else -> Unit
            }
        }
    }

    fun trigger(
        context: Context,
        preset: TriggerDevicePreset,
        scenario: TriggerScenario,
    ): TriggerResult {
        val intent = buildIntent(preset, scenario)
        return try {
            context.startActivity(intent)
            TriggerResult.Launched(preset = preset, scenario = scenario)
        } catch (error: ActivityNotFoundException) {
            TriggerResult.Failed("未找到系统 FastConnect 弹窗组件：${error.message ?: TARGET_ACTIVITY}")
        } catch (error: SecurityException) {
            TriggerResult.Failed("系统拒绝直接启动 MiuiFastConnectActivity：${error.message ?: "权限不足"}")
        } catch (error: Throwable) {
            TriggerResult.Failed("调起真实弹窗失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    /**
     * Encodes a 24-byte MIOT V1 FastConnect advertisement payload compatible with
     * MiuiAdvDataConstantsV1 and MiuiHeadsetInfo in BluetoothExtension v17.
     */
    internal fun buildMiotV1AdvData(
        deviceId: String,
        scenario: TriggerScenario,
    ): ByteArray {
        val normalized = deviceId.trim().uppercase()
        require(normalized.length == 8 && normalized.startsWith("01")) {
            "Unsupported MIOT V1 deviceId: $deviceId"
        }
        val majorHigh = normalized.substring(2, 4).toInt(16)
        val majorLow = normalized.substring(4, 6).toInt(16)
        val minor = normalized.substring(6, 8).toInt(16) and 0x07

        val locationAndScanByte = when (scenario) {
            // Discoverable (0x30) + PageScan/Connectable (0xC0), right ear primary (bit0 = 0)
            TriggerScenario.DISCOVERY,
            TriggerScenario.CONNECTING,
            TriggerScenario.CONNECT_FAIL,
            TriggerScenario.CONNECTED_BATTERY,
            -> ((minor shl 1) or 0xF0)
            // Not discoverable/connectable so controller routes to "other device / long press"
            TriggerScenario.OTHER_DEVICE -> (minor shl 1)
        }
        val classicStateByte = when (scenario) {
            // TWS connected (0x40) | Box open (0x02)
            TriggerScenario.DISCOVERY,
            TriggerScenario.CONNECTING,
            TriggerScenario.CONNECT_FAIL,
            TriggerScenario.CONNECTED_BATTERY,
            -> 0x42
            // Bonded with one phone (0x08) | Connected with other phone (0x04) | Box open (0x02)
            TriggerScenario.OTHER_DEVICE -> 0x0E
        }

        val adv = ByteArray(24)
        adv[0] = 0x16 // CHARACTERISTIC_ID_MIOT (22)
        adv[1] = majorHigh.toByte()
        adv[2] = majorLow.toByte()
        adv[3] = locationAndScanByte.toByte()
        adv[4] = classicStateByte.toByte()
        adv[5] = 96.toByte() // Left ear battery 96%
        adv[6] = 94.toByte() // Right ear battery 94%
        adv[7] = 82.toByte() // Charging case battery 82%
        // [8..10] bonded host MAC suffix (00:00:00 = not bonded to local phone)
        adv[8] = 0x00
        adv[9] = 0x00
        adv[10] = 0x00
        // [11..16] right MAC
        adv[11] = 0x02
        adv[12] = 0x11
        adv[13] = 0x22
        adv[14] = 0x33
        adv[15] = 0x44
        adv[16] = 0x55
        // [17] adv counter
        adv[17] = 0x01
        // [18..23] left MAC
        adv[18] = 0x02
        adv[19] = 0x11
        adv[20] = 0x22
        adv[21] = 0x33
        adv[22] = 0x44
        adv[23] = 0x56
        return adv
    }
}
