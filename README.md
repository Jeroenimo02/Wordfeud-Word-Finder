# Wordfeud Word Finder

Patches for Wordfeud, compatible with Morphe.

[![Add to Morphe](.github/assets/add-to-morphe.svg)](https://morphe.software/add-source?github=jeroenimo02/wordfeud-word-finder)

Add the patch source in Morphe with the badge above, or open
`https://morphe.software/add-source?github=jeroenimo02/wordfeud-word-finder`.

## What it does

- Adds a floating button (FAB) on the game screen.
- The button opens an overlay with your rack and a preview board.
- Lists all legal moves, sortable by score, length or A-Z.
- The Play button sends the chosen move through the Wordfeud API.

Supported app: `com.hbwares.wordfeud.free` **5.7.1**. Other versions are experimental.

## Patches

<!-- PATCHES_START -->
> **[v1.0.0](https://github.com/Jeroenimo02/Wordfeud-Morphe-Patch/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 Wordfeud&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.hbwares.wordfeud.free`

**🎯 Supported versions:**

| 5.7.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Word finder overlay](#word-finder-overlay) | Adds a floating button on the game screen that opens a list of all possible moves. |  |

</details>

<!-- PATCHES_END -->

## Word lists

The word finder needs a word list per ruleset. The lists live in this repository (`wordlists/`, built from open
sources by `tools/wordlists/build.py`). A list is downloaded in the background from this repository, pinned to the
commit the patch was built from, as soon as a game in that ruleset is seen (opening the overlay retries a failed
download), and verified against a SHA-256 before use.

| Ruleset id | Language | Source | License of the list |
|---|---|---|---|
| 0, 5 | English (US / international) | ENABLE word list, via [dolph/dictionary](https://github.com/dolph/dictionary). A free approximation of TWL and Collins: the server may reject words that are not in Wordfeud's own list | Public domain |
| 1 | Norwegian (bokmål) | Norsk ordbank (Språkbanken / Språkrådet), via [Ondkloss/norwegian-wordlist](https://github.com/Ondkloss/norwegian-wordlist) | CC BY 4.0 |
| 2 | Dutch | [OpenTaal](https://www.opentaal.org/), via [jensanjo/wordfeud-wordlists](https://github.com/jensanjo/wordfeud-wordlists) | Revised BSD and/or CC BY 3.0 |
| 4, 8 | Swedish (and Swedish strict) | Den stora svenska ordlistan (Göran Andersson), via [mrcz/Wordfeud-Player](https://github.com/mrcz/Wordfeud-Player) | CC BY-SA 3.0 |
| 9 | German | [enz/german-wordlist](https://github.com/enz/german-wordlist) | CC0 1.0 |
| 10 | Norwegian (nynorsk) | Norsk ordbank (Språkbanken / Språkrådet), via [Ondkloss/norwegian-wordlist](https://github.com/Ondkloss/norwegian-wordlist) | CC BY 4.0 |

Rulesets 3 (Danish), 6 (Spanish), 7 (French), 11 (Finnish) and 12 (Portuguese) have no open word list yet; the overlay
reports `WL-NONE` for them. The derived lists keep the license of their source; see `wordlists/README.md`.

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
- Verified on a device with Wordfeud 5.7.1 and the Dutch word list: moves, blanks, cross words, the seven-tile bonus, refresh after a move, light and dark themes. Other word lists and rulesets are not available yet.
- The exact form of a word rejection by the server (V6) and the button position with an ad banner (V7) are not verified yet.

## Translations

Non-English UI texts were translated by the author. Improvements are welcome.

## Trademark

Wordfeud is a trademark of Bertheussen IT. This project is not affiliated with or endorsed by Bertheussen IT.

## License

GPLv3, see [LICENSE](LICENSE) and [NOTICE](NOTICE). See [docs/DEV.md](docs/DEV.md) for development notes and
[the design spec](docs/superpowers/specs/2026-11-01-wordfeud-word-finder-design.md).
