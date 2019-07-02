import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class XMP {
    // starts at 176 of SPD
    public static final int SIZE = 79, 
                            PROFILE1_OFFSET = 9,    // relative to start of
                            PROFILE2_OFFSET = 44;   // XMP data
    private byte[] bytes;
    private boolean[] profileEnabled;   // 178, bits 0 and 1
    private byte[] dimmsPerChannel;     // 178, bits[2:3] and bits[4:5]
    private byte version;               // 179, bits[4:7] | bits[3:0]              
    private Profile[] profile;          // [185, 219], [220, 254]

    public XMP(byte[] bytes) throws IllegalArgumentException {
        if (bytes.length < SIZE) {
            String msg = String.format("Expected %d bytes. Got %d bytes.", SIZE, bytes.length);
            throw new IllegalArgumentException(msg);
        }

        if (bytes[0] != 0x0C || bytes[1] != 0x4A)
            throw new IllegalArgumentException("Invalid XMP header.");

        this.bytes = Arrays.copyOf(bytes, bytes.length);

        profileEnabled = new boolean[] {
            (bytes[2] & 0x1) == 0x1,
            (bytes[2] >> 1 & 0x1) == 0x1
        };

        dimmsPerChannel = new byte[] {
            (byte)(bytes[2] >> 2 & 0x3),
            (byte)(bytes[2] >> 4 & 0x3)
        };

        version = bytes[3];

        if (profileEnabled[0] || profileEnabled[1]) {
            profile = new Profile[2];

            if (profileEnabled[0]) {
                byte[] b = Arrays.copyOfRange(bytes, PROFILE1_OFFSET, PROFILE2_OFFSET);
                profile[0] = new Profile(b, new MTB(bytes[4], bytes[5]));
            }

            if (profileEnabled[1]) {
                byte[] b = Arrays.copyOfRange(bytes, PROFILE2_OFFSET, SIZE);
                profile[1] = new Profile(b, new MTB(bytes[5], bytes[6]));
            }
        }
    }

    public Profile[] getProfiles() { return profile; }

    public Profile getProfile(int index) {
        if (index < 0 || index > 1) return null;
        return profile[index];
    }

    public void setProfile(int index, Profile p) {
        if (index >= 0 && index <= 1 && profile != null) {
            profile[index] = p;
            profileEnabled[index] = true;
        }
    }

    public byte[] getBytes() {
        updateBytes();
        return bytes;
    }

    private void updateBytes() {
        if (profileEnabled[0]) bytes[2] = (byte)1;
        if (profileEnabled[1]) bytes[2] |= (byte)(1 << 1);

        bytes[2] |= (byte)((dimmsPerChannel[0] & 0x3) << 2 |
                           (dimmsPerChannel[1] & 0x3) << 4);

        bytes[3] = version;

        if (profile != null) {
            if (profileEnabled[0]) {
                System.arraycopy(profile[0].getBytes(), 0, bytes, PROFILE1_OFFSET, Profile.SIZE);
                MTB mtb = profile[0].getMTB();
                bytes[4] = mtb.dividend;
                bytes[5] = mtb.divisor;
            }

            if (profileEnabled[1]) {
                System.arraycopy(profile[1].getBytes(), 0, bytes, PROFILE2_OFFSET, Profile.SIZE);
                MTB mtb = profile[1].getMTB();
                bytes[6] = mtb.dividend;
                bytes[7] = mtb.divisor;
            }
        }
    }

    class MTB {
        public byte dividend, divisor;

        public MTB(byte dividend, byte divisor) {
            this.dividend = dividend;
            this.divisor = divisor;
        }

        // returns the MTB time in ns
        public double getTime() {
            if (divisor == 0) return 0;

            return 1.0 * Byte.toUnsignedInt(dividend) /
                         Byte.toUnsignedInt(divisor);
        }
    }

    class Profile {
        public static final int SIZE = 35;
        private byte[] bytes;
        private MTB mtb;        // (180, 181), (182, 183). not in bytes
        private int voltage;    // in mV. 185, 220
        private byte tCKmin;    // 186, 221
        private byte tCLmin;    // 187, 222
        // 188 223: 4 to 11, 189 224: 12 to 18
        private LinkedHashMap<Integer, Boolean> supportedCLs;
        private byte tCWLmin;   // 190, 225
        private byte tRPmin;    // 191, 226
        private byte tRCDmin;   // 192, 227
        private byte tWRmin;    // 193, 228
        // (194 229 bits[3:0]) | (195 230 bits[7:0])
        private short tRASmin;
        // (194 229 bits[7:4]) | (196 231 bits[7:0])
        private short tRCmin;
        // (198 233 bits[7:0]) | (197 232 bits[7:0])
        private short tREFImax;
        // (200 235 bits[7:0]) | (199 234 bits[7:0])
        private short tRFCmin;
        private byte tRTPmin;   // 201, 236
        private byte tRRDmin;   // 202, 237
        // (203 238 bits[3:0]) | (204 239 bits[7:0])
        private short tFAWmin;
        private byte tWTRmin;   // 205, 240

        public Profile(byte[] bytes, MTB mtb) throws IllegalArgumentException {
            if (bytes.length != SIZE) {
                String msg = "Profile must be " + SIZE + " bytes.";
                throw new IllegalArgumentException(msg);
            }

            this.bytes = Arrays.copyOf(bytes, bytes.length);
            this.mtb = mtb;

            voltage = (bytes[0] & 0x1) * 5 +
                      (bytes[0] >> 1 & 0xF) * 10 +
                      (bytes[0] >> 5 & 0x3) * 100;

            tCKmin = bytes[1];
            tCLmin = bytes[2];
            supportedCLs = new LinkedHashMap<>();
            for (int i = 4; i <= 11; i++) {
                int index = i - 4;
                supportedCLs.put(i, ((bytes[3] >> index) & 0x1) == 0x1);
            }
            for (int i = 12; i <= 18; i++) {
                int index = i - 12;
                supportedCLs.put(i, ((bytes[4] >> index) & 0x1) == 0x1);
            }
            tCWLmin = bytes[5];
            tRPmin = bytes[6];
            tRCDmin = bytes[7];
            tWRmin = bytes[8];
            int upper = Byte.toUnsignedInt(bytes[9]) & 0xF,
                lower = Byte.toUnsignedInt(bytes[10]) & 0xFF;
            tRASmin = (short)(upper << 8 | lower);
            upper = Byte.toUnsignedInt(bytes[9]) >> 4 & 0xF;
            lower = Byte.toUnsignedInt(bytes[11]);
            tRCmin = (short)(upper << 8 | lower);
            upper = Byte.toUnsignedInt(bytes[13]) & 0xFF;
            lower = Byte.toUnsignedInt(bytes[12]) & 0xFF;
            tREFImax = (short)(upper << 8 | lower);
            upper = Byte.toUnsignedInt(bytes[15]);
            lower = Byte.toUnsignedInt(bytes[14]);
            tRFCmin = (short)(upper << 8 | lower);
            tRTPmin = bytes[16];
            tRRDmin = bytes[17];
            upper = Byte.toUnsignedInt(bytes[18]) & 0xF;
            lower = Byte.toUnsignedInt(bytes[19]) & 0xFF;
            tFAWmin = (short)(upper << 8 | lower);
            tWTRmin = bytes[20];
        }

        public MTB getMTB() { return mtb; }

        public void setMTB(byte dividend, byte divisor) {
            mtb.dividend = dividend;
            mtb.divisor = divisor;
        }

        public int getVoltage() { return voltage; }

        public void setVoltage(int v) { voltage = v; }

        public byte gettCKmin() { return tCKmin; }

        public void settCKmin(byte b) { tCKmin = b; }

        public double getFrequency() {
            double mtbns = 1.0 * Byte.toUnsignedInt(mtb.dividend) / 
                                 Byte.toUnsignedInt(mtb.divisor);
            return 1000.0 / (Byte.toUnsignedInt(tCKmin) * mtbns);
        }

        public LinkedHashMap<Integer, Boolean> getSupportedCLs() {
            return supportedCLs;
        }

        public boolean setCLSupported(int cl, boolean supported) {
            if (!supportedCLs.containsKey(cl)) return false;
    
            supportedCLs.put(cl, supported);
            return true;
        }

        public LinkedHashMap<String, Integer> getTimings() {
            LinkedHashMap<String, Integer> timings = new LinkedHashMap<>();

            double tCKns = Byte.toUnsignedInt(tCKmin) * mtb.getTime(),
                   tCLns = Byte.toUnsignedInt(tCLmin) * mtb.getTime(),
                   tCL = Math.round(tCLns / tCKns),
                   tCWLns = Byte.toUnsignedInt(tCWLmin) * mtb.getTime(),
                   tCWL = Math.round(tCWLns / tCKns),
                   tRPns = Byte.toUnsignedInt(tRPmin) * mtb.getTime(),
                   tRP = Math.round(tRPns / tCKns),
                   tRCDns = Byte.toUnsignedInt(tRCDmin) * mtb.getTime(),
                   tRCD = Math.round(tRCDns / tCKns),
                   tWRns = Byte.toUnsignedInt(tWRmin) * mtb.getTime(),
                   tWR = Math.round(tWRns / tCKns),
                   tRASns = Short.toUnsignedInt(tRASmin) * mtb.getTime(),
                   tRAS = Math.round(tRASns / tCKns),
                   tRCns = Short.toUnsignedInt(tRCmin) * mtb.getTime(),
                   tRC = Math.round(tRCns / tCKns),
                   tREFIns = Short.toUnsignedInt(tREFImax) * mtb.getTime(),
                   tREFI = Math.round(tREFIns / tCKns),
                   tRFCns = Short.toUnsignedInt(tRFCmin) * mtb.getTime(),
                   tRFC = Math.round(tRFCns / tCKns),
                   tRTPns = Byte.toUnsignedInt(tRTPmin) * mtb.getTime(),
                   tRTP = Math.round(tRTPns / tCKns),
                   tRRDns = Byte.toUnsignedInt(tRRDmin) * mtb.getTime(),
                   tRRD = Math.round(tRRDns / tCKns),
                   tFAWns = Short.toUnsignedInt(tFAWmin) * mtb.getTime(),
                   tFAW = Math.round(tFAWns / tCKns),
                   tWTRns = Byte.toUnsignedInt(tWTRmin) * mtb.getTime(),
                   tWTR = Math.round(tWTRns / tCKns);

            timings.put("tCL", (int)tCL);
            timings.put("tRCD", (int)tRCD);
            timings.put("tRP", (int)tRP);
            timings.put("tRAS", (int)tRAS);
            timings.put("tRC", (int)tRC);
            timings.put("tRFC", (int)tRFC);
            timings.put("tRRD", (int)tRRD);
            timings.put("tFAW", (int)tFAW);
            timings.put("tWR", (int)tWR);
            timings.put("tWTR", (int)tWTR);
            timings.put("tRTP", (int)tRTP);
            timings.put("tCWL", (int)tCWL);
            timings.put("tREFI", (int)tREFI);

            return timings;
        }

        public boolean setTiming(String timing, int ticks) {
            if (ticks < 1) return false;

            int value = (int)Math.round(ticks * Byte.toUnsignedInt(tCKmin));

            if (timing.equals("tCL")) {
                if (!supportedCLs.containsKey(ticks)) return false;
                supportedCLs.put(value, true);
    
                tCLmin = (byte)value;
            }
            else if (timing.equals("tRCD")) {
                tRCDmin = (byte)value;
            }
            else if (timing.equals("tRP")) {
                tRPmin = (byte)value;
            }
            else if (timing.equals("tRAS")) {
                tRASmin = (short)value;
            }
            else if (timing.equals("tRC")) {
                tRCmin = (short)value;
            }
            else if (timing.equals("tRFC")) {
                tRFCmin = (short)value;
            }
            else if (timing.equals("tRRD")) {
                tRRDmin = (byte)value;
            }
            else if (timing.equals("tFAW")) {
                tFAWmin = (short)value;
            }
            else if (timing.equals("tWR")) {
                tWRmin = (byte)value;
            }
            else if (timing.equals("tWTR")) {
                tWTRmin = (byte)value;
            }
            else if (timing.equals("tRTP")) {
                tRTPmin = (byte)value;
            }
            else if (timing.equals("tCWL")) {
                tCWLmin = (byte)value;
            }
            else if (timing.equals("tREFI")) {
                tREFImax = (byte)value;
            }
            else return false;
    
            return true;
        }

        public void setTimings(LinkedHashMap<String, Integer> timings) {
            for (Map.Entry<String, Integer> e : timings.entrySet())
                setTiming(e.getKey(), e.getValue());
        }

        public byte[] getBytes() { 
            updateBytes();
            return bytes; 
        }

        private void updateBytes() {
            String v = String.valueOf(voltage);
            if (v.charAt(v.length() - 1) == '5')
                bytes[0] = (byte)1;
            else bytes[0] = 0;
            bytes[0] |= (voltage / 10 % 10) << 1;
            bytes[0] |= ((voltage / 100 % 100) & 0x3) << 5;

            bytes[1] = tCKmin;
            bytes[2] = tCLmin;

            int n = 0;
            for (int i = 4; i <= 11; i++) {
                int index = i - 4;
                if (supportedCLs.get(i)) n |= (1 << index);
            }
            bytes[3] = (byte)n;
            n = 0;
            for (int i = 12; i <= 18; i++) {
                int index = i - 12;
                if (supportedCLs.get(i)) n |= (1 << index);
            }
            bytes[4] = (byte)n;

            bytes[5] = tCWLmin;
            bytes[6] = tRPmin;
            bytes[7] = tRCDmin;
            bytes[8] = tWRmin;
            byte upperNibble = (byte)(tRCmin >> 8 & 0xF),
                 lowerNibble = (byte)(tRASmin >> 8 & 0xF);
            bytes[9] = (byte)(upperNibble << 4 | lowerNibble);
            bytes[10] = (byte)(tRASmin & 0xFF);
            bytes[11] = (byte)(tRCmin & 0xFF);
            bytes[12] = (byte)(tREFImax & 0xFF);
            bytes[13] = (byte)(tREFImax >> 8 & 0xFF);
            bytes[14] = (byte)(tRFCmin & 0xFF);
            bytes[15] = (byte)(tRFCmin >> 8 & 0xFF);
            bytes[16] = tRTPmin;
            bytes[17] = tRRDmin;
            bytes[18] = (byte)(tFAWmin >> 8 & 0xF);
            bytes[19] = (byte)(tFAWmin & 0xFF);
            bytes[20] = tWTRmin;
        }
    }
}