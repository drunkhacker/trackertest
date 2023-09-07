package dev.jayhan.trackertest

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

sealed class GPSTrackerMessage {
    var seq: Int = 0
}

data class LoginMessage(
    val terminalId: Long,
    val modelCode: Int,
    val tz: ZoneOffset,
    val cn: Boolean, // lang cn selected?
    val en: Boolean // lang en selected?
) : GPSTrackerMessage()

data class HeartbeatMessage(
    val terminalInfo: TerminalInfo,
    /*
    0x00:No Power (shutdown)
    0x01 :Extremely Low Battery (not enough for calling or sending text messages, etc.)
    0x02:Very Low Battery (Low Battery Alarm)
    0x03:Low Battery (can be used normally)
    0x04:Medium
    0x05:High
    0x06:Full
    */
    val voltage: Int,

    /*
    0x00: no signal
    0x01: extremely weak signal
    0x02: weak signal
    0x03: good signal
    0x04: strong signal
    */
    val gsmStrength: Int,
    // unused..
    val langStatus: Int,
) : GPSTrackerMessage() {
    data class TerminalInfo(
        val fuelConn: Boolean,
        val gpsTracking: Boolean,
        val charging: Boolean,
        val acc: Boolean,
        val defense: Boolean,
    )
}

data class GPSMessage(
    val datetime: OffsetDateTime,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val speed: Int, // km/h
    val dGPS: Boolean,
    val positioned: Boolean,
    val course: Int, // 0 ~ 360

    val satNum: Int,
    val gpsInfoLen: Int,

    // LBS info
    val mcc: Int,
    val mnc: Int,
    val lac: Int,
    val cellId: Int,

    // other
    val acc: Boolean,
    val uploadMode: Int,
    val gpsRealTime: Boolean
): GPSTrackerMessage()

data class GeneralInfoMessage(
    val infoType: Int,
    val data: ByteArray
) : GPSTrackerMessage()

data class CellInfo(
    val lac: Int,
    val cellId: Int,
    val rssi: Int,
)

data class WifiMessage(
    val datetime: OffsetDateTime,
    val mcc: Int,
    val mnc: Int,
    val lac: Int,
    val cellId: Int,
    val rssi: Int,
    val otherCells: List<CellInfo>,
    val timeAdvance: Int,
    val numWifi: Int,
    val wifis: List<WifiInfo>
): GPSTrackerMessage() {
    data class WifiInfo(
        val mac: ByteArray,
        val strength: Int,
    )
}

data class LBSMessage(
    val datetime: OffsetDateTime,
    val timeAdvance: Int,
    val mcc: Int,
    val mnc: Int,
    val cellNumber: Int,
    val cells: List<CellInfo>
): GPSTrackerMessage()
