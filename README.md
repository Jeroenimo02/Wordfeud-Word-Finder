# Wordfeud Word Finder

Patches for Wordfeud, compatible with Morphe.

[![Add to Morphe](.github/assets/add-to-morphe.svg)](https://morphe.software/add-source?github=jeroenimo02/morphe-patches)

Add the patch source in Morphe with the badge above, or open
`https://morphe.software/add-source?github=jeroenimo02/morphe-patches`.

## What it does

- Adds a floating button (FAB) on the game screen.
- The button opens an overlay with your rack and a preview board.
- Lists all legal moves, sortable by score, length or A-Z.
- The Play button sends the chosen move through the Wordfeud API.

Supported app: `com.hbwares.wordfeud.free` **5.7.1**. Other versions are experimental.

## Patches

<!-- PATCHES_START -->
<!-- PATCHES_END -->

## Word lists

The word finder needs a word list per ruleset. A list is downloaded in the background from a pinned commit as soon as
a game in that ruleset is seen (opening the overlay retries a failed download), and verified against a SHA-256
before use.

| Ruleset id | Language | Source (pinned commit) | License |
|---|---|---|---|
| 2 | Dutch | [jensanjo/wordfeud-wordlists](https://github.com/jensanjo/wordfeud-wordlists) `nl/wordlist-nl.txt` @ `9e8bbff63d7ebd80e2d91b9a33bd69d5d9c2bd06` | Revised BSD and/or CC BY 3.0; word list by [OpenTaal](https://www.opentaal.org/) (attribution required) |

All other rulesets have no word list yet; the overlay reports `WL-NONE` for them. See `docs/wordlists.md` for the research.

## Error codes

Every message shows a short text and a code.

| Code | Meaning |
|---|---|
| `WL-NET` | Word list: no connection or timeout |
| `WL-HTTP-<status>` | Word list: HTTP error |
| `WL-HASH` | Word list: checksum does not match |
| `WL-DISK` | Word list: saving failed |
| `WL-NONE` | No word list available for this ruleset |
| `GS-EVENT-<type>` | Unknown event type in the game |
| `GS-DATA` | Game data missing or unreadable |
| `MV-SESSION` | No valid session found |
| `MV-NET` | Move: connection failed, outcome unknown |
| `MV-SERVER-<type>` | Move: the server reported an error |
| `MV-SERVER-http_<status>` | Move: non-2xx reply that could not be parsed |

## Risks

- Using this patch may conflict with Wordfeud's terms of service. Possible consequences include a warning or a suspended account. You use it at your own risk.
- Moves are sent outside the app's own UI, straight to the API, using your Wordfeud session cookie. The cookie never leaves the device except to `api.wordfeud.com`.
- Network access is limited to `api.wordfeud.com` and the pinned word-list URLs. There is no analytics or telemetry.

## Status and limitations

- Requires Android 7.0 (API 24) or newer.
- Not yet verified on a device (checks V1-V12 are pending).
- Blank tiles are disabled until V2 is verified.
- After a move, the game refreshes through Wordfeud's own polling (about 20 s).

## Translations

Non-English UI texts were translated by the author. Improvements are welcome.

## Trademark

Wordfeud is a trademark of Bertheussen IT. This project is not affiliated with or endorsed by Bertheussen IT.

## License

GPLv3, see [LICENSE](LICENSE) and [NOTICE](NOTICE). See [docs/DEV.md](docs/DEV.md) for development notes and
[the design spec](docs/superpowers/specs/2026-11-01-wordfeud-word-finder-design.md).
