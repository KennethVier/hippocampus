package com.hippocampus.materials.domain;
import java.nio.ByteBuffer; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.security.NoSuchAlgorithmException; import java.util.UUID;
public final class ChunkIdentity {
    public static final String VERSION = "CHUNKER_V1";
    public static final UUID NAMESPACE = UUID.fromString("bc9da86d-4f4f-5f4f-9f59-8cc72e70b86a");
    private ChunkIdentity() {}
    public static UUID forChunk(UUID version, int index) {
        if (index < 1) throw new IllegalArgumentException("chunkIndex must be positive");
        String name = VERSION + "\n" + version.toString().toLowerCase(java.util.Locale.ROOT) + "\n" + index;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(ByteBuffer.allocate(16).putLong(NAMESPACE.getMostSignificantBits()).putLong(NAMESPACE.getLeastSignificantBits()).array());
            byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
            hash[6] = (byte)((hash[6] & 0x0f) | 0x50); hash[8] = (byte)((hash[8] & 0x3f) | 0x80);
            ByteBuffer bytes = ByteBuffer.wrap(hash); return new UUID(bytes.getLong(), bytes.getLong());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
