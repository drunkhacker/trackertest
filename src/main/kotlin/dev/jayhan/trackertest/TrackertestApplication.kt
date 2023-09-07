package dev.jayhan.trackertest

import dev.jayhan.trackertest.CRC16.Companion.getCrc16
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.ip.IpHeaders
import org.springframework.integration.ip.tcp.TcpInboundGateway
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.support.GenericMessage
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

@SpringBootApplication
//@EnableAsync
class TrackertestApplication

fun main(args: Array<String>) {
    runApplication<TrackertestApplication>(*args)
}

@Component
class MyTest(
    private val tcpSendingMessageHandler: MessageHandler
) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
//        send()
    }

    fun send() {
        return
        launch {
            while (!loginReplied.get()) { delay(500) }

            delay(1000)
            logger.info("try to send gprsset message")
            val ba = gprsSetCommand(20)
            val msg = GenericMessage(ba, mapOf(IpHeaders.CONNECTION_ID to connectionId))
            tcpSendingMessageHandler.handleMessage(msg)
//            val sent = inboundGateway.replyChannel?.send(msg)
//            logger.info("sent=$sent")
        }
    }
}

enum class PacketType(val typeCode: Int) {
    LOGIN(0x01),
    GPS(0x31),
    HEARTBEAT(0x13),
    ALARM(0x32),
    LBS(0x50),
    WIFI(0x33),
    GENERAL(0x94),
    UNKNOWN(0xFF)
    ;

    companion object {
        private val map = values().associateBy(PacketType::typeCode)

        fun fromCode(code: Int): PacketType {
            return map[code] ?: UNKNOWN
        }
    }
}

val logger = LoggerFactory.getLogger("TrackerTest")
var loginReplied: AtomicBoolean = AtomicBoolean(false)
var connectionId: String? = null

@ExperimentalUnsignedTypes
fun parse(ba: ByteArray): GPSTrackerMessage? {

    logger.info("parse function, ba.size=${ba.size}")
    logger.info(ba.joinToString(separator = " ") { "0x${it.toUByte().toString(16)}" })
    val dis = DataInputStream(ByteArrayInputStream(ba))
    var cdis: DataInputStream? = null

    // start message
    val stm1 = dis.readUnsignedByte()
    val stm2 = dis.readUnsignedByte()
    if (!(stm1 == 0x78 && stm2 == 0x78 || stm1 == 0x79 && stm2 == 0x79)) {
        logger.error("wrong start of message pkt, stm1=$stm1, stm2=$stm2")
        // discard message
        return null
    }

    // length of msg length field
    val llen = if (stm1 == 0x79) {
        2
    } else {
        1
    }

    // length of message
    val len = if (stm1 == 0x79) {
        dis.readShort().toInt()
    } else {
        dis.readUnsignedByte()
    }
    logger.debug("len=$len")
    val calcCrc = getCrc16(ba, start = 2, len = llen + (len - 2))
    // get crc from ba
    logger.debug("crcbyte=${ba[ba.lastIndex - 1].toUByte().toString(radix = 16)} ${ba[ba.lastIndex].toUByte().toString(radix = 16)}")

    val ch1 = (ba[ba.lastIndex - 1]).toUByte().toUInt().shl(8)
    val ch2 = (ba[ba.lastIndex]).toUByte().toUInt()
    val crc = ch1 + ch2
    if (crc.toUShort() != calcCrc) {
        logger.error("crc check failed, crc=$crc, calcCrc=$calcCrc")
        return null
    }

    try {
        val cba = dis.readNBytes(len)
        cdis = DataInputStream(ByteArrayInputStream(cba))
        val typeCode = cdis.readUnsignedByte()

        val packet = when(val packetType = PacketType.fromCode(typeCode)) {
            PacketType.LOGIN -> parseLoginPacket(cdis)
            PacketType.GPS -> parseGPSPacket(cdis)
            PacketType.HEARTBEAT -> parseHeartbeatPacket(cdis)
            PacketType.GENERAL -> parseGeneralInfoPacket(cdis, len - 6) // protocol(1) + infotype(1) + seq(2) + crc(2)
            PacketType.WIFI -> parseWifiPacket(cdis)
            PacketType.LBS -> parseLBSPacket(cdis)
            else -> {
                logger.error("unknown packet type = $packetType, typeCode = $typeCode")
                return null
            }
        }

        val seq = cdis.readShort()

        // get stop bit ??
        logger.info("packet received = $packet, seq = $seq")

        logger.debug("crc=$crc, calcCrc=$calcCrc")

        packet.seq = seq.toInt()
        return packet

    } catch (e: Throwable) {
        e.printStackTrace()
        logger.warn("exception while parsing bytearray, message=${e.cause?.message}")
        return null
    } finally {
        cdis?.close()
        dis.close()
    }
}

