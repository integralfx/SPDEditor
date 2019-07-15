import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.Arrays
import kotlin.math.round

class SPDEditor @Throws(IllegalArgumentException::class)
constructor(bytes: UByteArray) {
    private val bytes: UByteArray
    // 0x06: 1.25v, 1.35v, not 1.50v
    val voltages: LinkedHashMap<String, Boolean>
    private var tCKmin: UByte = 0u            // 0x0C
    // 0x0E: 11 to 4, 0x0F: 18 to 12
    val supportedCLs: LinkedHashMap<UInt, Boolean>

    private var tCLmin: UByte = 0u            // 0x10
    private var tWRmin: UByte = 0u            // 0x11
    private var tRCDmin: UByte = 0u           // 0x12
    private var tRRDmin: UByte = 0u           // 0x13
    private var tRPmin: UByte = 0u            // 0x14
    private var tRASmin: UShort = 0u          // (0x15 bits[3:0]) | (0x16 bits[7:0])
    private var tRCmin: UShort = 0u           // (0x15 bits[7:4]) | (0x17 bits[7:0])
    private var tRFCmin: UShort = 0u          // (0x19 bits[7:0]) | (0x18 bits[7:0])
    private var tWTRmin: UByte = 0u           // 0x1A
    private var tRTPmin: UByte = 0u           // 0x1B
    private var tFAWmin: UShort = 0u          // (0x1C bits[3:0]) | (0x1D bits[7:0])

    // all in picoseconds (1 thousandth of a ns)
    private var tCKminCorrection: UByte = 0u  // 0x22
    private var tCLminCorrection: UByte = 0u  // 0x23
    private var tRCDminCorrection: UByte = 0u // 0x24
    private var tRPminCorrection: UByte = 0u  // 0x25
    private var tRCminCorrection: UByte = 0u  // 0x26

    var xmp: XMP? = null
        private set                // 0xB0, can be null if no XMP

    var frequency: Double
        get() {
            var tCKns = tCKmin.toInt().toDouble() / 8.0 +
                        tCKminCorrection.toInt().toDouble() / 1000.0
            tCKns = getMorePrecisetCKns(tCKns)
            return 1000 / tCKns
        }
        set(freq) {
            val tCKns = 1000 / freq
            val value = (tCKns * 8).toInt()
            val correction = round(1000 * (tCKns * 8 - value) / 8)

            tCKmin = value.toUByte()
            tCKminCorrection = correction.toInt().toUByte()
        }

    var timings: LinkedHashMap<String, UInt>
        get() {
            val timings = LinkedHashMap<String, UInt>()

            var tCKns = tCKmin.toInt().toDouble() / 8.0 +
                        tCKminCorrection.toInt().toDouble() / 1000.0
            tCKns = getMorePrecisetCKns(tCKns)

            val tCLns = tCLmin.toInt() / 8.0 + tCLminCorrection.toInt() / 1000.0
            val tCL = round(tCLns / tCKns)
            val tRCDns = tRCDmin.toInt() / 8.0 + tRCDminCorrection.toInt() / 1000.0
            val tRCD = round(tRCDns / tCKns)
            val tRPns = tRPmin.toInt() / 8.0 + tRPminCorrection.toInt() / 1000.0
            val tRP = round(tRPns / tCKns)
            val tRASns = tRASmin.toInt() / 8.0
            val tRAS = round(tRASns / tCKns)
            val tRCns = tRCmin.toInt() / 8.0 + tRCminCorrection.toInt() / 1000.0
            val tRC = round(tRCns / tCKns)
            val tRFCns = tRFCmin.toInt() / 8.0
            val tRFC = round(tRFCns / tCKns)
            val tRRDns = tRRDmin.toInt() / 8.0
            val tRRD = round(tRRDns / tCKns)
            val tFAWns = tFAWmin.toInt().toInt() / 8.0
            val tFAW = round(tFAWns / tCKns)
            val tWRns = tWRmin.toInt() / 8.0
            val tWR = round(tWRns / tCKns)
            val tWTRns = tWTRmin.toInt() / 8.0
            val tWTR = round(tWTRns / tCKns)
            val tRTPns = tRTPmin.toInt() / 8.0
            val tRTP = round(tRTPns / tCKns)

            timings["tCL"] = tCL.toInt().toUInt()
            timings["tRCD"] = tRCD.toInt().toUInt()
            timings["tRP"] = tRP.toInt().toUInt()
            timings["tRAS"] = tRAS.toInt().toUInt()
            timings["tRC"] = tRC.toInt().toUInt()
            timings["tRFC"] = tRFC.toInt().toUInt()
            timings["tRRD"] = tRRD.toInt().toUInt()
            timings["tFAW"] = tFAW.toInt().toUInt()
            timings["tWR"] = tWR.toInt().toUInt()
            timings["tWTR"] = tWTR.toInt().toUInt()
            timings["tRTP"] = tRTP.toInt().toUInt()

            return timings
        }
        set(timings) {
            for ((key, value) in timings)
                setTiming(key, value)
        }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            /*
            try {
                File f = new File("c-die_2133C11-13-12.bin");
                SPDEditor spd = new SPDEditor(Files.readAllBytes(f.toPath()));

                spd.setFrequency(800);
                spd.setTiming("tCL", 10);
                spd.setTiming("tRCD", 10);
                spd.setTiming("tRP", 10);
                spd.setTiming("tRAS", 30);
                spd.setCLSupported(11, false);
                spd.save("test.bin");
            }
            catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
            */
        }
    }

    init {
        if (bytes.size < 128) {
            throw IllegalArgumentException("Expected 128 bytes. Got " + bytes.size + " bytes.")
        }

        this.bytes = bytes.copyOf()

        voltages = LinkedHashMap()
        voltages["1.25v"] = bytes[0x06].toUInt() shr 2 and 0x1u == 0x1u
        voltages["1.35v"] = bytes[0x06].toUInt() shr 1 and 0x1u == 0x1u
        voltages["1.50v"] = bytes[0x06].toUInt() and 0x1u != 0x1u // not 1.50v

        tCKmin = bytes[0x0C]

        supportedCLs = LinkedHashMap()
        for (i in 4..11) {
            val index = i - 4
            supportedCLs[i.toUInt()] = bytes[0x0E].toUInt() shr index and 0x1u == 0x1u
        }
        for (i in 12..18) {
            val index = i - 12
            supportedCLs[i.toUInt()] = bytes[0x0F].toUInt() shr index and 0x1u == 0x1u
        }

        tCLmin = bytes[0x10]
        tWRmin = bytes[0x11]
        tRCDmin = bytes[0x12]
        tRRDmin = bytes[0x13]
        tRPmin = bytes[0x14]
        var msb = bytes[0x15].toUInt() and 0xFu
        tRASmin = (msb shl 8 or bytes[0x16].toUInt()).toUShort()
        msb = bytes[0x15].toUInt() shr 4 and 0xFu
        tRCmin = (msb shl 8 or bytes[0x17].toUInt()).toUShort()
        tRFCmin = (bytes[0x19].toUInt() shl 8 or bytes[0x18].toUInt()).toUShort()
        tWTRmin = bytes[0x1A]
        tRTPmin = bytes[0x1B]
        msb = bytes[0x1C].toUInt() and 0xFu
        tFAWmin = (msb shl 8 or bytes[0x1D].toUInt()).toUShort()

        tCKminCorrection = bytes[0x22]
        tCLminCorrection = bytes[0x23]
        tRCDminCorrection = bytes[0x24]
        tRPminCorrection = bytes[0x25]
        tRCminCorrection = bytes[0x26]

        try {
            xmp = XMP(bytes.copyOfRange(0xB0, 0xB0 + XMP.SIZE))
        } catch (e: IllegalArgumentException) {
            xmp = null
            println("No XMP found.")
        }

    }

    fun setVoltage(voltage: String, enabled: Boolean): Boolean {
        if (!voltages.containsKey(voltage)) return false

        voltages[voltage] = enabled
        return true
    }

    fun setCLSupported(cl: UInt, supported: Boolean): Boolean {
        if (!supportedCLs.containsKey(cl)) return false

        supportedCLs[cl] = supported
        return true
    }

    fun setTiming(timing: String, ticks: UInt): Boolean {
        if (ticks < 1u) return false

        var tCKns = tCKmin.toInt() / 8.0 + tCKminCorrection.toInt() / 1000.0
        tCKns = getMorePrecisetCKns(tCKns)

        val ns = ticks.toInt() * tCKns
        val value = (ns * 8).toInt()
        val correction = round(1000 * (ns * 8 - value) / 8).toInt()

        when (timing) {
            "tCL" -> {
                if (!supportedCLs.containsKey(ticks)) return false
                supportedCLs[value.toUInt()] = true

                tCLmin = value.toUByte()
                tCLminCorrection = correction.toUByte()
            }
            "tRCD" -> {
                tRCDmin = value.toUByte()
                tRCDminCorrection = correction.toUByte()
            }
            "tRP" -> {
                tRPmin = value.toUByte()
                tRPminCorrection = correction.toUByte()
            }
            "tRAS" -> tRASmin = value.toUShort()
            "tRC" -> {
                tRCmin = value.toUShort()
                tRCminCorrection = correction.toUByte()
            }
            "tRFC" -> tRFCmin = value.toUShort()
            "tRRD" -> tRRDmin = value.toUByte()
            "tFAW" -> tFAWmin = value.toUShort()
            "tWR" -> tWRmin = value.toUByte()
            "tWTR" -> tWTRmin = value.toUByte()
            "tRTP" -> tRTPmin = value.toUByte()
            else -> return false
        }

        return true
    }

    fun printTimings() {
        for ((key, value) in timings) {
            println("$key: $value")
        }
    }

    fun save(filename: String): Boolean {
        updateBytes()

        val crc = crc16XModem().toUInt()
        // little endian - LSB goes last
        bytes[0x7F] = (crc shr 8 and 0xFFu).toUByte()
        bytes[0x7E] = (crc and 0xFFu).toUByte()

        try {
            FileOutputStream(filename).use { fos ->
                fos.write(bytes.toByteArray())
                return true
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return false
    }

    private fun getMorePrecisetCKns(tCKns: Double): Double {
        val value = round(1000 * tCKns).toInt()
        when (value) {
            938 -> return 0.9375                   // 1066.666...
            1071 -> return 1000 / (2800 / 3.0)     // 933.333...
            else -> return tCKns
        }
    }

    // source: http://mdfs.net/Info/Comp/Comms/CRC16.htm
    private fun crc16XModem(): Short {
        var crc = 0
        for (i in 0..0x74) {
            crc = crc xor (bytes[i].toInt() shl 8)

            for (j in 0..7) {
                crc = crc shl 1
                if (crc and 0x10000 == 0x10000)
                    crc = crc xor 0x1021 and 0xFFFF
            }
        }

        return crc.toShort()
    }

    private fun updateBytes() {
        var n = 0
        if (voltages["1.25v"]!!) n = n or (1 shl 2)
        if (voltages["1.35v"]!!) n = n or (1 shl 1)
        if (!voltages["1.50v"]!!) n = n or 1
        bytes[0x06] = n.toUByte()

        bytes[0x0C] = tCKmin

        n = 0
        for (i in 4..11) {
            val index = i - 4
            if (supportedCLs[i.toUInt()]!!) n = n or (1 shl index)
        }
        bytes[0x0E] = n.toUByte()
        n = 0
        for (i in 12..18) {
            val index = i - 12
            if (supportedCLs[i.toUInt()]!!) n = n or (1 shl index)
        }
        bytes[0x0F] = n.toUByte()

        bytes[0x10] = tCLmin
        bytes[0x11] = tWRmin
        bytes[0x12] = tRCDmin
        bytes[0x13] = tRRDmin
        bytes[0x14] = tRPmin
        var msb = tRASmin.toUInt() shr 8 and 0xFu
        bytes[0x15] = msb.toUByte()
        bytes[0x16] = (tRASmin and 0xFFu).toUByte()
        msb = tRCmin.toUInt() shr 8 and 0xFFu
        bytes[0x15] = bytes[0x15] or (msb shl 4).toUByte()
        bytes[0x17] = (tRCmin and 0xFFu).toUByte()
        bytes[0x18] = (tRFCmin and 0xFFu).toUByte()
        bytes[0x19] = (tRFCmin.toUInt() shr 8 and 0xFu).toUByte()
        bytes[0x1A] = tWTRmin
        bytes[0x1B] = tRTPmin
        bytes[0x1C] = (tFAWmin.toUInt() shr 8 and 0xFu).toUByte()
        bytes[0x1D] = (tFAWmin.toUInt() and 0xFFu).toUByte()

        bytes[0x22] = tCKminCorrection
        bytes[0x23] = tCLminCorrection
        bytes[0x24] = tRCDminCorrection
        bytes[0x25] = tRPminCorrection
        bytes[0x26] = tRCminCorrection

        if (xmp != null)
            xmp!!.bytes.copyInto(bytes, 0xB0)
    }
}