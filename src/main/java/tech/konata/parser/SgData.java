package tech.konata.parser;

/**
 * One row from {@code SG_TABLE.csv}, representing a single syllable of the
 * PLG100-SG formant-singing synthesiser.
 *
 * <h2>CSV columns (0-based index)</h2>
 * <pre>
 *  0  syllable_type          – e.g. "あ", "か", …
 *  1  lyrics_representation  – display character inserted into the project
 *  2  input_text             – romaji or text input form
 *  3–6 phoneme_representation1–4  – phoneme name strings
 *  7  phoneme_representation5
 *  8  SysEX_header           – the PhoneSEQ SysEx header byte
 *  9  ph1  – first phoneme code (hex)
 * 10  time1 – duration of ph1 (hex, or "00" for note-length, "**" = wildcard)
 * 11  ph2  – second phoneme code
 * 12  time2
 * 13  ph3
 * 14  time3
 * 15  ph4
 * 16  time4
 * 17  ph5
 * 18  time5
 * 19  eox  – end-of-SysEx marker ("F7")
 * </pre>
 *
 * <h2>Pronunciation modes</h2>
 * Determined from the time fields as per the PLG100-SG manual (§1 "発音モードについて"):
 * <ul>
 *   <li>{@code 1} Normal  – last phoneme time is "00"; note-off is ignored.</li>
 *   <li>{@code 2} Note-off – a non-last time is "00"; the phoneme after "00"
 *       plays on note-off.</li>
 *   <li>{@code 3} Fixed-time – no time field is "00"; each phoneme plays for
 *       its stated duration.</li>
 * </ul>
 *
 * <h2>Breath mark</h2>
 * Phoneme code {@code 7E} in any phoneme slot signals a breath mark; it is
 * stripped from the matching data before comparison.
 */
public final class SgData {

    public final String syllableType;
    public final String lyricsRepresentation;
    public final String inputText;
    public final String phonemeRepr1;
    public final String phonemeRepr2;
    public final String phonemeRepr3;
    public final String phonemeRepr4;
    public final String phonemeRepr5;
    public final String sysExHeader;

    // Phoneme slots (indices 9–18 in the CSV)
    public final String ph1;
    public final String ph2;
    public final String ph3;
    public final String ph4;
    public final String ph5;

    // All time fields are stored as "**" (wildcard / ignored during matching)
    // per the original implementation; the actual duration values in the CSV
    // are intentionally not used for matching.
    public final String time1;
    public final String time2;
    public final String time3;
    public final String time4;
    public final String time5;

    public final String eox;

    /** Number of non-empty phoneme slots. */
    public final int availablePhonemeCount;

    /** {@code true} if a breath mark (phoneme code {@code 7E}) is present. */
    public boolean hasBreathMark;

    /**
     * Pronunciation mode determined from the time fields:
     * 1 = Normal, 2 = Note-off, 3 = Fixed-time.
     */
    public final int pronunciationMode;

    public SgData(String syllableType, String lyricsRepresentation, String inputText,
                  String phonemeRepr1, String phonemeRepr2, String phonemeRepr3,
                  String phonemeRepr4, String phonemeRepr5, String sysExHeader,
                  String ph1, String time1,
                  String ph2, String time2,
                  String ph3, String time3,
                  String ph4, String time4,
                  String ph5, String time5,
                  String eox) {

        this.syllableType        = syllableType;
        this.lyricsRepresentation = lyricsRepresentation;
        this.inputText           = inputText;
        this.phonemeRepr1        = phonemeRepr1;
        this.phonemeRepr2        = phonemeRepr2;
        this.phonemeRepr3        = phonemeRepr3;
        this.phonemeRepr4        = phonemeRepr4;
        this.phonemeRepr5        = phonemeRepr5;
        this.sysExHeader         = sysExHeader;
        this.ph1  = ph1;
        this.ph2  = ph2;
        this.ph3  = ph3;
        this.ph4  = ph4;
        this.ph5  = ph5;
        // All time values are treated as wildcards during matching
        this.time1 = "**";
        this.time2 = "**";
        this.time3 = "**";
        this.time4 = "**";
        this.time5 = "**";
        this.eox  = eox;

        this.hasBreathMark        = containsBreathMark();
        this.availablePhonemeCount = countAvailablePhonemes(time1, time2, time3, time4, time5);
        this.pronunciationMode    = determinePronunciationMode(time1, time2, time3, time4, time5);
    }

