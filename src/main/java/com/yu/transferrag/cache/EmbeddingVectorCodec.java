package com.yu.transferrag.cache;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/** Exact IEEE-754 floats with input/model binding and corruption detection, no Java deserialization. */
public final class EmbeddingVectorCodec {
    private static final int MAGIC = 0x4e4a5501;
    private EmbeddingVectorCodec() {}
    public static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static String sha(String text) {
        return HexFormat.of().formatHex(digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    public static void validate(float[] vector, int dimensions) {
        if (vector == null || vector.length != dimensions) throw new IllegalArgumentException("Invalid embedding dimensions");
        boolean nonzero = false;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite embedding");
            nonzero |= value != 0;
        }
        if (!nonzero) throw new IllegalArgumentException("Zero embedding");
    }
    public static byte[] encode(float[] vector, int dimensions, String signature, String inputHash) {
        validate(vector, dimensions);
        ByteBuffer b = ByteBuffer.allocate(104 + dimensions * 4);
        b.putInt(MAGIC).putInt(dimensions).put(HexFormat.of().parseHex(signature)).put(HexFormat.of().parseHex(inputHash));
        for (float value : vector) b.putFloat(value);
        b.put(digest(Arrays.copyOf(b.array(), b.position())));
        return b.array();
    }
    public static float[] decode(byte[] bytes, int dimensions, String signature, String inputHash) {
        if (bytes.length != 104 + dimensions * 4) throw new IllegalArgumentException("Invalid cache entry size");
        ByteBuffer b = ByteBuffer.wrap(bytes);
        if (b.getInt() != MAGIC || b.getInt() != dimensions) throw new IllegalArgumentException("Invalid cache entry format");
        byte[] model = new byte[32], input = new byte[32];b.get(model).get(input);
        if (!MessageDigest.isEqual(model, HexFormat.of().parseHex(signature))
                || !MessageDigest.isEqual(input, HexFormat.of().parseHex(inputHash))
                || !MessageDigest.isEqual(digest(Arrays.copyOf(bytes, bytes.length - 32)), Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length)))
            throw new IllegalArgumentException("Cache entry binding/checksum mismatch");
        float[] result = new float[dimensions];for (int i=0;i<dimensions;i++) result[i]=b.getFloat();
        validate(result, dimensions);return result;
    }
}
