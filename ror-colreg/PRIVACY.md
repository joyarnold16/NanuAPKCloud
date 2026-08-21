# Privacy Policy — Nanu ROR Visual Deck

_Last updated: 2026-08-02_

Nanu ROR Visual Deck ("the app") is a mostly-offline training reference for the
International Regulations for Preventing Collisions at Sea (COLREG). This
policy explains what the app does — and does not do — with your data.

## Summary

**The app collects no personal data itself, and has no account, login,
analytics, or advertising SDKs.** Everything you enter (exam history,
flashcard ratings, favourites, simulator statistics, and the name you type
for a completion certificate) is written only to local storage on your own
device and is never transmitted anywhere. The one exception is the optional
in-app purchase, which is processed entirely by Google Play Billing — see
"In-app purchases" below.

## What the app stores, and where

All of the following stays in the device's local browser storage
(`localStorage`), inside the app's own private storage sandbox. It is never
sent to a server, because the app has no server or backend:

- Exam attempt history and scores
- Flashcard confidence ratings
- Favourited rules, lights, and signals
- Encounter-simulator statistics (encounters, clear passages, collisions)
- The name typed into the completion-certificate field
- A "reduced motion" display preference

You can delete all of this at any time from the app's Progress screen
("Reset progress"), or by clearing the app's storage/data in your device's
system settings. You can also export this data as a JSON file for backup and
re-import it later — that file only ever goes where you choose to save it.

## In-app purchases

A single one-time purchase unlocks the Encounter Lab bridge simulator, Oral
Practice, and Exam Mode. That purchase is handled entirely by **Google Play
Billing** — the app itself never sees your payment details (card number,
billing address, etc.); Google processes the transaction and only tells the
app whether it succeeded. Whether you're unlocked is then cached locally on
your device so the app can check it instantly, without a network round trip,
every time it starts. As the developer we only see standard Play Console
transaction records (order ID, amount, date) that Google provides to every
app for accounting purposes — the same as any other Play Store purchase.
This is governed by Google's own Play Billing / Google Play privacy terms,
not by this app.

## Network access

The app requests the `INTERNET` permission for two purposes: completing the
in-app purchase above via Google Play Billing, and the "About" screen's
links out to official public reference pages (IMO, IALA, USCG), which only
load if you deliberately tap one, opening it in your device's browser.
Outside of an active purchase, the app makes no background network requests
and does not fetch remote content on startup — all training content (rules,
lights, signals, buoyage, question bank) is bundled inside the app and
works fully offline.

## Third parties

The only third-party service the app integrates with is Google Play Billing,
used solely to process the optional in-app purchase described above. The app
does not integrate any analytics, advertising, or tracking SDKs, and does
not share your study data, favourites, or progress with anyone.

## Children's privacy

The app does not knowingly collect personal information from anyone,
including children, because it does not collect personal information from
anyone.

## Changes to this policy

If this policy changes, the date at the top of this file will be updated.
Material changes will also be reflected in the app's release notes.

## Contact

Questions about this policy can be sent to: **[replace with your contact
email before publishing]**
