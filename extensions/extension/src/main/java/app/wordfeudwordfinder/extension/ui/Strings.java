package app.wordfeudwordfinder.extension.ui;

import app.wordfeudwordfinder.extension.core.WfError;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * UI texts (spec section 9). English is the fallback language.
 *
 * NOTE: the language list (en, nl, da, sv, nb, es, fr, de) is an assumption (controller ruling R7);
 * it must be verified against the APK's config.* splits / flutter_assets (V12 / Task 16).
 * Non-English texts are translated by the author; improvements are welcome.
 *
 * Placeholders (use String.format on the result):
 *  USES "%s" = the tiles the word uses from the rack; ENDS_ON "%s" = bonus-square label;
 *  PTS "%d" = score. All other keys have no placeholders.
 */
public final class Strings {
    private Strings() {}

    public enum Key {
        TITLE, YOUR_TILES, SORT_SCORE, SORT_LENGTH, SORT_ALPHA, PLAY,
        NOT_YOUR_TURN, DIR_DOWN, DIR_ACROSS, USES, ENDS_ON,
        REJECTED, RETRY, COPY_DETAILS, DOWNLOADING, PTS,
        ERR_WL_NET, ERR_WL_HTTP, ERR_WL_HASH, ERR_WL_DISK, ERR_WL_NONE,
        ERR_GS_EVENT, ERR_GS_DATA, ERR_MV_SESSION, ERR_MV_NET, ERR_MV_SERVER
    }

    private static final Map<String, Map<Key, String>> TEXTS = new HashMap<>();

    /** Supported language codes. */
    public static java.util.Set<String> languages() {
        return java.util.Collections.unmodifiableSet(TEXTS.keySet());
    }

    public static String get(String lang, Key key) {
        Map<Key, String> m = TEXTS.get(normalize(lang));
        String s = m == null ? null : m.get(key);
        if (s == null || s.isEmpty()) s = TEXTS.get("en").get(key);
        return s;
    }

    static String normalize(String lang) {
        if (lang == null) return "en";
        String l = lang.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int i = l.indexOf('-');
        if (i > 0) l = l.substring(0, i);
        if (l.equals("no") || l.equals("nn")) l = "nb";
        return TEXTS.containsKey(l) ? l : "en";
    }

    public static Key errorKey(WfError e) {
        String c = e.code();
        if (c.startsWith("WL-HTTP")) return Key.ERR_WL_HTTP;
        if (c.startsWith("WL-NET")) return Key.ERR_WL_NET;
        if (c.startsWith("WL-HASH")) return Key.ERR_WL_HASH;
        if (c.startsWith("WL-DISK")) return Key.ERR_WL_DISK;
        if (c.startsWith("WL-NONE")) return Key.ERR_WL_NONE;
        if (c.startsWith("GS-EVENT")) return Key.ERR_GS_EVENT;
        if (c.startsWith("MV-SESSION")) return Key.ERR_MV_SESSION;
        if (c.startsWith("MV-NET")) return Key.ERR_MV_NET;
        if (c.startsWith("MV-SERVER")) return Key.ERR_MV_SERVER;
        return Key.ERR_GS_DATA;
    }

    private static void add(String lang, String... v) {
        Key[] keys = Key.values();
        if (v.length != keys.length) throw new IllegalStateException("texts for " + lang);
        Map<Key, String> m = new EnumMap<>(Key.class);
        for (int i = 0; i < keys.length; i++) m.put(keys[i], v[i]);
        TEXTS.put(lang, m);
    }

