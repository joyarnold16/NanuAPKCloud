package com.nanu.aitradingbot;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Single-process ownership and fsynced atomic replacement. No silent fallback to a blank account. */
public final class FileLedgerStorage implements PaperLedger.Storage, AutoCloseable {
    private final Path path;
    private final FileChannel lockChannel;
    private final FileLock lock;
    public FileLedgerStorage(Path path) throws Exception {
        this.path = path.toAbsolutePath();
        Files.createDirectories(this.path.getParent());
        lockChannel = FileChannel.open(this.path.resolveSibling(this.path.getFileName() + ".lock"),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (Exception e) { lockChannel.close(); throw e; }
        if (acquired == null) { lockChannel.close(); throw new IllegalStateException("Another engine owns the paper ledger"); }
        lock = acquired;
    }
    @Override public String read() throws Exception {
        return Files.exists(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : null;
    }
    @Override public void write(String snapshot) throws Exception {
        Path temporary = path.resolveSibling(path.getFileName() + ".next");
        try (FileChannel out = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(snapshot);
            while (buffer.hasRemaining()) out.write(buffer);
            out.force(true);
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        // Directory fsync is supported on Linux. Windows does not expose directory channels.
        if (!System.getProperty("os.name").startsWith("Windows")) {
            try (FileChannel directory = FileChannel.open(path.getParent(), StandardOpenOption.READ)) { directory.force(true); }
        }
    }
    @Override public void close() throws Exception { lock.release(); lockChannel.close(); }
}