fun parseLoginPacket(dis: DataInputStream): LoginMessage {
    // 78 78 11 01
    // 07 52 53 36 90 02 42 70 = term id
    // 00 32 = model code
    // 05 12
    // 0000 0101 0001 0010
    // 0101 0001 0000

    // 0b 1001 1011 101 1000
    // 79 0D 0A
    logger.debug("parseLoginPacket")
    val terminalId = dis.readLong()
    val modelCode = dis.readUnsignedShort()

    logger.debug("terminalId=$terminalId")
    logger.debug("modelCode=$modelCode")

    // tz & lang
    val b1 = dis.readByte().toUInt()
    val b2 = dis.readByte().toUInt()

    var tz100 = (((b1 shl 8) + (b2 and 0xF0u)) shr 4).toInt()
    val tz_east = (b2 and 0x08u) == 0u
    if (!tz_east) {
        tz100 = -tz100
    }
    val lang_cn = (b2 and 0x02u) > 0u
    val lang_en = (b2 and 0x01u) > 0u

    val tz = ZoneOffset.ofHoursMinutes(tz100 / 100, tz100 % 100)

    return LoginMessage(terminalId, modelCode, tz, lang_cn, lang_en)

}

fun replyLoginPacket(seq: Int): ByteArray {
    val baos = ByteArrayOutputStream()
    val daos = DataOutputStream(baos)
    // start message
    daos.write(0x78)
    daos.write(0x78)

    // length
    daos.write(5)
    // protocol number
    daos.write(0x01)
    // sequence
    daos.writeShort(seq)

    val ba = baos.toByteArray()
    val crc = getCrc16(ba, 2, 4)
    logger.debug("crc=${crc.toInt()}")
    daos.writeShort(crc.toInt())
    return baos.toByteArray().also {
        logger.debug("reply packet = ${it.joinToString(separator = " ") { "0x${it.toUByte().toString(radix = 16)}" }}")
        loginReplied.set(true)
    }
}

fun replyHeartbeatPacket(seq: Int): ByteArray {
    val baos = ByteArrayOutputStream()
    val daos = DataOutputStream(baos)
    // start message
    daos.write(0x78)
    daos.write(0x78)

    // length
    daos.write(5)
    // protocol number
    daos.write(0x13)
    // sequence
    daos.writeShort(seq)

    val ba = baos.toByteArray()
    val crc = getCrc16(ba, 2, 4)
    logger.debug("crc=${crc.toInt()}")
    daos.writeShort(crc.toInt())
    return baos.toByteArray().also {
        logger.debug("reply packet = ${it.joinToString(separator = " ") { "0x${it.toUByte().toString(radix = 16)}" }}")
    }
}

fun replyGeneralInfoPacket(seq: Int, infoType: Int): ByteArray {
    val baos = ByteArrayOutputStream()
    val daos = DataOutputStream(baos)
    // start message
    daos.write(0x79)
    daos.write(0x79)

    // length
    daos.writeShort(6)
    // protocol number
    daos.write(0x94)

    // info Type
    daos.write(infoType)

    // sequence
    daos.writeShort(seq)

    val ba = baos.toByteArray()
    val crc = getCrc16(ba, 2, 4)
    logger.debug("crc=${crc.toInt()}")
    daos.writeShort(crc.toInt())
    return baos.toByteArray().also {
        logger.debug("reply packet = ${it.joinToString(separator = " ") { "0x${it.toUByte().toString(radix = 16)}" }}")
    }
}