    static {
        // Order: see Key enum.
        add("en", "Word finder", "Your tiles", "Score", "Length", "A-Z", "Play",
            "Not your turn", "Down", "Across", "Uses: %s", "Ends on %s",
            "Move rejected", "Retry", "Copy details", "Downloading word list...", "%d pts",
            "No connection to download the word list.",
            "Word list download failed.",
            "Word list is corrupt. Please retry.",
            "Could not save the word list.",
            "No word list available for this game's rules.",
            "This game contains something unexpected.",
            "The game data is missing or unreadable.",
            "No valid session found. Try reopening Wordfeud.",
            "Connection failed. The move may or may not have been played.",
            "The server reported an error.");
        add("nl", "Woordzoeker", "Jouw letters", "Score", "Lengte", "A-Z", "Speel",
            "Je bent niet aan zet", "Verticaal", "Horizontaal", "Gebruikt: %s", "Eindigt op %s",
            "Zet afgewezen", "Opnieuw", "Kopieer details", "Woordenlijst downloaden...", "%d pnt",
            "Geen verbinding om de woordenlijst te downloaden.",
            "Downloaden van de woordenlijst mislukt.",
            "Woordenlijst is beschadigd. Probeer opnieuw.",
            "Woordenlijst kon niet worden opgeslagen.",
            "Geen woordenlijst beschikbaar voor de regels van dit potje.",
            "Dit potje bevat iets onverwachts.",
            "Gegevens van het potje ontbreken of zijn onleesbaar.",
            "Geen geldige sessie gevonden. Open Wordfeud opnieuw.",
            "Verbinding mislukt. De zet is mogelijk wel gespeeld.",
            "De server meldt een fout.");
        add("da", "Ordfinder", "Dine brikker", "Point", "Laengde", "A-Z", "Spil",
            "Det er ikke din tur", "Ned", "Vandret", "Bruger: %s", "Slutter paa %s",
            "Traek afvist", "Proev igen", "Kopier detaljer", "Henter ordliste...", "%d point",
            "Ingen forbindelse til at hente ordlisten.",
            "Hentning af ordlisten mislykkedes.",
            "Ordlisten er beskadiget. Proev igen.",
            "Ordlisten kunne ikke gemmes.",
            "Ingen ordliste til dette spils regler.",
            "Spillet indeholder noget uventet.",
            "Spildata mangler eller kan ikke laeses.",
            "Ingen gyldig session fundet. Proev at aabne Wordfeud igen.",
            "Forbindelsen mislykkedes. Traekket er muligvis spillet.",
            "Serveren meldte en fejl.");
        add("sv", "Ordsokare", "Dina brickor", "Poang", "Langd", "A-Z", "Spela",
            "Det ar inte din tur", "Ned", "Vagrat", "Anvander: %s", "Slutar pa %s",
            "Draget nekades", "Forsok igen", "Kopiera detaljer", "Laddar ner ordlista...", "%d p",
            "Ingen anslutning for att ladda ner ordlistan.",
            "Nedladdningen av ordlistan misslyckades.",
            "Ordlistan ar skadad. Forsok igen.",
            "Ordlistan kunde inte sparas.",
            "Ingen ordlista for det har spelets regler.",
            "Spelet innehaller nagot ovantat.",
            "Speldata saknas eller kan inte lasas.",
            "Ingen giltig session hittades. Prova att oppna Wordfeud igen.",
            "Anslutningen misslyckades. Draget kan ha spelats.",
            "Servern rapporterade ett fel.");
        add("nb", "Ordsoker", "Brikkene dine", "Poeng", "Lengde", "A-Z", "Spill",
            "Det er ikke din tur", "Ned", "Vannrett", "Bruker: %s", "Slutter paa %s",
            "Trekket ble avvist", "Proev igjen", "Kopier detaljer", "Laster ned ordliste...", "%d p",
            "Ingen forbindelse for aa laste ned ordlisten.",
            "Nedlasting av ordlisten mislyktes.",
            "Ordlisten er skadet. Proev igjen.",
            "Ordlisten kunne ikke lagres.",
            "Ingen ordliste for reglene i dette spillet.",
            "Spillet inneholder noe uventet.",
            "Spilldata mangler eller er uleselige.",
            "Fant ingen gyldig sesjon. Proev aa aapne Wordfeud paa nytt.",
            "Forbindelsen mislyktes. Trekket kan ha blitt spilt.",
            "Serveren meldte en feil.");
        add("es", "Buscador de palabras", "Tus fichas", "Puntos", "Longitud", "A-Z", "Jugar",
            "No es tu turno", "Vertical", "Horizontal", "Usa: %s", "Termina en %s",
            "Jugada rechazada", "Reintentar", "Copiar detalles", "Descargando lista de palabras...", "%d pts",
            "Sin conexion para descargar la lista de palabras.",
            "Error al descargar la lista de palabras.",
            "La lista de palabras esta danada. Vuelve a intentarlo.",
            "No se pudo guardar la lista de palabras.",
            "No hay lista de palabras para las reglas de esta partida.",
            "La partida contiene algo inesperado.",
            "Faltan datos de la partida o no se pueden leer.",
            "No se encontro una sesion valida. Prueba a reabrir Wordfeud.",
            "Fallo de conexion. Puede que la jugada se haya realizado.",
            "El servidor notifico un error.");
        add("fr", "Chercheur de mots", "Vos lettres", "Score", "Longueur", "A-Z", "Jouer",
            "Ce n'est pas votre tour", "Vertical", "Horizontal", "Utilise : %s", "Finit sur %s",
            "Coup refuse", "Reessayer", "Copier les details", "Telechargement de la liste de mots...", "%d pts",
            "Pas de connexion pour telecharger la liste de mots.",
            "Echec du telechargement de la liste de mots.",
            "La liste de mots est corrompue. Reessayez.",
            "Impossible d'enregistrer la liste de mots.",
            "Aucune liste de mots pour les regles de cette partie.",
            "La partie contient un element inattendu.",
            "Les donnees de la partie sont absentes ou illisibles.",
            "Aucune session valide. Essayez de rouvrir Wordfeud.",
            "Echec de la connexion. Le coup a peut-etre ete joue.",
            "Le serveur a signale une erreur.");
        add("de", "Wortfinder", "Deine Buchstaben", "Punkte", "Laenge", "A-Z", "Spielen",
            "Du bist nicht am Zug", "Senkrecht", "Waagerecht", "Nutzt: %s", "Endet auf %s",
            "Zug abgelehnt", "Erneut versuchen", "Details kopieren", "Wortliste wird geladen...", "%d Pkt.",
            "Keine Verbindung zum Laden der Wortliste.",
            "Laden der Wortliste fehlgeschlagen.",
            "Die Wortliste ist beschaedigt. Bitte erneut versuchen.",
            "Die Wortliste konnte nicht gespeichert werden.",
            "Keine Wortliste fuer die Regeln dieses Spiels.",
            "Das Spiel enthaelt etwas Unerwartetes.",
            "Spieldaten fehlen oder sind nicht lesbar.",
            "Keine gueltige Sitzung gefunden. Oeffne Wordfeud erneut.",
            "Verbindung fehlgeschlagen. Der Zug wurde eventuell gespielt.",
            "Der Server hat einen Fehler gemeldet.");
    }
}
