package tech.konata;

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

        // haruyoko
        File file = new File("D:\\MidiTest\\tokinona.MID");
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

            String line = strings[i];

            String[] split = line.split(",");

            for (int j = 7; j < split.length; j++) {

                if (split[j].length() == 1) {
                    split[j] = "0" + split[j];
                }

            }

            table.add(new SGData(split[0], split[1], split[2], split[3], split[4], split[5], split[6], split[7], split[8], split[9], split[10], split[11], split[12], split[13], split[14], split[15], split[16], split[17], split[18], split[19]));
        }

    }

    @SneakyThrows
    private void parseMidi(File midiIn) {

        Sequence sequence = MidiSystem.getSequence(midiIn);

        Track[] tracks = sequence.getTracks();

        double msPerTick = 0;
        long noteStartTick = 0;

        for (int i = 0; i < tracks.length; i++) {
            Track track = tracks[i];

            for (int j = 0; j < track.size(); j++) {
                MidiEvent midiEvent = track.get(j);
                MidiMessage message = midiEvent.getMessage();

                if (message instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) message;
                    if(mm.getType() == 0x51 /*set tempo*/){
                        byte[] data = mm.getData();
                        int tempo = (data[0] & 0xff) << 16 | (data[1] & 0xff) << 8 | (data[2] & 0xff);
                        double bpm = (double) (60000000 / tempo) + 1;

                        convertors.forEach(c -> {
                            c.insertTempo(midiEvent.getTick(), bpm);
                        });

                        msPerTick = (60000 / (bpm * sequence.getResolution()));
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
                                System.out.println("[" + curMillis + "] NOTE_OFF: Note: " + sm.getData1() + " Velocity: " + sm.getData2());

                                long finalNoteStartTick = noteStartTick;
                                convertors.forEach(c -> {
                                    c.insertNote(curLyrics.lyrics_representation, finalNoteStartTick, midiEvent.getTick(), note);
                                });
                            } else {
                                System.out.println("[" + curMillis + "] NOTE_ON: Note: " + sm.getData1() + " Velocity: " + sm.getData2());

                                noteStartTick = midiEvent.getTick();
                            }

                        }
                    }

                }

                if (message instanceof SysexMessage) {
                    SysexMessage sysexMessage = (SysexMessage) message;

                    StringBuilder sb = new StringBuilder();
                    for (byte b : sysexMessage.getMessage()) {
                        sb.append(String.format("%02X ", b));
                    }
                    System.out.println(sb);

                    SGData parsed = parse(sb.toString());

                    if (parsed != null) {
                        System.out.println(parsed.input_text);
                        curLyrics = parsed;
                    } else {
                        System.out.println("[Err] Find failed");
                    }

                }

            }

        }
    }

    SGData curLyrics = null;

    private SGData parse(String hexArr) {

        // header is always F0 43 1# 5D 03 0* 00

        if (!hexArr.startsWith("F0 43 1")) {
            return null;
        }

        if (!hexArr.startsWith(" 5D 03 0", 8)) {
            return null;
        }

        if (!hexArr.startsWith(" 00", 17)) {
            return null;
        }

        if (!hexArr.endsWith(" F7 ")) {
            return null;
        }

        String cont = hexArr.substring(21, hexArr.length() - 4);
//        System.out.println("Content: " + cont);

        String[] split = cont.split(" ");

        for (int length = split.length; length > 0; length--) {

            for (SGData sgData : table) {

                if (sgData.availFieldCount == length) {

                    if (sgData.ph1.equals(split[0])) {
                        return sgData;
                    }

                }

            }

        }

        return null;
    }

}
