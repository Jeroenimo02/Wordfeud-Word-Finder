package app.wordfeudwordfinder.extension.words;

/**
 * Word list per ruleset id (spec section 6.1). Every URL here is one of the only hosts, besides
 * api.wordfeud.com, that the patch may contact. Each entry pins an exact file version (commit URL), its SHA-256
 * and a list version; raising the version makes clients re-download.
 *
 * <p>Only client-approved sources are listed (plan task 9); every other ruleset reports WL-NONE.
 */
public final class WordListSources {

    /** One downloadable word list. */
    public static final class Source {
        /** https URL of a fixed file version. */
        public final String url;
        /** Expected SHA-256 of the raw downloaded bytes, lowercase hex. */
        public final String sha256;
        /** List version; a stored file with a lower version is replaced. */
        public final int version;
        /** Charset name of the text file, e.g. "UTF-8". */
        public final String charset;

        public Source(String url, String sha256, int version, String charset) {
            this.url = url;
            this.sha256 = sha256;
            this.version = version;
            this.charset = charset;
        }
    }

    private WordListSources() {}

    /** The source for a ruleset id, or null when there is none (WL-NONE). */
    public static Source get(int rulesetId) {
        switch (rulesetId) {
            case 2: // Dutch: jensanjo/wordfeud-wordlists (OpenTaal), approved 2026-11-01
                return new Source(
                        "https://raw.githubusercontent.com/jensanjo/wordfeud-wordlists/"
                                + "9e8bbff63d7ebd80e2d91b9a33bd69d5d9c2bd06/nl/wordlist-nl.txt",
                        "414075805ca9b76fba3cbdaab7dd5ff7cbf1f851c3580b06f1001e2fca97b287", 1, "UTF-8");
            default:
                return null;
        }
    }
}
