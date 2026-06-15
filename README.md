# Nanu AI Trading Bot v5.5 — Final Safety Polish

This package is Android source code for the Nanu AI Trading Bot APK.

## Added / fixed in v5.5

- Duplicate API Doctor dialog fixed: one manual press = one final result popup.
- API Doctor button now shows a running state and will not start a second check while one is active.
- API Doctor result is saved on the Security page as the last result, not repeatedly shown again.
- Withdrawal warning clarified: Binance /account can show account-level withdraw ability, which is not the same as API-key withdrawal permission.
- Manual safety confirmation added: user must confirm Binance API-key withdrawals are OFF before live auto-trading unlock.
- Final Live Safety Checklist added.
- Live unlock now requires: LIVE mode, API Doctor private OK, spot trading OK, withdrawal OFF confirmation, Telegram Doctor PASS, phone notification/sound readiness, Profit Guard ON, daily loss limit set, Panic button tested.
- Telegram Doctor PASS/FAIL status is saved.
- Telegram token or chat ID update resets Telegram Doctor status.
- Test Phone Alert / Long Sound button added.
- Panic button test is remembered as a safety checklist item.
- v5.4 Live Gate Fix preserved.
- v5.3 API Doctor + Trusted IP Helper preserved.
- v5.2 Profit Guard + Alerts preserved.
- v5.1 no-flicker UI preserved.

## Safety note

This is still a development build. It is designed to keep live auto-trading locked until the safety checklist is complete. It does not guarantee profit and it should be tested in Paper mode first.

## Build output

Release tag: `nanu-ai-trading-bot-v5-5`
APK name: `nanu-ai-trading-bot-v5-5-debug.apk`

## Recommended first test order

1. Install APK.
2. Open Security.
3. Run Telegram Doctor.
4. Press Test Phone Alert / Long Sound.
5. Select LIVE only for API Doctor.
6. Run API Doctor.
7. Confirm Binance API-key Enable Withdrawals is OFF, then press Confirm API Withdrawals OFF.
8. Enable Profit Guard.
9. Press Panic once in safe/paper state to test emergency stop.
10. Do not unlock live trading until every checklist line is green.
