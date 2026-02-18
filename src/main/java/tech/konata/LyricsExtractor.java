package tech.konata;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import tech.konata.convert.ProjectConvertor;
import tech.konata.convert.impl.SVP;
import tech.konata.convert.impl.VPR;

import javax.sound.midi.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author IzumiiKonata
 * Date: 2025/4/10 22:50
 */
public class LyricsExtractor {

    @SneakyThrows
    public static void main(String[] args) {
        new LyricsExtractor().run();

    }

    public LyricsExtractor() {

    }

    List<ProjectConvertor> convertors = Arrays.asList(
            new SVP(),
            new VPR()
    );

    public void run() {
        this.parseCSVTable();

        convertors.forEach(ProjectConvertor::load);

        // haruyoko, tokinona
        File file = new File("D:\\MidiTest\\haruyoko.MID");
        this.parseMidi(file);

        convertors.forEach(c -> {
            c.save(file.getName().substring(0, file.getName().lastIndexOf(".")));
        });

    }

    List<SGData> table = new ArrayList<>();

    @SneakyThrows
    private void parseCSVTable() {

        InputStream is = LyricsExtractor.class.getResourceAsStream("/SG_TABLE.csv");

        String[] strings = new String(is.readAllBytes()).split("\n");

        // skip first line
        for (int i = 1; i < strings.length; i++) {

            String line = strings[i].trim();
            if (line.isEmpty()) continue;

            String[] split = line.split(",");

            // Convert single-digit hex to double-digit
            for (int j = 7; j < split.length; j++) {

                if (split[j].length() == 1) {
                    split[j] = "0" + split[j];
                }

            }

            // Complement data
            while (split.length < 20) {
                split = Arrays.copyOf(split, split.length + 1);
                split[split.length - 1] = "";
            }

            table.add(new SGData(split[0], split[1], split[2], split[3], split[4], split[5], split[6], split[7], split[8], split[9], split[10], split[11], split[12], split[13], split[14], split[15], split[16], split[17], split[18], split[19]));
        }

        System.out.println("SG_TABLE loaded: " + table.size() + " entries");

    }

    @AllArgsConstructor
    private static class NoteRecord {

        public long startTick;
        public SGData lyrics;
    }

