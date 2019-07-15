import kotlin.math.roundToInt

class XMP(bytes: UByteArray) {
    companion object {
        const val SIZE = 79
        const val PROFILE1_OFFSET = 9
        const val PROFILE2_OFFSET = 44
    }

    init {
        if (bytes.size < SIZE)
            throw IllegalArgumentException("Expected $SIZE bytes. Got $bytes.size bytes")

        if (bytes[0].compareTo(0x0Cu) != 0 || bytes[1].compareTo(0x4Au) != 0)
            throw IllegalArgumentException("Invalid XMP header.")
    }

    private val _bytes = bytes.copyOf()
    val bytes: UByteArray
        get() {
            updateBytes()
            return _bytes
        }

    private val profileEnabled = booleanArrayOf(
        bytes[2].toInt() and 0x1 == 0x1,
        bytes[2].toInt() shr 0x1 == 0x1
    )
    private val dimmsPerChannel = byteArrayOf(
        (bytes[2].toInt() shr 2 and 0x3).toByte(),
        (bytes[2].toInt() shr 4 and 0x3).toByte()
    )
    private val version = bytes[3]

    var profile: Array<Profile?>? = null

    init {
        if (profileEnabled[0] || profileEnabled[1]) {
            profile = Array(2){null}

            if (profileEnabled[0]) {
                val b = bytes.copyOfRange(PROFILE1_OFFSET, PROFILE2_OFFSET)
                profile!![0] = Profile(b, MTB(bytes[4], bytes[5]))
            }

            if (profileEnabled[1]) {
                val b = bytes.copyOfRange(PROFILE2_OFFSET, SIZE)
                profile!![0] = Profile(b, MTB(bytes[6], bytes[7]))
            }
        }
    }

    private fun updateBytes() {
        if (profileEnabled[0]) _bytes[2] = (1).toUByte()
        if (profileEnabled[1]) _bytes[2] = (_bytes[2].toInt() or 2).toUByte()

        val b = (dimmsPerChannel[0].toInt() and 0x3) shl 2 or
                (dimmsPerChannel[1].toInt() and 0x3) shl 4
        _bytes[2] = (_bytes[2].toInt() or b).toUByte()

        _bytes[3] = version

        if (profile != null) {
            if (profileEnabled[0]) {
                profile!![0]!!.bytes.copyInto(_bytes, PROFILE1_OFFSET)
                _bytes[4] = profile!![0]!!.mtb.dividend
                _bytes[5] = profile!![0]!!.mtb.divisor
            }

            if (profileEnabled[1]) {
                profile!![0]!!.bytes.copyInto(_bytes, PROFILE2_OFFSET)
                _bytes[6] = profile!![1]!!.mtb.dividend
                _bytes[7] = profile!![1]!!.mtb.divisor
            }
        }
    }

    class MTB(val dividend: UByte, val divisor: UByte) {
        fun getTime(): Double {
            if (divisor.toInt() == 0) return 0.0
            return dividend.toInt().toDouble() / divisor.toInt().toDouble()
        }
    }

    class Profile(bytes: UByteArray, var mtb: MTB) {
        companion object {
            const val SIZE = 35
        }

        private val _bytes = bytes.copyOf()
        val bytes: UByteArray
            get() {
                updateBytes()
                return _bytes
            }
        var voltage: UInt
        var tCKmin: UByte
        private var tCLmin: UByte
        var supportedCLs: LinkedHashMap<UInt, Boolean>
            private set
        private var tCWLmin: UByte
        private var tRPmin: UByte
        private var tRCDmin: UByte
        private var tWRmin: UByte
        private var tRASmin: UShort
        private var tRCmin: UShort
        private var tREFImax: UShort
        private var tRFCmin: UShort
        private var tRTPmin: UByte
        private var tRRDmin: UByte
        private var tFAWmin: UShort
        private var tWTRmin: UByte

