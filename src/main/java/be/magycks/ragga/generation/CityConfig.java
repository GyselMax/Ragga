package be.magycks.ragga.generation;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32;

/*
 * every setting that shapes a city + the rng seed, packable into one shareable "city code"
 *
 * byte layout (version 1) — encode and decode must follow this exact order
 *   version                      1  unsigned byte
 *   width, height                4  unsigned short each        1 – 65535
 *   nodeMinimumDistance          2  unsigned short, 0.1 steps  0.0 – 6553.5
 *   nodeMaximumDistanceVariance  2  unsigned short, 0.1 steps  0.1 – 6553.5
 *   nodeCandidateAmount          1  unsigned byte              1 – 255
 *   edgeAlignmentWeight          1  unsigned byte, 0.1 steps   0.0 – 25.5
 *   edgeDistanceWeight           1  unsigned byte, 0.1 steps   0.0 – 25.5
 *   edgeSearchRadius             2  unsigned short             0 – 65535
 *   edgeMinimumAngle             1  unsigned byte, degrees     0 – 180
 *   edgeMaximumDegree            1  unsigned byte              1 – 255
 *   starter count                1  unsigned byte              1 – 255
 *     per starter: x, y          4  unsigned short each        inside width / height
 *                  splitNumber   1  unsigned byte, 0.1 steps   0.1 – 25.5
 *   seed                         8  long
 *   checksum                     1  low byte of a crc32 over everything above
 *
 * one starter node = 31 bytes = a 42 character code
 */
