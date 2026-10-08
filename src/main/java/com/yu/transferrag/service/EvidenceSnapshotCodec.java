package com.yu.transferrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import io.qdrant.client.grpc.Points.PointStruct;

/** Versioned private serialization; hashes detect corrupt or unexpected storage. */
public final class EvidenceSnapshotCodec {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private EvidenceSnapshotCodec() {}
    public record Input(int version, String collection, String model, List<TextPoint> points) {}
    public record TextPoint(String id, String text, Map<String,Object> metadata) {}
    public record Wire(int version, String collection, String model, int dimension, List<String> points) {}
    public static String encode(Object value) {
        try {
            String json=JSON.writeValueAsString(value);
            if(json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) throw new IllegalStateException("Snapshot too large");
            return json;
        } catch(Exception error) { throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_SERIALIZATION_FAILED",false); }
    }
    public static <T> T decode(String json, String hash, Class<T> type) {
        if(json==null || !Objects.equals(hash,sha(json))) throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_CORRUPT",false);
        try { return JSON.readValue(json,type); }
        catch(Exception error) { throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_CORRUPT",false); }
    }
    public static String sha(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception error) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static List<PointStruct> points(Wire wire) {
        if(wire.version()!=1 || wire.points().isEmpty()) throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_FORMAT_UNSUPPORTED",false);
        try { return wire.points().stream().map(encoded -> {
            try { return PointStruct.parseFrom(Base64.getDecoder().decode(encoded)); }
            catch(Exception error) { throw new IllegalArgumentException(); }
        }).toList(); }
        catch(Exception error) { throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_CORRUPT",false); }
    }
}
