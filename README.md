# Wordfeud Word Finder

Patches for Wordfeud, compatible with Morphe. Adds a word finder overlay to Wordfeud.

See [docs/DEV.md](docs/DEV.md) for development notes and
[the design spec](docs/superpowers/specs/2026-11-01-wordfeud-word-finder-design.md).

Licensed under GPLv3, see [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Word lists

The word finder needs a word list per ruleset. Lists are downloaded on first use from a pinned commit and verified
against a SHA-256 before use.

| Ruleset id | Language | Source (pinned commit) | License |
|---|---|---|---|
| 2 | Dutch | [jensanjo/wordfeud-wordlists](https://github.com/jensanjo/wordfeud-wordlists) `nl/wordlist-nl.txt` @ `9e8bbff63d7ebd80e2d91b9a33bd69d5d9c2bd06` | Revised BSD and/or CC BY 3.0; word list by [OpenTaal](https://www.opentaal.org/) (attribution required) |

All other rulesets have no word list yet; the overlay reports `WL-NONE` for them. See `docs/wordlists.md` for the research.