public record CityConfig(
        int width,
        int height,
        double nodeMinimumDistance,
        double nodeMaximumDistanceVariance,
        int nodeCandidateAmount,
        double edgeAlignmentWeight,
        double edgeDistanceWeight,
        int edgeSearchRadius,
        double edgeMinimumAngle,
        int edgeMaximumDegree,
        List<Starter> starters,
        long seed
) {
    // BUMP THIS whenever a generator change would give a different city for the same code
    public static final int VERSION = 1;

    // a starter node aka seed node — kept separate from Node because Node is a jpa entity with an id - just used for values in seed
    public record Starter(double x, double y, double splitNumber) {
        // turns this starter into a fresh graph Node with the same x, y and splitNumber
        public Node toNode() {
            return new Node(x, y, splitNumber);
        }
    }

    // compact constructor — rejects anything the code can't store, so every config is encodable
    public CityConfig {
        checkRange("width", width, 1, 65535);
        checkRange("height", height, 1, 65535);
        checkRange("nodeMinimumDistance", nodeMinimumDistance, 0, 6553.5);
        checkRange("nodeMaximumDistanceVariance", nodeMaximumDistanceVariance, 0.1, 6553.5);  // random.nextDouble(bound) needs bound > 0
        checkRange("nodeCandidateAmount", nodeCandidateAmount, 1, 255);
        checkRange("edgeAlignmentWeight", edgeAlignmentWeight, 0, 25.5);
        checkRange("edgeDistanceWeight", edgeDistanceWeight, 0, 25.5);
        checkRange("edgeSearchRadius", edgeSearchRadius, 0, 65535);
        checkRange("edgeMinimumAngle", edgeMinimumAngle, 0, 180);
        checkRange("edgeMaximumDegree", edgeMaximumDegree, 1, 255);

        starters = List.copyOf(starters);   // immutable copy, so the config can't change after it's built
        checkRange("starter count", starters.size(), 1, 255);
        for (Starter starter : starters) {
            checkRange("starter x", starter.x(), 0, width - 1);
            checkRange("starter y", starter.y(), 0, height - 1);
            checkRange("starter splitNumber", starter.splitNumber(), 0.1, 25.5);  // the generator throws on 0
        }
    }



    /// ENCODING ///
    // packs everything into a url-safe string (letters, digits, - and _)
    public String encode() {
        ByteBuffer buffer = ByteBuffer.allocate(26 + 5 * starters.size());

        buffer.put((byte) VERSION);
        buffer.putShort((short) width);
        buffer.putShort((short) height);
        buffer.putShort((short) tenths(nodeMinimumDistance));
        buffer.putShort((short) tenths(nodeMaximumDistanceVariance));
        buffer.put((byte) nodeCandidateAmount);
        buffer.put((byte) tenths(edgeAlignmentWeight));
        buffer.put((byte) tenths(edgeDistanceWeight));
        buffer.putShort((short) edgeSearchRadius);
        buffer.put((byte) Math.round(edgeMinimumAngle));
        buffer.put((byte) edgeMaximumDegree);

        buffer.put((byte) starters.size());
        for (Starter starter : starters) {
            buffer.putShort((short) Math.round(starter.x()));
            buffer.putShort((short) Math.round(starter.y()));
            buffer.put((byte) tenths(starter.splitNumber()));
        }

        buffer.putLong(seed);
        buffer.put(checksum(buffer.array(), buffer.position()));

        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }



    /// DECODING ///
    // rebuilds a config from a city code — throws IllegalArgumentException with a readable reason if it's broken
    public static CityConfig decode(String code) {
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(code.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("not a city code: it contains characters a code can't have", e);
        }
        if (bytes.length < 2) throw new IllegalArgumentException("not a city code: too short");

        // check the checksum first, so a typo gets reported as a typo instead of as some weird value
        int bodyLength = bytes.length - 1;
        if (checksum(bytes, bodyLength) != bytes[bodyLength]) {
            throw new IllegalArgumentException("city code is damaged, probably a typo or an incomplete paste");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, bodyLength);
        try {
            int version = unsignedByte(buffer);
            if (version != VERSION) {
                throw new IllegalArgumentException("city code is from generator version " + version + ", this is version " + VERSION);
            }

            // one local per field, read top to bottom — the order IS the format
            int width                           = unsignedShort(buffer);
            int height                          = unsignedShort(buffer);
            double nodeMinimumDistance          = unsignedShort(buffer) / 10.0;
            double nodeMaximumDistanceVariance  = unsignedShort(buffer) / 10.0;
            int nodeCandidateAmount             = unsignedByte(buffer);
            double edgeAlignmentWeight          = unsignedByte(buffer) / 10.0;
            double edgeDistanceWeight           = unsignedByte(buffer) / 10.0;
            int edgeSearchRadius                = unsignedShort(buffer);
            double edgeMinimumAngle             = unsignedByte(buffer);
            int edgeMaximumDegree               = unsignedByte(buffer);

            int starterCount = unsignedByte(buffer);
            List<Starter> starters = new ArrayList<>(starterCount);
            for (int i = 0; i < starterCount; i++) {
                double x = unsignedShort(buffer);
                double y = unsignedShort(buffer);
                double splitNumber = unsignedByte(buffer) / 10.0;
                starters.add(new Starter(x, y, splitNumber));
            }

            long seed = buffer.getLong();

            if (buffer.hasRemaining()) throw new IllegalArgumentException("not a city code: too long");

            return new CityConfig(width, height,
                    nodeMinimumDistance, nodeMaximumDistanceVariance, nodeCandidateAmount,
                    edgeAlignmentWeight, edgeDistanceWeight, edgeSearchRadius, edgeMinimumAngle, edgeMaximumDegree,
                    starters, seed);
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("not a city code: too short", e);
        }
    }



    /// CONVENIENCE ///
    // accepts a plain number (default settings + that seed) or a full city code
    // never ambiguous: while VERSION < 4 every code starts with 'A', so a code is never all digits
    public static CityConfig parse(String input, CityConfig defaults) {
        String trimmed = input.strip();
        if (trimmed.matches("-?\\d+")) {
            return defaults.withSeed(Long.parseLong(trimmed));
        }
        return decode(trimmed);
    }

    // same settings, different seed — handy for a "reroll" button
    public CityConfig withSeed(long newSeed) {
        return new CityConfig(width, height,
                nodeMinimumDistance, nodeMaximumDistanceVariance, nodeCandidateAmount,
                edgeAlignmentWeight, edgeDistanceWeight, edgeSearchRadius, edgeMinimumAngle, edgeMaximumDegree,
                starters, newSeed);
    }

    // snaps every value onto the steps the code can store — always generate from this, never from raw input
    public CityConfig normalized() {
        return decode(encode());
    }

    // fresh Node objects for the generator (a new list every call, so two runs never share nodes)
    public List<Node> starterNodes() {
        return starters.stream().map(Starter::toNode).toList();
    }



    /// HELPERS ///
    // stores a one-decimal value as a whole number (4.2 -> 42), so it fits in a byte or short
    private static int tenths(double value) {
        return (int) Math.round(value * 10);
    }

    // reads one byte as 0..255 — java bytes are signed (-128..127), the mask fixes that
    private static int unsignedByte(ByteBuffer buffer) {
        return buffer.get() & 0xFF;
    }

    // reads two bytes as 0..65535 — same mask trick as unsignedByte
    private static int unsignedShort(ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    // one-byte fingerprint of the first `length` bytes (low byte of a crc32) — about 1 in 256 typos slip through
    private static byte checksum(byte[] bytes, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, length);
        return (byte) crc.getValue();
    }

    // throws a readable error if value is outside min..max — written as !(in range) so NaN fails too
    private static void checkRange(String name, double value, double min, double max) {
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ", got " + value);
        }
    }
}