        init {
            if (bytes.size != SIZE)
                throw IllegalArgumentException("Profile must be $SIZE bytes.")

            voltage = ((bytes[0].toInt() and 0x1) * 5 +
                       (bytes[0].toInt() shr 1 and 0xF) * 10 +
                       (bytes[0].toInt() shr 5 and 0x3) * 100).toUInt()

            tCKmin = bytes[1]
            tCLmin = bytes[2]
            supportedCLs = LinkedHashMap()
            for (i in 4..11) {
                val index = i - 4
                supportedCLs[i.toUInt()] = ((bytes[3].toUInt() shr index) and 0x1u) == 0x1u
            }
            for (i in 12..18) {
                val index = i - 12
                supportedCLs[i.toUInt()] = ((bytes[4].toUInt() shr index) and 0x1u) == 0x1u
            }
            tCWLmin = bytes[5]
            tRPmin = bytes[6]
            tRCDmin = bytes[7]
            tWRmin = bytes[8]
            var upper = bytes[9].toUInt() and 0xFu
            var lower = bytes[10].toUInt() and 0xFFu
            tRASmin = (upper shl 8 or lower).toUShort()
            upper = bytes[9].toUInt() shr 4 and 0xFu
            lower = bytes[11].toUInt()
            tRCmin = (upper shl 8 or lower).toUShort()
            upper = bytes[13].toUInt() and 0xFFu
            lower = bytes[12].toUInt() and 0xFFu
            tREFImax = (upper shl 8 or lower).toUShort()
            upper = bytes[15].toUInt()
            lower = bytes[14].toUInt()
            tRFCmin = (upper shl 8 or lower).toUShort()
            tRTPmin = bytes[16]
            tRRDmin = bytes[17]
            upper = bytes[18].toUInt() and 0xFu
            lower = bytes[19].toUInt() and 0xFFu
            tFAWmin = (upper shl 8 or lower).toUShort()
            tWTRmin = bytes[20]
        }

        fun getFrequency(): Double {
            val mtbns = mtb.dividend.toInt().toDouble() / mtb.divisor.toInt().toDouble()
            return 1000.0 / (tCKmin.toInt().toDouble() * mtbns)
        }

        fun setCLSupported(cl: UInt, supported: Boolean): Boolean {
            if (!supportedCLs.containsKey(cl)) return false

            supportedCLs[cl] = supported
            return true
        }

        fun getTimings(): LinkedHashMap<String, UInt> {
            val timings = LinkedHashMap<String, UInt>()

            val tCKns = tCKmin.toInt().toDouble() * mtb.getTime()
            val tCLns = tCLmin.toInt().toDouble() * mtb.getTime()
            val tCL = Math.round(tCLns / tCKns)
            val tCWLns = tCWLmin.toInt().toDouble() * mtb.getTime()
            val tCWL = Math.round(tCWLns / tCKns)
            val tRPns = tRPmin.toInt().toDouble() * mtb.getTime()
            val tRP = Math.round(tRPns / tCKns)
            val tRCDns = tRCDmin.toInt().toDouble() * mtb.getTime()
            val tRCD = Math.round(tRCDns / tCKns)
            val tWRns = tWRmin.toInt().toDouble() * mtb.getTime()
            val tWR = Math.round(tWRns / tCKns)
            val tRASns = tRASmin.toInt().toDouble() * mtb.getTime()
            val tRAS = Math.round(tRASns / tCKns)
            val tRCns = tRCmin.toInt().toDouble() * mtb.getTime()
            val tRC = Math.round(tRCns / tCKns)
            val tREFIns = tREFImax.toInt().toDouble() * mtb.getTime()
            val tREFI = Math.round(tREFIns / tCKns)
            val tRFCns = tRFCmin.toInt().toDouble() * mtb.getTime()
            val tRFC = Math.round(tRFCns / tCKns)
            val tRTPns = tRTPmin.toInt().toDouble() * mtb.getTime()
            val tRTP = Math.round(tRTPns / tCKns)
            val tRRDns = tRRDmin.toInt().toDouble() * mtb.getTime()
            val tRRD = Math.round(tRRDns / tCKns)
            val tFAWns = tFAWmin.toInt().toDouble() * mtb.getTime()
            val tFAW = Math.round(tFAWns / tCKns)
            val tWTRns = tWTRmin.toInt().toDouble() * mtb.getTime()
            val tWTR = Math.round(tWTRns / tCKns)

            timings["tCL"] = tCL.toUInt()
            timings["tRCD"] = tRCD.toUInt()
            timings["tRP"] = tRP.toUInt()
            timings["tRAS"] = tRAS.toUInt()
            timings["tRC"] = tRC.toUInt()
            timings["tRFC"] = tRFC.toUInt()
            timings["tRRD"] = tRRD.toUInt()
            timings["tFAW"] = tFAW.toUInt()
            timings["tWR"] = tWR.toUInt()
            timings["tWTR"] = tWTR.toUInt()
            timings["tRTP"] = tRTP.toUInt()
            timings["tCWL"] = tCWL.toUInt()
            timings["tREFI"] = tREFI.toUInt()

            return timings
        }

