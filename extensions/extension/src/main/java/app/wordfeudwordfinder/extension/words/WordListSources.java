package app.wordfeudwordfinder.extension.words;

/**
 * Word list per ruleset id (spec section 6.1). Every URL here is one of the only hosts, besides
 * api.wordfeud.com, that the patch may contact. Each entry pins an exact file version (commit URL), its SHA-256
 * and a list version; raising the version makes clients re-download.
 *
 * <p>The table is empty until the sources are chosen and approved (plan task 9); until then every ruleset
 * reports WL-NONE.
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
            // Real entries follow in plan task 9, e.g.:
            // case 1: return new Source("https://raw.githubusercontent.com/<owner>/<repo>/<commit>/<file>",
            //         "<sha256>", 1, "UTF-8");
            default:
                return null;
        }
    }
}
