package com.nanu.godmode;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JournalDb extends SQLiteOpenHelper {
    public JournalDb(Context context) { super(context, "nanu_journal.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, kind TEXT, message TEXT)");
        db.execSQL("CREATE TABLE trades(id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT, side TEXT, entry REAL, qty REAL, opened_at INTEGER, status TEXT, exit_price REAL, closed_at INTEGER, pnl REAL, reason TEXT, peak REAL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS events");
        db.execSQL("DROP TABLE IF EXISTS trades");
        onCreate(db);
    }

    public synchronized void event(String kind, String message) {
        ContentValues v = new ContentValues();
        v.put("ts", System.currentTimeMillis());
        v.put("kind", kind);
        v.put("message", message);
        getWritableDatabase().insert("events", null, v);
    }

    public synchronized long openTrade(String symbol, String side, double entry, double qty, String reason) {
        ContentValues v = new ContentValues();
        v.put("symbol", symbol);
        v.put("side", side);
        v.put("entry", entry);
        v.put("qty", qty);
        v.put("opened_at", System.currentTimeMillis());
        v.put("status", "OPEN");
        v.put("reason", reason);
        v.put("peak", entry);
        long id = getWritableDatabase().insert("trades", null, v);
        event("TRADE", "Opened " + symbol + " at " + fmt(entry) + " qty " + fmt(qty));
        return id;
    }

    public synchronized void updatePeak(long id, double peak) {
        ContentValues v = new ContentValues();
        v.put("peak", peak);
        getWritableDatabase().update("trades", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void closeTrade(long id, double exitPrice, String reason) {
        TradeRow t = getTrade(id);
        if (t == null) return;
        double pnl = (exitPrice - t.entry) * t.qty;
        ContentValues v = new ContentValues();
        v.put("status", "CLOSED");
        v.put("exit_price", exitPrice);
        v.put("closed_at", System.currentTimeMillis());
        v.put("pnl", pnl);
        v.put("reason", reason);
        getWritableDatabase().update("trades", v, "id=?", new String[]{String.valueOf(id)});
        event("TRADE", "Closed " + t.symbol + " at " + fmt(exitPrice) + " PnL " + fmt(pnl) + " | " + reason);
    }

    public synchronized TradeRow getTrade(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT id,symbol,side,entry,qty,opened_at,status,peak FROM trades WHERE id=?", new String[]{String.valueOf(id)});
        try { return c.moveToFirst() ? readTrade(c) : null; } finally { c.close(); }
    }

    public synchronized List<TradeRow> openTrades() {
        ArrayList<TradeRow> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,symbol,side,entry,qty,opened_at,status,peak FROM trades WHERE status='OPEN' ORDER BY id DESC", null);
        try { while (c.moveToNext()) rows.add(readTrade(c)); } finally { c.close(); }
        return rows;
    }

    public synchronized int openCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM trades WHERE status='OPEN'", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public synchronized double dailyPnl() {
        long start = System.currentTimeMillis() - 24L*60L*60L*1000L;
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(pnl),0) FROM trades WHERE status='CLOSED' AND closed_at>=?", new String[]{String.valueOf(start)});
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    public synchronized String recentTradesText(int limit) {
        StringBuilder sb = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery("SELECT symbol,side,entry,qty,status,exit_price,pnl,reason,opened_at FROM trades ORDER BY id DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) {
                sb.append(time(c.getLong(8))).append("  ")
                  .append(c.getString(0)).append(" ").append(c.getString(1)).append(" ")
                  .append(c.getString(4)).append(" entry=").append(fmt(c.getDouble(2)))
                  .append(" qty=").append(fmt(c.getDouble(3)))
                  .append(" exit=").append(fmt(c.getDouble(5)))
                  .append(" pnl=").append(fmt(c.getDouble(6)))
                  .append("\n  ").append(c.getString(7)).append("\n\n");
            }
        } finally { c.close(); }
        return sb.length()==0 ? "No trades yet." : sb.toString();
    }

    public synchronized String recentEventsText(int limit) {
        StringBuilder sb = new StringBuilder();
        Cursor c = getReadableDatabase().rawQuery("SELECT ts,kind,message FROM events ORDER BY id DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) sb.append(time(c.getLong(0))).append("  [").append(c.getString(1)).append("] ").append(c.getString(2)).append("\n\n");
        } finally { c.close(); }
        return sb.length()==0 ? "No events yet." : sb.toString();
    }

    private TradeRow readTrade(Cursor c) {
        TradeRow t = new TradeRow();
        t.id = c.getLong(0); t.symbol = c.getString(1); t.side = c.getString(2); t.entry = c.getDouble(3); t.qty = c.getDouble(4); t.openedAt = c.getLong(5); t.status = c.getString(6); t.peak = c.getDouble(7);
        return t;
    }

    public static String fmt(double d) { return String.format(Locale.US, "%.5f", d); }
    public static String time(long ts) { return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(ts)); }
}
