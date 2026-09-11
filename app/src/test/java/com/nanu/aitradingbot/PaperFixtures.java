package com.nanu.aitradingbot;

final class PaperFixtures {
    static final class Memory implements PaperLedger.Storage {
        String value; boolean fail;
        public String read() { return value; }
        public void write(String next) throws Exception { if (fail) throw new Exception("disk full"); value = next; }
    }
    static DexCandidate good(long now) {
        DexCandidate c = new DexCandidate(); c.chain = "bsc";
        c.tokenAddress = "0x1111111111111111111111111111111111111111";
        c.pairAddress = "0x2222222222222222222222222222222222222222";
        c.symbol = "TEST"; c.priceUsd = 1.099; c.liquidityUsd = 80000; c.volume24hUsd = 60000;
        c.observedAtMs = now; c.pairCreatedAtMs = now - 48 * 3_600_000L;
        c.buys24h = 120; c.sells24h = 90; c.change1h = 2.5; c.change24h = 9;
        c.security.status = "PASS"; c.security.checkedAtMs = now;
        c.security.buyTax = c.security.sellTax = 0;
        long end = now / 300_000 * 300_000;
        for (int i = 0; i < 100; i++) {
            double price = 1 + i * .001;
            c.candles.add(new Ohlcv(end - (100 - i) * 300_000L, price - .0005, price + .001, price - .001, price, 1000));
        }
        c.decision = "QUALIFIED"; return c;
    }
}
