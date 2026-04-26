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