    @SneakyThrows
    private void parseMidi(File midiIn) {

        Sequence sequence = MidiSystem.getSequence(midiIn);
        Track[] tracks = sequence.getTracks();

        double msPerTick = 0;
        NoteRecord[] noteRecord = new NoteRecord[128];

        System.out.println("Parsing MIDI file: " + midiIn.getName());
        System.out.println("Tracks: " + tracks.length);

        for (int i = 0; i < tracks.length; i++) {
            Track track = tracks[i];
            System.out.println("Track " + i + ": " + track.size() + " events");

            for (int j = 0; j < track.size(); j++) {
                MidiEvent midiEvent = track.get(j);
                MidiMessage message = midiEvent.getMessage();

                if (message instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) message;
                    if(mm.getType() == 0x51 /*set tempo*/){
                        byte[] data = mm.getData();
                        int tempo = (data[0] & 0xff) << 16 | (data[1] & 0xff) << 8 | (data[2] & 0xff);
                        double bpm = (double) (60000000 / tempo);

                        convertors.forEach(c -> {
                            c.insertTempo(midiEvent.getTick(), bpm);
                        });

                        msPerTick = (60000 / (bpm * sequence.getResolution()));
                        System.out.println("[Tick " + midiEvent.getTick() + "] Tempo: " + bpm + " BPM");
                    }
                }

                double curMillis = midiEvent.getTick() * msPerTick;

                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;

                    if (sm.getChannel() == 0) {
                        if (sm.getCommand() == ShortMessage.NOTE_ON) {
                            int note = sm.getData1();
                            int velocity = sm.getData2();

                            if (velocity == 0) {
                                // NOTE_OFF event
                                System.out.println("[" + curMillis + "] NOTE_OFF: Note: " + sm.getData1() + " Velocity: " + sm.getData2());

                                convertors.forEach(c -> {
                                    NoteRecord nr = noteRecord[note];
                                    if (nr != null && nr.lyrics != null) {
                                        String lyrics = nr.lyrics.lyrics_representation;
                                        c.insertNote(lyrics, nr.startTick, midiEvent.getTick(), note);
                                    }
                                });
                            } else {
                                // NOTE_ON event
                                System.out.println("[" + curMillis + "] NOTE_ON: Note: " + sm.getData1() + " Velocity: " + sm.getData2());

                                if (curLyrics != null) {
                                    System.out.println("  - Lyrics: " + curLyrics.lyrics_representation + ", Mode: " + curLyrics.pronunciationMode);
                                    if (curLyrics.hasBreathMark) {
                                        System.out.println("  - Has breath mark");
                                    }
                                }
                                noteRecord[sm.getData1()] = new NoteRecord(midiEvent.getTick(), curLyrics);
                            }

                        }

                        if (sm.getCommand() == ShortMessage.PITCH_BEND) {
                            int lsb = sm.getData1(); // Low byte
                            int msb = sm.getData2(); // High byte

                            // Calculate pitch bend value
                            int pitchBendValue = (msb << 7) | lsb;
                            int relativeValue = pitchBendValue - 8192;
                            
                            convertors.forEach(c -> {
                                c.onPitchBend(relativeValue, midiEvent.getTick());
                            });

                            System.out.println("[" + curMillis + "] PITCH_BEND: " + relativeValue);
                        }

                    }

                }

                if (message instanceof SysexMessage) {
                    SysexMessage sysexMessage = (SysexMessage) message;

                    StringBuilder sb = new StringBuilder();
                    for (byte b : sysexMessage.getMessage()) {
                        sb.append(String.format("%02X ", b));
                    }
                    String hexString = sb.toString().substring(0, sb.length() - 1);
                    System.out.println("[SysEx] " + hexString);

                    SGData parsed = parsePhoneSeqData(hexString);

                    if (parsed != null) {
                        System.out.println("  - Parsed: " + parsed.input_text);
                        System.out.println("  - Mode: " + parsed.pronunciationMode + ", Breath: " + parsed.hasBreathMark);
                        System.out.println("  - Phonemes: " + parsed.getValidPhonemeCount() + " phonemes");
                        curLyrics = parsed;
                    } else {
                        System.out.println("  - [Err] PhoneSEQ data parse failed");
                    }

                }

            }

        }
    }

    SGData curLyrics = null;

    /**
     * PhoneSEQ data parsing
     * Parse PLG100-SG system exclusive messages
     */
    private SGData parsePhoneSeqData(String hexArr) {
        // PhoneSEQ header check
        if (!hexArr.startsWith("F0 43 1")) {
            return null;
        }

        if (!hexArr.startsWith(" 5D 03 0", 8)) {
            return null;
        }

        if (!hexArr.startsWith(" 00", 17)) {
            return null;
        }

        if (!hexArr.endsWith(" F7")) {
            return null;
        }

        // Extract data part
        String cont = hexArr.substring(21, hexArr.length() - 4).trim();
        if (cont.isEmpty()) {
            return null;
        }

        System.out.println("  - Content: " + cont);
        String[] split = cont.split(" ");

        // Check for breath information (7E)
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
            mainLoop:
            for (SGData sgData : table) {
                if (sgData.availFieldCount == length) {
                    for (int i = 0; i < length; i++) {
                        String field = sgData.getField(i + 9);
                        if (field.equals("**")) {
                            continue;
                        }
                        if (!field.equals(split[i])) {
                            continue mainLoop;
                        }
                    }
                    // Set breath information
                    sgData.hasBreathMark = hasBreath;
                    return sgData;
                }
            }
        }

        return null;
    }

}
