# Nanu portable DEX paper engine

This is the first NAS foundation, sharing the Android DEX paper ledger, indicators, safety policy and read-only clients. It has **no wallet or signing dependency**, opens no network control port, and cannot submit real-money trades. It is separate from the legacy Binance server in `server/`.

Build with Java 17 and Gradle 8.10.2:

```sh
gradle -p nas test installDist
nas/build/install/nanu-paper-engine/bin/nanu-paper-engine /private/data/paper-ledger.json --once
nas/build/install/nanu-paper-engine/bin/nanu-paper-engine /private/data/paper-ledger.json
```

The generated distribution contains its JSON dependency. Windows users run the equivalent `.bat` launcher. The source targets Java 11; runtime support on the Synology DS116's ARM hardware has **not** been verified. Do not assume this host can run a current JVM or Docker.

The console starts with entries paused. Commands are `status`, `start`, `pause`, `panic`, `clear`, and `exit`. Open positions are monitored separately every 15 seconds even while entry discovery is paused. `panic` persists an exit request; `clear` refuses while a position remains open. EOF ends the console, so this is not yet a headless system service. `--once` validates/restores storage and prints status without fetching prices or entering trades.

Keep the ledger on a private local filesystem supporting atomic rename; run one process only. The `.lock` file is a process lock, not a backup. A truncated ledger is preserved and blocks startup. Recover an offline backup only after reconciling its positions and history; do not erase the file to dismiss a halt. Protect the data directory with operating-system permissions. The ledger contains simulated account data, never wallet secrets.

Before a production 24/7 service: verify the actual NAS runtime, add authenticated control/status transport and durable rejection/event logs, supervisor/shutdown health checks, reliable provider timestamps, independent pool/LP validation, routing simulation and an explicit single-owner migration protocol. Android must then become a remote dashboard. Mainnet execution must remain blocked throughout that work.

See [milestone audit](../docs/PAPER_HARDENING_M1.md) for risk semantics, cost assumptions, validation and unresolved limitations.
