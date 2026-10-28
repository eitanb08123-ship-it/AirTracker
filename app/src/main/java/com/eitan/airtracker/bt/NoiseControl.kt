package com.eitan.airtracker.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class NoiseMode(val code: Byte, val label: String) {
    OFF(0x01, "כבוי"),
    ANC(0x02, "חסימת רעשים"),
    TRANSPARENCY(0x03, "שקיפות"),
    ADAPTIVE(0x04, "מותאם"),
}

sealed class NoiseResult {
    /** Command was delivered to the earbuds. They may still ignore it (non-ANC models). */
    data object Sent : NoiseResult()
    data class Failed(val reason: String) : NoiseResult()
}

/**
 * Sends a noise-control command over Apple's accessory protocol (L2CAP PSM 0x1001), the
 * same open-source approach used by LibrePods. Many Android builds (including Samsung before
 * One UI 9 / Android 17) refuse this connection, and AirPods without ANC have no such mode,
 * so the result is reported honestly instead of pretending it worked.
 */
object NoiseControl {
    private const val TAG = "NoiseControl"
    private const val PSM = 0x1001

    private val HANDSHAKE = bytes(0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val FEATURES = bytes(0x04, 0x00, 0x04, 0x00, 0x4D, 0x00, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val NOTIFICATIONS = bytes(0x04, 0x00, 0x04, 0x00, 0x0F, 0x00, 0xFF, 0xFF, 0xFE, 0xFF)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun modePacket(m: NoiseMode) = bytes(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, m.code.toInt(), 0x00, 0x00, 0x00)

    @SuppressLint("MissingPermission")
    suspend fun setMode(device: BluetoothDevice, mode: NoiseMode): NoiseResult = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createInsecureL2capChannel(PSM)
            val connected = withTimeoutOrNull(6_000L) {
                runCatching { socket.connect() }.isSuccess
            } ?: false
            if (!connected || !socket.isConnected) {
                return@withContext NoiseResult.Failed(
                    "הטלפון לא מאפשר את החיבור הזה לאוזניות. בסמסונג זה צפוי לעבוד רק החל מ-One UI 9 (Android 17)."
                )
            }
            val out = socket.outputStream
            out.write(HANDSHAKE); out.flush(); delay(150)
            out.write(FEATURES); out.flush(); delay(150)
            out.write(NOTIFICATIONS); out.flush(); delay(150)
            out.write(modePacket(mode)); out.flush()
            delay(300)
            NoiseResult.Sent
        } catch (e: SecurityException) {
            NoiseResult.Failed("חסרה הרשאת Bluetooth.")
        } catch (e: Exception) {
            Log.w(TAG, "noise control failed", e)
            NoiseResult.Failed("החיבור לאוזניות נכשל (${e.javaClass.simpleName}). ודא שהן מחוברות לטלפון.")
        } finally {
            runCatching { socket?.close() }
        }
    }
}
