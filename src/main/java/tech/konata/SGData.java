package tech.konata;

import lombok.AllArgsConstructor;

/**
 * @author IzumiiKonata
 * Date: 2025/4/11 17:45
 */
public class SGData {

    public final String syllable_type,lyrics_representation,input_text,phoneme_representation1,phoneme_representation2,phoneme_representation3,phoneme_representation4,phoneme_representation5,SysEX_header,ph1,time1,ph2,time2,ph3,time3,ph4,time4,ph5,time5,eox;

    public int availFieldCount;

    public SGData(String syllableType, String lyricsRepresentation, String inputText, String phonemeRepresentation1, String phonemeRepresentation2, String phonemeRepresentation3, String phonemeRepresentation4, String phonemeRepresentation5, String sysEXHeader, String ph1, String time1, String ph2, String time2, String ph3, String time3, String ph4, String time4, String ph5, String time5, String eox) {
        syllable_type = syllableType;
        lyrics_representation = lyricsRepresentation;
        input_text = inputText;
        phoneme_representation1 = phonemeRepresentation1;
        phoneme_representation2 = phonemeRepresentation2;
        phoneme_representation3 = phonemeRepresentation3;
        phoneme_representation4 = phonemeRepresentation4;
        phoneme_representation5 = phonemeRepresentation5;
        SysEX_header = sysEXHeader;
        this.ph1 = ph1;
        this.time1 = "**";
        this.ph2 = ph2;
        this.time2 = "**";
        this.ph3 = ph3;
        this.time3 = "**";
        this.ph4 = ph4;
        this.time4 = "**";
        this.ph5 = ph5;
        this.time5 = "**";
        this.eox = eox;

        if (!ph1.isEmpty())
            availFieldCount++;

        if (!time1.isEmpty())
            availFieldCount++;

        if (!ph2.isEmpty())
            availFieldCount++;

        if (!time2.isEmpty())
            availFieldCount++;

        if (!ph3.isEmpty())
            availFieldCount++;

        if (!time3.isEmpty())
            availFieldCount++;

        if (!ph4.isEmpty())
            availFieldCount++;

        if (!time4.isEmpty())
            availFieldCount++;

        if (!ph5.isEmpty())
            availFieldCount++;

        if (!time5.isEmpty())
            availFieldCount++;

    }

    public String getField(int index) {

        return switch (index) {
            case 0 -> syllable_type;
            case 1 -> lyrics_representation;
            case 2 -> input_text;
            case 3 -> phoneme_representation1;
            case 4 -> phoneme_representation2;
            case 5 -> phoneme_representation3;
            case 6 -> phoneme_representation4;
            case 7 -> phoneme_representation5;
            case 8 -> SysEX_header;
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
            default -> throw new RuntimeException("?");
        };

    }
}
