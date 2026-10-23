package cl.nexbyte.siger.entrega;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import tools.jackson.databind.json.JsonMapper;

/** Solo demostración local: un escritor, publicación atómica; no reemplaza almacenamiento productivo. */
final class ArchivosDemo implements AutoCloseable {
    final Path directory;
    private final JsonMapper mapper;
    private final FileChannel lockChannel;
    private final FileLock lock;
    ArchivosDemo(Path directory, JsonMapper mapper) throws IOException {
        this.directory = directory.toAbsolutePath().normalize(); this.mapper = mapper;
        Files.createDirectories(this.directory);
        lockChannel = FileChannel.open(this.directory.resolve("writer.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (RuntimeException | IOException e) { lockChannel.close(); throw e; }
        if (acquired == null) { lockChannel.close(); throw new IOException("Directorio de demostración en uso"); }
        lock = acquired;
    }
    <T> T read(String name, Class<T> type) throws IOException {
        return mapper.readValue(Files.readAllBytes(directory.resolve(name)), type);
    }
    boolean exists(String name) { return Files.exists(directory.resolve(name)); }
    void write(String name, Object value) throws IOException {
        var temp = Files.createTempFile(directory, "pending-", ".tmp");
        try {
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(mapper.writeValueAsBytes(value));
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    public void close() throws IOException { try { lock.release(); } finally { lockChannel.close(); } }
}