        fun setTiming(timing: String, ticks: UInt): Boolean {
            if (ticks < 1u) return false

            val value = (ticks.toInt().toDouble() * tCKmin.toInt().toDouble()).roundToInt().toUInt()

            when (timing) {
                "tCL" -> {
                    if (!supportedCLs.containsKey(ticks)) return false
                    supportedCLs[value] = true

                    tCLmin = value.toUByte()
                }
                "tRCD" -> tRCDmin = value.toUByte()
                "tRP" -> tRPmin = value.toUByte()
                "tRAS" -> tRASmin = value.toUShort()
                "tRC" -> tRCmin = value.toUShort()
                "tRFC" -> tRFCmin = value.toUShort()
                "tRRD" -> tRRDmin = value.toUByte()
                "tFAW" -> tFAWmin = value.toUShort()
                "tWR" -> tWRmin = value.toUByte()
                "tWTR" -> tWTRmin = value.toUByte()
                "tRTP" -> tRTPmin = value.toUByte()
                "tCWL" -> tCWLmin = value.toUByte()
                "tREFI" -> tREFImax = value.toUShort()
                else -> return false
            }

            return true
        }

        fun setTimings(timings: LinkedHashMap<String, UInt>) {
            for ((key, value) in timings) setTiming(key, value)
        }

        private fun updateBytes() {
            val v = voltage.toString()
            if (v[v.length - 1] == '5')
                _bytes[0] = 1.toUByte()
            else _bytes[0] = 0u
            _bytes[0] = _bytes[0] or (voltage / 10u % 10u shl 1).toUByte()
            _bytes[0] = _bytes[0] or (voltage / 100u % 100u and 0x3u shl 5).toUByte()

            _bytes[1] = tCKmin
            _bytes[2] = tCLmin

            var n = 0
            for (i in 4..11) {
                val index = i - 4
                if (supportedCLs[i.toUInt()]!!) n = n or (1 shl index)
            }
            _bytes[3] = n.toUByte()
            n = 0
            for (i in 12..18) {
                val index = i - 12
                if (supportedCLs[i.toUInt()]!!) n = n or (1 shl index)
            }
            _bytes[4] = n.toUByte()

            _bytes[5] = tCWLmin
            _bytes[6] = tRPmin
            _bytes[7] = tRCDmin
            _bytes[8] = tWRmin
            val upperNibble = (tRCmin.toUInt() shr 8 and 0xFu)
            val lowerNibble = (tRASmin.toUInt() shr 8 and 0xFu)
            _bytes[9] = (upperNibble shl 4 or lowerNibble).toUByte()
            _bytes[10] = (tRASmin.toUInt() and 0xFFu).toUByte()
            _bytes[11] = (tRCmin.toUInt() and 0xFFu).toUByte()
            _bytes[12] = (tREFImax.toUInt() and 0xFFu).toUByte()
            _bytes[13] = (tREFImax.toUInt() shr 8 and 0xFFu).toUByte()
            _bytes[14] = (tRFCmin.toUInt() and 0xFFu).toUByte()
            _bytes[15] = (tRFCmin.toUInt() shr 8 and 0xFFu).toUByte()
            _bytes[16] = tRTPmin
            _bytes[17] = tRRDmin
            _bytes[18] = (tFAWmin.toUInt() shr 8 and 0xFu).toUByte()
            _bytes[19] = (tFAWmin.toUInt() and 0xFFu).toUByte()
            _bytes[20] = tWTRmin
        }
    }
}