    /**
     * Returns the raw CSV field at the given zero-based column index (0–19).
     */
    public String getField(int index) {
        return switch (index) {
            case 0 -> syllableType;
            case 1 -> lyricsRepresentation;
            case 2 -> inputText;
            case 3 -> phonemeRepr1;
            case 4 -> phonemeRepr2;
            case 5 -> phonemeRepr3;
            case 6 -> phonemeRepr4;
            case 7 -> phonemeRepr5;
            case 8 -> sysExHeader;
            case 9 -> ph1;
            case 10 -> time1;
            case 11 -> ph2;
            case 12 -> time2;
            case 13 -> ph3;
            case 14 -> time3;
            case 15 -> ph4;
            case 16 -> time4;
            case 17 -> ph5;
            case 18 -> time5;
            case 19 -> eox;
            default -> throw new IndexOutOfBoundsException("Invalid field index: " + index);
        };
    }

    /** Returns the number of non-empty phoneme slots (ph1 … ph5). */
    public int getValidPhonemeCount() {
        int count = 0;
        if (!ph1.isEmpty()) count++;
        if (!ph2.isEmpty()) count++;
        if (!ph3.isEmpty()) count++;
        if (!ph4.isEmpty()) count++;
        if (!ph5.isEmpty()) count++;
        return count;
    }

    /**
     * Returns {@code true} if any phoneme slot contains the breath-mark code
     * {@code 7E}.
     */
    private boolean containsBreathMark() {
        return "7E".equals(ph1) || "7E".equals(ph2)
            || "7E".equals(ph3) || "7E".equals(ph4)
            || "7E".equals(ph5);
    }

    /**
     * Counts the number of occupied phoneme slots using the <em>original</em>
     * (pre-wildcard) time values from the CSV.
     *
     * <p>A slot is occupied if the phoneme string is non-empty OR the time
     * string is non-empty and not a wildcard.
     */
    private static int countAvailablePhonemes(
            String t1, String t2, String t3, String t4, String t5) {
        // Only phoneme presence matters; time1–5 from the CSV (passed directly)
        // determine the count per the original logic.
        // Because time fields in this.timeN are always "**", we receive the raw
        // CSV values as parameters here so the count is correct.
        int count = 0;
        if (!t1.isEmpty() && !"**".equals(t1)) count++;
        if (!t2.isEmpty() && !"**".equals(t2)) count++;
        if (!t3.isEmpty() && !"**".equals(t3)) count++;
        if (!t4.isEmpty() && !"**".equals(t4)) count++;
        if (!t5.isEmpty() && !"**".equals(t5)) count++;
        return count;
    }

    /**
     * Determines the pronunciation mode from the time fields as specified in
     * §1 "発音モードについて" of the PLG100-SG manual.
     *
     * @param t1…t5 raw time values from the CSV (before wildcard substitution)
     */
    private static int determinePronunciationMode(
            String t1, String t2, String t3, String t4, String t5) {
        boolean anyZero = "00".equals(t1) || "00".equals(t2)
                       || "00".equals(t3) || "00".equals(t4)
                       || "00".equals(t5);
        // Fixed-time mode: no time field is "00"
        // Normal / Note-off mode: at least one time field is "00"
        return anyZero ? 1 : 3;
    }

    @Override
    public String toString() {
        return "SgData{lyric='" + lyricsRepresentation
                + "', phonemes=" + getValidPhonemeCount()
                + ", mode=" + pronunciationMode
                + ", breath=" + hasBreathMark + "}";
    }
}