fun parseHeartbeatPacket(dis: DataInputStream): HeartbeatMessage {
    logger.debug("parseHeartbeatPacket")
    val termInfoByte = dis.readByte().toInt()
    val terminalInfo = HeartbeatMessage.TerminalInfo(
        fuelConn    = termInfoByte and 0b10000000 != 0,
        gpsTracking = termInfoByte and 0b01000000 != 0,
        // bit 5, bit 4, bit 3 is not used
        charging    = termInfoByte and 0b00000100 != 0,
        acc         = termInfoByte and 0b00000010 != 0,
        defense     = termInfoByte and 0b00000001 != 0,
    )

    val volt = dis.read()
    val gsm = dis.read()
    val langStatus = dis.readUnsignedShort()

    return HeartbeatMessage(
        terminalInfo = terminalInfo,
        voltage = volt,
        gsmStrength = gsm,
        langStatus = langStatus
    )
}


fun parseGPSPacket(dis: DataInputStream): GPSMessage {
    logger.debug("parseGPSPacket")
    // datetime (UTC)
    val year = dis.read() + 2000
    val month = dis.read()
    val day = dis.read()
    val hour = dis.read()
    val min = dis.read()
    val sec = dis.read()
    val datetime = OffsetDateTime.of(year, month, day, hour, min, sec, 0, ZoneOffset.UTC)

    var b = dis.read()
    val gpsInfoLen = (b and 0xF0) shr 4
    val gpsSatNum = (b and 0x0F)

    // lat & lng is represented by 1/500 sec. unit
    val latraw = (dis.read() shl 3*8) + (dis.read() shl 2*8) + (dis.read() shl 1*8) + (dis.read())
    val lngraw = (dis.read() shl 3*8) + (dis.read() shl 2*8) + (dis.read() shl 1*8) + (dis.read())

    val speed = dis.read()

    // 1 degree = 3600 sec, and base unit is 1/500 sec, so divide it by 3600*500 = 1800000 to get the decimal lat, lng value
    logger.debug("latraw = $latraw, lngraw = $lngraw")
    var lat = latraw.toBigDecimal().setScale(9) / BigDecimal.valueOf(1800000)
    var lng = lngraw.toBigDecimal().setScale(9) / BigDecimal.valueOf(1800000)

    // GPS info
    b = dis.read()
    val dgps       = b and 0b00100000 != 0
    val positioned = b and 0b00010000 != 0
    val isEast     = b and 0b00001000 == 0
    val isSouth    = b and 0b00000100 == 0
    val course = dis.read() + ((b and 0x03) shl 6)

    if (isSouth) {
        lat = -lat
    }
    if (!isEast) {
        lng = -lng
    }

    // LBS info
    val mcc = dis.readShort().toInt()
    val mnc = dis.readShort().toInt()
    val lac = dis.readShort().toInt()
    val cellId = dis.readInt()

    val acc = dis.read() > 0
    // not used
    val dataUploadMode = dis.read()
    // not used
    val gpsRealtime = dis.read() == 0

    return GPSMessage(
        datetime = datetime,
        latitude = lat,
        longitude = lng,
        satNum = gpsSatNum,
        gpsInfoLen = gpsInfoLen,
        speed = speed,
        dGPS = dgps,
        positioned = positioned,
        course = course,
        mcc = mcc,
        mnc = mnc,
        lac = lac,
        cellId = cellId,
        acc = acc,
        uploadMode = dataUploadMode,
        gpsRealTime = gpsRealtime
    )
}

fun parseGeneralInfoPacket(dis: DataInputStream, dataSize: Int): GeneralInfoMessage {
    val infoType = dis.read()
    val data = dis.readNBytes(dataSize)

    return GeneralInfoMessage(infoType, data)
}
fun readCellInfo(dis: DataInputStream): CellInfo {
    val lac = dis.readUnsignedShort()
    val ci = dis.readInt()
    val rssi = dis.read()

    return CellInfo(lac, ci, rssi)
}

fun readWifiInfo(dis: DataInputStream): WifiMessage.WifiInfo {
    val mac = dis.readNBytes(6)
    val strength = dis.read()

    return WifiMessage.WifiInfo(mac, strength)
}

