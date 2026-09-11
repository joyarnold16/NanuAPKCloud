package com.nanu.aitradingbot;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class FileLedgerStorageTest {
    @Test public void diskRestartRecoversPendingPanic() throws Exception {
        Path directory = Files.createTempDirectory("nanu-paper-test"); Path file = directory.resolve("ledger.json");
        try {
            try (FileLedgerStorage storage = new FileLedgerStorage(file)) {
                PaperLedger book = new PaperLedger(storage); long now = System.currentTimeMillis();
                book.open(PaperFixtures.good(now), new PaperLedger.Limits(), now, "paper"); book.panic();
            }
            try (FileLedgerStorage storage = new FileLedgerStorage(file)) {
                PaperLedger restored = new PaperLedger(storage); assertTrue(restored.halted()); assertNotNull(restored.position());
            }
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory.resolve("ledger.json.lock")); Files.deleteIfExists(directory); }
    }
    @Test public void secondWriterCannotAcquireSameLedger() throws Exception {
        Path directory = Files.createTempDirectory("nanu-lock-test"); Path file = directory.resolve("ledger.json");
        try {
            try (FileLedgerStorage first = new FileLedgerStorage(file)) {
                assertThrows(Exception.class, () -> new FileLedgerStorage(file));
            }
        } finally { Files.deleteIfExists(directory.resolve("ledger.json.lock")); Files.deleteIfExists(directory); }
    }
    @Test public void corruptFileIsPreservedAndCannotTrade() throws Exception {
        Path directory = Files.createTempDirectory("nanu-corrupt-test"); Path file = directory.resolve("ledger.json");
        try {
            Files.write(file, "truncated{".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (FileLedgerStorage storage = new FileLedgerStorage(file)) {
                PaperLedger book = new PaperLedger(storage); assertFalse(book.healthy());
                assertEquals("truncated{", storage.read());
            }
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory.resolve("ledger.json.lock")); Files.deleteIfExists(directory); }
    }
}
