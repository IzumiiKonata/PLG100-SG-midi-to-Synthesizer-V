package tech.konata.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Loads and parses the {@code SG_TABLE.csv} resource, building an immutable list
 * of {@link SgData} records.
 *
 * <h2>CSV format</h2>
 * Row 0 is a header line and is skipped. Each subsequent non-blank row has at
 * least 9 columns and up to 20 columns (the table is padded with empty strings
 * if columns are missing). Single-digit hex phoneme codes (columns 7–19) are
 * zero-padded to two digits.
 */
public final class SgTableLoader {

    /** Total number of CSV columns expected per row. */
    private static final int EXPECTED_COLUMN_COUNT = 20;

    /** First column index that may contain a phoneme hex code. */
    private static final int PHONEME_HEX_START_COL = 7;

    private SgTableLoader() { /* static utility class */ }

    /**
     * Loads {@code /SG_TABLE.csv} from the classpath and returns an unmodifiable
     * list of parsed {@link SgData} entries.
     *
     * @return non-null, unmodifiable list of SG syllable records
     * @throws RuntimeException if the resource cannot be read or parsed
     */
    public static List<SgData> load() {
        InputStream is = SgTableLoader.class.getResourceAsStream("/SG_TABLE.csv");
        if (is == null) {
            throw new IllegalStateException("SG_TABLE.csv not found on classpath");
        }

        try {
            String   raw  = new String(is.readAllBytes());
            String[] rows = raw.split("\n");

            List<SgData> table = new ArrayList<>(rows.length);

            // Skip header row (index 0)
            for (int i = 1; i < rows.length; i++) {
                String row = rows[i].trim();
                if (row.isEmpty()) continue;

                String[] cols = row.split(",");
                cols = padAndNormaliseHex(cols);
                table.add(toSgData(cols));
            }

            System.out.println("SG_TABLE loaded: " + table.size() + " entries");
            return Collections.unmodifiableList(table);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SG_TABLE.csv", e);
        }
    }

    /**
     * Zero-pads single-digit hex values in the phoneme/time columns, and ensures
     * the array has exactly {@value #EXPECTED_COLUMN_COUNT} elements (padding with
     * empty strings as needed).
     */
    private static String[] padAndNormaliseHex(String[] cols) {
        // Zero-pad single-character hex strings in columns 7+
        for (int j = PHONEME_HEX_START_COL; j < cols.length; j++) {
            if (cols[j].length() == 1) {
                cols[j] = "0" + cols[j];
            }
        }
        // Pad to the expected width
        if (cols.length < EXPECTED_COLUMN_COUNT) {
            cols = Arrays.copyOf(cols, EXPECTED_COLUMN_COUNT);
            for (int j = 0; j < EXPECTED_COLUMN_COUNT; j++) {
                if (cols[j] == null) cols[j] = "";
            }
        }
        return cols;
    }

    private static SgData toSgData(String[] c) {
        return new SgData(
                c[0],  c[1],  c[2],  c[3],  c[4],
                c[5],  c[6],  c[7],  c[8],
                c[9],  c[10],
                c[11], c[12],
                c[13], c[14],
                c[15], c[16],
                c[17], c[18],
                c[19]
        );
    }
}