fun parseWifiPacket(dis: DataInputStream): WifiMessage {
    val year = dis.read() + 2000
    val month = dis.read()
    val day = dis.read()
    val hour = dis.read()
    val min = dis.read()
    val sec = dis.read()
    val datetime = OffsetDateTime.of(year, month, day, hour, min, sec, 0, ZoneOffset.UTC)

    val mcc = dis.readUnsignedShort()
    val mnc = dis.readUnsignedShort()
    val lac = dis.readUnsignedShort()
    val ci = dis.readInt()
    val rssi = dis.read()

    val otherCells = (1..5).map { readCellInfo(dis) }
    val ta = dis.read()
    val numWifi = dis.read()
    val wifis = (1..5).map { readWifiInfo(dis) }

    return WifiMessage(
        datetime = datetime,
        mcc = mcc,
        mnc = mnc,
        lac = lac,
        cellId = ci,
        rssi = rssi,
        otherCells = otherCells,
        timeAdvance = ta,
        numWifi = numWifi,
        wifis = wifis
    )
}

fun parseLBSPacket(dis: DataInputStream): LBSMessage {
    logger.debug("parseLBSPacket")
    val year = dis.read() + 2000
    logger.debug("year=$year")
    val month = dis.read()
    logger.debug("month=$month")
    val day = dis.read()
    logger.debug("day=$day")
    val hour = dis.read()
    logger.debug("hour=$hour")
    val min = dis.read()
    logger.debug("min=$min")
    val sec = dis.read()
    logger.debug("sec=$sec")
    val datetime = OffsetDateTime.of(year, month, day, hour, min, sec, 0, ZoneOffset.UTC)
    logger.debug("datetime=$datetime")
    val ta = dis.read()
    val mcc = dis.readUnsignedShort()
    val mnc = dis.readUnsignedShort()
    val cellNumber = dis.readUnsignedByte()

//    logger.info("Dis.avail = " + dis.available())
    val cells = (1..5).map { readCellInfo(dis) }
    dis.readNBytes(3) // reserved 3 bytes
    return LBSMessage(datetime, ta, mcc, mnc, cellNumber, cells)
}

fun gprsSetCommand(seq: Int): ByteArray {
    val baos = ByteArrayOutputStream()
    val daos = DataOutputStream(baos)
    // start message
    daos.write(0x78)
    daos.write(0x78)

    val command = "GPRSSET#"

    // length
    // 协议号+信息内容+信息序列号+错误校验
    daos.write(5 + (1 + 4 + command.length + 2))

    // protocol number
    daos.write(0x80)

    // command length
    daos.write(4 + command.length)

    // server flag
    daos.writeInt(0x1234)

    // command
    daos.write(command.toByteArray(Charsets.US_ASCII))

    // lang
    daos.write(0x00)
    daos.write(0x02)

    // sequence
    daos.writeShort(seq)

    val ba = baos.toByteArray()
    val crc = getCrc16(ba, 2, ba.size - 2)
    logger.debug("crc=${crc.toInt()}")
    daos.writeShort(crc.toInt())
    return baos.toByteArray().also {
        logger.debug("server command packet = ${it.joinToString(separator = " ") { "0x${it.toUByte().toString(radix = 16)}" }}")
    }
}

val terminalIdMap: MutableMap<String, Long> = mutableMapOf()

fun handleTrackerMessage(packet: GPSTrackerMessage, connId: String): ByteArray? {
    val termId = terminalIdMap[connId]
    return when (packet) {
        is LoginMessage -> {
            val newTermId = packet.terminalId
            logger.info("[$newTermId] login. socket connectionId=$connId")
            if (termId != null) {
                logger.warn("already associated terminalId exists. connectionId=$connId, terminalId=${termId}")
            }
            terminalIdMap[connId] = newTermId
            replyLoginPacket(packet.seq)
        }
        is HeartbeatMessage -> {
            logger.info("[$termId] heartbeat..")
            replyHeartbeatPacket(packet.seq)
        }
        is GeneralInfoMessage -> replyGeneralInfoPacket(packet.seq, packet.infoType)
        is GPSMessage -> {
            logger.info("[$termId] location=(${packet.latitude}, ${packet.longitude})")
            null
        }

        else -> null
    }
}

