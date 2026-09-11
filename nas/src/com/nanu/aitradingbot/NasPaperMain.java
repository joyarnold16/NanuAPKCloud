package com.nanu.aitradingbot;

import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Local-console NAS groundwork. No network control port and no wallet/signing dependency. */
public final class NasPaperMain {
    private static boolean entries;
    private static long generation;
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !"--once".equals(args[1])))
            throw new IllegalArgumentException("Usage: NasPaperMain <state-file> [--once]");
        PaperExecution.requirePaper("paper");
        try (FileLedgerStorage storage = new FileLedgerStorage(Paths.get(args[0]))) {
            PaperLedger ledger = new PaperLedger(storage);
            if (!ledger.healthy()) throw new IllegalStateException("Corrupt/unwritable ledger; preserve file and recover offline");
            if (args.length == 2) { System.out.println(ledger.snapshot()); return; }
            DexDataClient data = new DexDataClient(); PaperLedger.Limits limits = new PaperLedger.Limits();
            ScheduledExecutorService workers = Executors.newScheduledThreadPool(2);
            workers.scheduleWithFixedDelay(() -> {
                try {
                    JSONObject p = ledger.position(); if (p == null) return;
                    DexCandidate c = data.pair(p.getString("chain"), p.getString("pairAddress"), p.getString("tokenAddress"));
                    data.security(c); System.out.println(ledger.mark(c, limits, System.currentTimeMillis()));
                } catch (Exception e) { System.err.println("Position attention required: " + e.getMessage()); }
            }, 0, 15, TimeUnit.SECONDS);
            workers.scheduleWithFixedDelay(() -> {
                long request;
                synchronized (NasPaperMain.class) { if (!entries || ledger.position() != null) return; request = generation; }
                try {
                    for (DexCandidate c : data.discover(25_000, 10_000, 24)) {
                        synchronized (NasPaperMain.class) {
                            if (!entries || request != generation) return;
                            System.out.println(ledger.open(c, limits, System.currentTimeMillis(), "paper"));
                            if (ledger.position() != null) return;
                        }
                    }
                } catch (Exception e) { System.err.println("Entry refused: " + e.getMessage()); }
            }, 0, 60, TimeUnit.SECONDS);
            System.out.println("PAPER ONLY. Commands: status, start, pause, panic, clear, exit. Entries start paused.");
            try (Scanner console = new Scanner(System.in)) {
                while (console.hasNextLine()) {
                    String command = console.nextLine().trim();
                    synchronized (NasPaperMain.class) {
                        generation++;
                        if ("exit".equals(command)) break;
                        try {
                            if ("start".equals(command)) { ledger.advanceClock(System.currentTimeMillis()); entries = !ledger.halted(); }
                            else if ("pause".equals(command)) entries = false;
                            else if ("panic".equals(command)) { entries = false; ledger.panic(); }
                            else if ("clear".equals(command)) { entries = false; ledger.clearPanic(); }
                            else if (!"status".equals(command)) System.out.println("Unknown command");
                            System.out.println(ledger.snapshot());
                        } catch (Exception e) { System.err.println(e.getMessage()); }
                    }
                }
            } finally {
                synchronized (NasPaperMain.class) { entries = false; generation++; }
                workers.shutdownNow();
                if (!workers.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Workers did not stop; retain ledger lock");
            }
        }
    }
}
