package tech.konata.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses PLG100-SG {@code PhoneSEQ} SysEx messages and resolves them to
 * {@link SgData} syllable records by searching the loaded SG table.
 *
 * <h2>PhoneSEQ SysEx structure</h2>
 * All bytes are shown in hex. The message must match this layout:
 * <pre>
 *   F0 43 1x 5D 03 00 [00] &lt;data bytes ...&gt; F7
 *                              ↑
 *                              offset 17 in the space-separated hex string
 *                              (fixed value "00")
 * </pre>
 * Relevant header checks (on the space-delimited hex string):
 * <ul>
 *   <li>Starts with {@code "F0 43 1"}</li>
 *   <li>Characters 8–15: {@code " 5D 03 0"}</li>
 *   <li>Characters 17–19: {@code " 00"}</li>
 *   <li>Ends with {@code " F7"}</li>
 * </ul>
 *
 * The body is the substring after offset 21 (trimmed). Phoneme codes are
 * space-separated two-digit hex values. The special code {@code 7E} signals a
 * breath mark and is stripped before matching.
 *
 * <h2>Matching algorithm</h2>
 * Starting from the longest possible match and working down to length 1, the
 * parser searches for an {@link SgData} entry whose:
 * <ul>
 *   <li>{@code availablePhonemeCount} equals the number of filtered data bytes,
 *       and</li>
 *   <li>every non-wildcard field (value {@code "**"}) at positions 9, 10, 11,
 *       … (phoneme / time columns) matches the corresponding data byte.</li>
 * </ul>
 */
public final class PhoneSeqParser {

    // PhoneSEQ header constants
    private static final String HEADER_PREFIX      = "F0 43 1";
    private static final String HEADER_MID         = " 5D 03 0";
    private static final int    HEADER_MID_OFFSET  = 8;
    private static final String HEADER_ZERO        = " 00";
    private static final int    HEADER_ZERO_OFFSET = 17;
    private static final String BODY_START_OFFSET_STR = "";  // body starts at char 21
    private static final int    BODY_START           = 21;
    private static final String SYSEX_END           = " F7";
    private static final String BREATH_CODE         = "7E";

    /** First CSV column index that holds phoneme/time data (used in matching). */
    private static final int PHONEME_COLUMNS_START = 9;

    private final List<SgData> table;

    public PhoneSeqParser(List<SgData> table) {
        this.table = table;
    }

    /**
     * Attempts to parse a space-delimited hex SysEx string into an {@link SgData}
     * record.
     *
     * @param hexString space-delimited hex bytes, e.g.
     *                  {@code "F0 43 10 5D 03 00 00 0D 0A 01 00 F7"}
     * @return matching {@link SgData} with {@code hasBreathMark} set, or
     *         {@code null} if the message does not conform to the PhoneSEQ format
     *         or no table match is found
     */
    public SgData parse(String hexString) {
        if (!isValidPhoneSeqHeader(hexString)) return null;

        String body = extractBody(hexString);
        if (body.isEmpty()) return null;

        System.out.println("  - Content: " + body);
        String[] rawCodes = body.split(" ");

        // Strip breath-mark code (7E) and record its presence
        boolean      hasBreath    = false;
        List<String> filteredCodes = new ArrayList<>(rawCodes.length);
        for (String code : rawCodes) {
            if (BREATH_CODE.equals(code)) {
                hasBreath = true;
            } else {
                filteredCodes.add(code);
            }
        }

        SgData match = findBestMatch(filteredCodes);
        if (match != null) {
            match.hasBreathMark = hasBreath;
        }
        return match;
    }

    private static boolean isValidPhoneSeqHeader(String hex) {
        if (hex == null) return false;
        if (!hex.startsWith(HEADER_PREFIX)) return false;
        if (hex.length() <= HEADER_MID_OFFSET + HEADER_MID.length()) return false;
        if (!hex.startsWith(HEADER_MID, HEADER_MID_OFFSET)) return false;
        if (hex.length() <= HEADER_ZERO_OFFSET + HEADER_ZERO.length()) return false;
        if (!hex.startsWith(HEADER_ZERO, HEADER_ZERO_OFFSET)) return false;
        if (!hex.endsWith(SYSEX_END)) return false;
        return true;
    }

    private static String extractBody(String hex) {
        // Body runs from offset 21 up to (but not including) the " F7" suffix
        if (hex.length() <= BODY_START) return "";
        String body = hex.substring(BODY_START, hex.length() - SYSEX_END.length()).trim();
        return body;
    }

    private SgData findBestMatch(List<String> codes) {
        for (int length = codes.size(); length > 0; length--) {
            for (SgData entry : table) {
                if (entry.availablePhonemeCount != length) continue;
                if (rowMatchesCodes(entry, codes, length)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the first {@code length} codes in {@code codes}
     * match the corresponding phoneme/time fields in {@code entry}, ignoring
     * wildcard fields ({@code "**"}).
     */
    private static boolean rowMatchesCodes(SgData entry, List<String> codes, int length) {
        for (int i = 0; i < length; i++) {
            String field = entry.getField(PHONEME_COLUMNS_START + i);
            if ("**".equals(field)) continue;
            if (!field.equals(codes.get(i))) return false;
        }
        return true;
    }
}
