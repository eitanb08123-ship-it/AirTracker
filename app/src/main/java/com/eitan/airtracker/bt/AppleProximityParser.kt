package com.eitan.airtracker.bt

/**
 * Parses the public "proximity pairing" BLE advertisement that AirPods broadcast
 * (Apple manufacturer id 0x004C, message type 0x07). This only reads data that
 * the earbuds openly advertise to every nearby phone; no private Apple API is used.
 *
 * AirPods broadcast this while the case lid is open or the buds are out of the case.
 * With the lid closed they normally stop advertising, so nothing can be detected.
 *
 * Battery values are a best-effort interpretation of a community-documented format
 * and should be treated as estimates.
 */
data class AppleProximity(
    val modelId: Int,
    val leftPct: Int?,
    val rightPct: Int?,
    val casePct: Int?,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
) {
    val modelName: String get() = AppleProximityParser.modelName(modelId)
    val modelHex: String get() = "0x%04X".format(modelId)
    /** All-zero readings are not real data (format differs on some models) – treat as unavailable. */
    val hasBattery: Boolean
        get() = listOfNotNull(leftPct, rightPct, casePct).any { it > 0 }
}

object AppleProximityParser {
    const val APPLE_ID = 0x004C
    private const val TYPE_PROXIMITY_PAIRING = 0x07

    fun parse(data: ByteArray?): AppleProximity? {
        if (data == null || data.size < 8) return null
        if ((data[0].toInt() and 0xFF) != TYPE_PROXIMITY_PAIRING) return null
        val model = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val status = data[5].toInt() and 0xFF
        val flipped = (status and 0x20) == 0

        val bat = data[6].toInt() and 0xFF
        val hi = bat shr 4
        val lo = bat and 0x0F
        val leftRaw = if (flipped) hi else lo
        val rightRaw = if (flipped) lo else hi

        val b7 = data[7].toInt() and 0xFF
        val chargeFlags = b7 shr 4
        val caseRaw = b7 and 0x0F

        return AppleProximity(
            modelId = model,
            leftPct = toPct(leftRaw),
            rightPct = toPct(rightRaw),
            casePct = toPct(caseRaw),
            leftCharging = (chargeFlags and (if (flipped) 0b010 else 0b001)) != 0,
            rightCharging = (chargeFlags and (if (flipped) 0b001 else 0b010)) != 0,
            caseCharging = (chargeFlags and 0b100) != 0,
        )
    }

    private fun toPct(v: Int): Int? = if (v in 0..10) v * 10 else null

    fun modelName(id: Int): String = when (id) {
        0x2002 -> "AirPods (דור 1)"
        0x200F -> "AirPods (דור 2)"
        0x2013 -> "AirPods (דור 3)"
        0x2019 -> "AirPods 4"
        0x201B -> "AirPods 4 (ANC)"
        0x200E -> "AirPods Pro"
        0x2014, 0x2024 -> "AirPods Pro 2"
        0x200A, 0x201F -> "AirPods Max"
        else -> "אוזניות Apple"
    }
}
