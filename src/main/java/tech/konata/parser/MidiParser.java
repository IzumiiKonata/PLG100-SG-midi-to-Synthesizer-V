package tech.konata.parser;

import tech.konata.convert.ProjectConverter;

import javax.sound.midi.*;
import java.io.File;
import java.util.List;

/**
 * Parses a Standard MIDI File (SMF) and dispatches tempo, note, and pitch-bend
 * events to a list of {@link ProjectConverter} instances.
 *
 * <h2>Channel convention</h2>
 * Only events on MIDI channel 0 (the first channel) are processed.  This
 * matches the PLG100-SG's default part assignment.
 *
 * <h2>PhoneSEQ SysEx</h2>
 * Each SysEx message on any channel is tested by {@link PhoneSeqParser}.  If it
 * matches the PLG100-SG PhoneSEQ format, the resolved {@link SgData} is stored
 * and assigned to the next NOTE_ON event.
 *
 * <h2>Note matching</h2>
 * NOTE_ON with velocity 0 is treated as NOTE_OFF (per the MIDI spec).  A note is
 * only forwarded to the converters when its corresponding NOTE_OFF is received
 * <em>and</em> a non-null {@link SgData} record was set before the NOTE_ON.
 */
public final class MidiParser {

    private static final int TARGET_CHANNEL = 0;

    private final List<ProjectConverter> converters;
    private final PhoneSeqParser         phoneSeqParser;

    /** Per-MIDI-key pending note start tick and associated SgData. */
    private final long[]   noteStartTick = new long[128];
    private final SgData[] noteLyrics    = new SgData[128];

    /** The SgData resolved from the most recently seen PhoneSEQ SysEx. */
    private SgData pendingLyric = null;

    public MidiParser(List<ProjectConverter> converters, List<SgData> sgTable) {
        this.converters     = converters;
        this.phoneSeqParser = new PhoneSeqParser(sgTable);
    }

    /**
     * Parses {@code midiFile} and dispatches all relevant events to the
     * registered converters.
     *
     * @param midiFile the SMF file to parse
     * @throws RuntimeException if the file cannot be read
     */
    public void parse(File midiFile) {
        System.out.println("Parsing MIDI file: " + midiFile.getName());

        Sequence sequence;
        try {
            sequence = MidiSystem.getSequence(midiFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read MIDI file: " + midiFile, e);
        }

        Track[] tracks = sequence.getTracks();
        System.out.println("Tracks: " + tracks.length);

        double msPerTick = 0.0;

        for (int ti = 0; ti < tracks.length; ti++) {
            Track track = tracks[ti];
            System.out.println("Track " + ti + ": " + track.size() + " events");

            for (int ei = 0; ei < track.size(); ei++) {
                MidiEvent   event   = track.get(ei);
                MidiMessage message = event.getMessage();
                long        tick    = event.getTick();

                if (message instanceof MetaMessage) {
                    msPerTick = handleMetaMessage((MetaMessage) message, tick, msPerTick, sequence);

                } else if (message instanceof ShortMessage) {
                    double curMs = tick * msPerTick;
                    handleShortMessage((ShortMessage) message, tick, curMs);

                } else if (message instanceof SysexMessage) {
                    handleSysexMessage((SysexMessage) message);
                }
            }
        }
    }

    /**
     * Handles meta messages; returns the (possibly updated) ms-per-tick value.
     */
    private double handleMetaMessage(MetaMessage mm, long tick,
                                     double currentMsPerTick, Sequence sequence) {
        if (mm.getType() != 0x51) return currentMsPerTick; // only SET_TEMPO

        byte[] data  = mm.getData();
        int    tempo = (data[0] & 0xFF) << 16 | (data[1] & 0xFF) << 8 | (data[2] & 0xFF);
        double bpm   = 60_000_000.0 / tempo;

        converters.forEach(c -> c.insertTempo(tick, bpm));

        System.out.printf("[Tick %d] Tempo: %.2f BPM%n", tick, bpm);
        return 60_000.0 / (bpm * sequence.getResolution());
    }

    private void handleShortMessage(ShortMessage sm, long tick, double curMs) {
        if (sm.getChannel() != TARGET_CHANNEL) {
            return;
        }

        switch (sm.getCommand()) {
            case ShortMessage.NOTE_ON:
                handleNoteOn(sm, tick, curMs);
                break;
            case ShortMessage.PITCH_BEND:
                handlePitchBend(sm, tick, curMs);
                break;
            default:
                break;
        }
    }

    private void handleNoteOn(ShortMessage sm, long tick, double curMs) {
        int note     = sm.getData1();
        int velocity = sm.getData2();

        if (velocity == 0) {
            // NOTE_ON with velocity 0 = NOTE_OFF
            handleNoteOff(note, tick, curMs);
        } else {
            System.out.printf("[%.1f ms] NOTE_ON: note=%d vel=%d%n", curMs, note, velocity);
            if (pendingLyric != null) {
                System.out.println("  - Lyric: " + pendingLyric.lyricsRepresentation
                        + ", mode=" + pendingLyric.pronunciationMode);
                if (pendingLyric.hasBreathMark) System.out.println("  - Has breath mark");
            }
            noteStartTick[note] = tick;
            noteLyrics[note]    = pendingLyric;
        }
    }

    private void handleNoteOff(int note, long tick, double curMs) {
        System.out.printf("[%.1f ms] NOTE_OFF: note=%d%n", curMs, note);
        SgData lyric = noteLyrics[note];
        if (lyric == null) return;

        long startTick = noteStartTick[note];
        String syllable = lyric.lyricsRepresentation;
        converters.forEach(c -> c.insertNote(syllable, startTick, tick, note));
    }

    private void handlePitchBend(ShortMessage sm, long tick, double curMs) {
        int lsb   = sm.getData1();
        int msb   = sm.getData2();
        int value = ((msb << 7) | lsb) - 8192; // centre at 0

        converters.forEach(c -> c.onPitchBend(value, tick));
        System.out.printf("[%.1f ms] PITCH_BEND: %d%n", curMs, value);
    }

    private void handleSysexMessage(SysexMessage sysex) {
        StringBuilder sb = new StringBuilder();
        for (byte b : sysex.getMessage()) {
            sb.append(String.format("%02X ", b));
        }
        // Remove trailing space
        String hex = sb.toString().stripTrailing();
        System.out.println("[SysEx] " + hex);

        SgData parsed = phoneSeqParser.parse(hex);
        if (parsed != null) {
            System.out.println("  - Parsed: " + parsed.inputText);
            System.out.println("  - Mode=" + parsed.pronunciationMode
                    + ", breath=" + parsed.hasBreathMark);
            System.out.println("  - Phonemes: " + parsed.getValidPhonemeCount());
            pendingLyric = parsed;
        } else {
            System.out.println("  - [Err] PhoneSEQ data parse failed");
        }
    }
}
