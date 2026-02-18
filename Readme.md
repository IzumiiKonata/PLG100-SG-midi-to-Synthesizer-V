# PLG100-SG MIDI Parser and Convertor

## Project Overview

This project is a specialized tool for parsing and converting Japanese lyric data stored in the `Yamaha PLG100-SG` sound card.  
Based on technical specifications from [the official user manual](http://www.javelinart.com/PLG100SG_Manuals_InEnglish.zip), this tool can extract the `PLG100-SG`'s unique `PhoneSEQ` lyric encoding format from MIDI files and convert it to project files for modern voice synthesis software ([Synthesizer V Pro](https://dreamtonics.com/synthesizerv) and [VOCALOID6](https://www.vocaloid.com/en/vocaloid6)).

## Technical Analysis of PLG100-SG

### 1. PhoneSEQ Data Format

PLG100-SG uses MIDI System Exclusive messages (SysEx) to store lyric information, referred to as **PhoneSEQ data**.

#### 1.1 Standard Message Format
```
F0 43 1N 5D 03 0P 00 [PH] [DUR] ... [SP] F7
```

- **F0**: SysEx start flag
- **43**: Yamaha manufacturer ID
- **N**: Device number (usually is 0)
- **P**: Part Number (set by part assignment information)
- **[PH]**: Phoneme number (sequential number assigned to the phoneme), 01–7B
- **[DUR]**: Duration information (hexadecimal, time to sound the phoneme), 1=7.5ms
- **[SP]**: Special symbol (breath information=7E, singing end=7F)
- **F7**: SysEx end flag

#### 1.2 Pronunciation Modes

PLG100-SG supports 3 types of pronunciation modes:

| Mode | Condition | Behavior | Example |
|------|-----------|----------|----------|
| **Normal Pronunciation Mode** | Last phoneme time is 00 | Ignores note-off, continues until next note-on | `F0 43 10 5D 03 00 00 0D 0A 01 00 F7` |
| **Note-Off Pronunciation Mode** | Intermediate phoneme time is 00 | Moves to next phoneme on note-off | `F0 43 10 5D 03 00 00 0D 0A 01 00 22 0A F7` |
| **Fixed Time Pronunciation Mode** | All phoneme times are non-zero | Pronounces only for set time | `F0 43 10 5D 03 00 00 0D 0A 01 0A F7` |

#### 1.3 Breath Information

Breath marks (breathing points) are represented by **7E** to enhance naturalness of lyrics:
- `F0 43 10 5D 03 00 00 0D 0A 01 00 7E F7`
- Display: "さ▼" (sa▼)

### 2. SG_TABLE Phoneme Mapping

The project uses `SG_TABLE.csv` as a phoneme mapping table:

#### 2.1 Table Structure

| Field Name | Description | Example |
|------------|-------------|----------|
| `syllable_type` | Syllable type | "あ", "か", "さ" |
| `lyrics_representation` | Lyric display text | "あ", "か", "さ" |
| `input_text` | Input text | "あ", "か", "さ" |
| `phoneme_representation1-5` | Phoneme representation | "CL", "kha", "aj" |
| `SysEX_header` | SysEx header | "F0 43 1# 5D 03 0* 00" |
| `ph1-time5` | Phoneme numbers and time data | "7A", "01", "06", "04" |
| `eox` | End marker | "F7" |

#### 2.2 Phoneme Encoding Examples

| Lyric | Phoneme Sequence | PhoneSEQ Data |
|-------|------------------|----------------|
| あ | aj | `F0 43 10 5D 03 00 00 01 00 F7` |
| か | kha + aj | `F0 43 10 5D 03 00 00 7A 01 06 04 01 00 F7` |
| さ | ssa + aj | `F0 43 10 5D 03 00 00 0D 0A 01 00 F7` |
| さ▼ | ssa + aj + 7E | `F0 43 10 5D 03 00 00 0D 0A 01 00 7E F7` |

### 3. Decoding Algorithm

#### 3.1 PhoneSEQ Data Parsing

```java
private SGData parsePhoneSeqData(String hexArr) {
    // Header check
    if (!hexArr.startsWith("F0 43 1")) return null;
    if (!hexArr.startsWith(" 5D 03 0", 8)) return null;
    if (!hexArr.startsWith(" 00", 17)) return null;
    if (!hexArr.endsWith(" F7")) return null;
    
    // Data extraction
    String cont = hexArr.substring(21, hexArr.length() - 4).trim();
    String[] split = cont.split(" ");
    
    // Breath information check
    boolean hasBreath = false;
    List<String> filteredData = new ArrayList<>();
    for (String data : split) {
        if (data.equals("7E")) {
            hasBreath = true;
        } else {
            filteredData.add(data);
        }
    }
    split = filteredData.toArray(new String[0]);
    
    // Table search
    for (int length = split.length; length > 0; length--) {
        for (SGData sgData : table) {
            if (sgData.availFieldCount == length) {
                boolean match = true;
                for (int i = 0; i < length; i++) {
                    String field = sgData.getField(i + 9);
                    if (field.equals("**")) continue;
                    if (!field.equals(split[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    sgData.hasBreathMark = hasBreath;
                    return sgData;
                }
            }
        }
    }
    return null;
}
```

#### 3.2 Pronunciation Mode Determination Algorithm

```java
private int determinePronunciationMode() {
    // Zero time check
    boolean hasZeroTime = false;
    boolean lastIsZeroTime = false;
    
    // Check last valid time field
    if (!time5.isEmpty() && time5.equals("00")) {
        hasZeroTime = true;
        lastIsZeroTime = true;
    } else if (!time4.isEmpty() && time4.equals("00")) {
        hasZeroTime = true;
        lastIsZeroTime = true;
    } else if (!time3.isEmpty() && time3.equals("00")) {
        hasZeroTime = true;
        lastIsZeroTime = true;
    } else if (!time2.isEmpty() && time2.equals("00")) {
        hasZeroTime = true;
        lastIsZeroTime = true;
    } else if (!time1.isEmpty() && time1.equals("00")) {
        hasZeroTime = true;
        lastIsZeroTime = true;
    }
    
    // Mode determination
    if (!hasZeroTime) {
        return 3; // Fixed time pronunciation mode
    } else if (lastIsZeroTime) {
        return 1; // Normal pronunciation mode
    } else {
        return 2; // Note-off pronunciation mode
    }
}
```

### 4. MIDI Event Processing

#### 4.1 Note Event Processing

```java
if (sm.getCommand() == ShortMessage.NOTE_ON) {
    int note = sm.getData1();
    int velocity = sm.getData2();
    
    if (velocity == 0) {
        // NOTE_OFF event
        convertors.forEach(c -> {
            NoteRecord nr = noteRecord[note];
            if (nr != null && nr.lyrics != null) {
                String lyrics = nr.lyrics.lyrics_representation;
                c.insertNote(lyrics, nr.startTick, midiEvent.getTick(), note);
            }
        });
    } else {
        // NOTE_ON event
        noteRecord[sm.getData1()] = new NoteRecord(midiEvent.getTick(), curLyrics);
    }
}
```

#### 4.2 Pitch Bend Processing

```java
if (sm.getCommand() == ShortMessage.PITCH_BEND) {
    int lsb = sm.getData1();
    int msb = sm.getData2();
    
    // Calculate 14-bit pitch bend value
    int pitchBendValue = (msb << 7) | lsb;
    int relativeValue = pitchBendValue - 8192;
    
    convertors.forEach(c -> {
        c.onPitchBend(relativeValue, midiEvent.getTick());
    });
}
```
