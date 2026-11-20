package com.bitwig.mods.lockstates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uses Bitwig's bundled Gson; the mod does not ship a second JSON library. */
public final class BankStore {
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path directory;

    public BankStore(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }

    public List<String> ids() throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(p -> p.getFileName().toString().matches("[0-9a-f-]{36}\\.json"))
                    .map(p -> p.getFileName().toString().substring(0, 36)).sorted().toList();
        }
    }

    public StateBank load(String id) throws IOException {
        Path file = file(id);
        if (Files.size(file) > MAX_BYTES) throw new IOException("Bank exceeds 32 MiB");
        StateBank bank = decode(Files.readString(file, StandardCharsets.UTF_8));
        if (!bank.id().equals(id)) throw new IOException("Bank file and ID do not match");
        return bank;
    }

    public void save(StateBank bank) throws IOException {
        byte[] bytes = encode(bank).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Bank exceeds 32 MiB");
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".lock-state-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try { Files.move(temporary, file(bank.id()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) {
                // Do not replace the last valid bank when this filesystem cannot commit atomically.
                throw new IOException("The bank folder does not support atomic replacement", error);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private Path file(String id) { StateBank.uuid(id); return directory.resolve(id + ".json"); }

    public static String encode(StateBank bank) { return JSON.toJson(bank) + "\n"; }

    public static StateBank decode(String text) throws IOException {
        return decode(text, StateBank.class);
    }

    static String encodeDocument(Object data) { return JSON.toJson(data); }

    static <T> T decode(String text, Class<T> type) throws IOException {
        if (text.length() > MAX_BYTES) throw new IOException("Bank exceeds size limit");
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            Object parsed = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing bank data");
            // Gson calls record constructors, including every invariant above.
            T bank = JSON.fromJson(JSON.toJson(parsed), type);
            if (bank == null) throw new IOException("Empty bank");
            return bank;
        } catch (RuntimeException error) { throw new IOException("Invalid state bank: " + error.getMessage(), error); }
    }

    private static Object read(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IOException("Bank nesting limit exceeded");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Map<String, Object> object = new LinkedHashMap<>();
                var keys = new HashSet<String>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (!keys.add(key)) throw new IOException("Duplicate JSON field: " + key);
                    if (keys.size() > StateBank.MAX_PARAMETERS) throw new IOException("Too many JSON fields");
                    object.put(key, read(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); List<Object> values = new ArrayList<>();
                while (reader.hasNext()) {
                    if (values.size() >= StateBank.MAX_PARAMETERS) throw new IOException("Too many JSON entries");
                    values.add(read(reader, depth + 1));
                }
                reader.endArray(); yield values;
            }
            case STRING -> reader.nextString();
            case NUMBER -> new java.math.BigDecimal(reader.nextString());
            case BOOLEAN -> reader.nextBoolean();
            default -> throw new IOException("Unexpected JSON value: " + reader.peek());
        };
    }
}
