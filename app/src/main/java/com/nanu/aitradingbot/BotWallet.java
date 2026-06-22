package com.nanu.aitradingbot;

import wallet.core.jni.CoinType;
import wallet.core.jni.HDWallet;

/**
 * Wallet identity management is delegated to Trust Wallet Core. The mnemonic is encrypted by
 * SecurePrefs before storage; it is never logged, sent to market APIs, or placed in a URL.
 */
public final class BotWallet {
    static {
        System.loadLibrary("TrustWalletCore");
    }

    public static final class Addresses {
        public final String mnemonic;
        public final String bsc;
        public final String solana;

        Addresses(String mnemonic, String bsc, String solana) {
            this.mnemonic = mnemonic;
            this.bsc = bsc;
            this.solana = solana;
        }
    }

    private BotWallet() {}

    public static Addresses create() {
        HDWallet wallet = new HDWallet(128, "");
        return new Addresses(
                wallet.mnemonic(),
                wallet.getAddressForCoin(CoinType.SMARTCHAIN),
                wallet.getAddressForCoin(CoinType.SOLANA)
        );
    }

    public static Addresses restore(String mnemonic) {
        if (mnemonic == null || mnemonic.trim().isEmpty()) throw new IllegalArgumentException("Wallet backup is missing.");
        HDWallet wallet = new HDWallet(mnemonic.trim(), "");
        return new Addresses(
                wallet.mnemonic(),
                wallet.getAddressForCoin(CoinType.SMARTCHAIN),
                wallet.getAddressForCoin(CoinType.SOLANA)
        );
    }
}
