package tech.konata.convert.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import tech.konata.convert.*;
import tech.konata.convert.pitch.PitchConverter;
import tech.konata.convert.pitch.SynthVPitchConversion;

import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts MIDI data to the Synthesizer V {@code .svp} project format.
 *
 * <h2>SVP tick / position unit</h2>
 * Synthesizer V uses a "blick" unit internally.  The conversion factor is
 * {@value #BLICKS_PER_TICK} blicks per MIDI tick (at the standard 480-TPQN
 * resolution used by this project).
 *
 * <h2>Pitch encoding</h2>
 * The {@code pitchDelta} channel stores semitone offsets × 100 (cents), as a
 * flat array of {@code [position0, value0, position1, value1, ...]} doubles.
 */
public final class SVP extends ProjectConverter {

    /** Blicks per MIDI tick (SVP's internal time unit). */
    private static final long   BLICKS_PER_TICK    = 1_470_000L;

    /** Cents per semitone. */
    private static final double CENTS_PER_SEMITONE = 100.0;

    // Note default attribute values
    private static final double  DEFAULT_DETUNE          = 0.0;
    private static final double  DEFAULT_F0_OFFSET       = 0.0;
    private static final double  DEFAULT_F0_TRANSITION   = 0.05000000074505806;
    private static final double  DEFAULT_F0_DEPTH        = 0.0;
    private static final double  DEFAULT_F0_VBR          = 0.0;
    private static final double  DEFAULT_EXPRESSION      = 1.0;
    private static final boolean DEFAULT_INSTANT_MODE    = true;
    private static final int     DEFAULT_ACTIVE_TAKE_ID  = 0;
    private static final boolean DEFAULT_LIKED           = false;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private JsonObject project;
    private final List<Note>               notes         = new ArrayList<>();
    private final List<Pair<Long, Double>> pitchBendData = new ArrayList<>();

    @Override
    public void load() {
        String resourcePath = "/SynthesizerV_Project_Template.json";
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Synthesizer V project template not found: " + resourcePath);
            }
            project = gson.fromJson(new InputStreamReader(in), JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Synthesizer V project template", e);
        }
    }

    @Override
    public void insertTempo(long tick, double bpm) {
        validateTick(tick);
        validateBpm(bpm);

        JsonObject time       = project.getAsJsonObject("time");
        JsonArray  tempoArray = time.getAsJsonArray("tempo");
        tempoArray.add(buildTempoObject(tick, bpm));
        time.add("tempo", tempoArray);
        project.add("time", time);
    }

    @Override
    public void insertNote(String lyric, long tickStart, long tickEnd, int midiKey) {
        validateLyric(lyric);
        validateTickRange(tickStart, tickEnd);
        validateMidiKey(midiKey);

        notes.add(new Note(midiKey, tickStart, tickEnd, lyric));

        JsonArray  tracks    = project.getAsJsonArray("tracks");
        JsonObject track     = tracks.get(0).getAsJsonObject();
        JsonObject mainGroup = track.getAsJsonObject("mainGroup");
        JsonArray  noteArr   = mainGroup.getAsJsonArray("notes");

        noteArr.add(buildNoteObject(lyric, tickStart, tickEnd, midiKey));
        writeTrackBack(track, mainGroup, noteArr, tracks);
    }

    @Override
    public void onPitchBend(int value, long tick) {
        validatePitchBend(value);
        // Scale 14-bit signed MIDI pitch-bend to semitones (±8192 → ±10.67 semitones
        // with the PLG100-SG's default sensitivity of 768 units/semitone)
        pitchBendData.add(new Pair<>(tick, value / 768.0));
    }

    @Override
    public void save(String baseName) {
        if (baseName == null || baseName.isBlank()) {
            throw new IllegalArgumentException("Output file base name must not be blank");
        }

        Pitch pitch = new Pitch(pitchBendData, /* absolute */ false);

        JsonArray  tracks    = project.getAsJsonArray("tracks");
        JsonObject track     = tracks.get(0).getAsJsonObject();
        JsonObject mainGroup = track.getAsJsonObject("mainGroup");

        writePitchDelta(mainGroup, pitch);
        writeTrackBack(track, mainGroup, mainGroup.getAsJsonArray("notes"), tracks);

        String outputPath = baseName + ".svp";
        try (FileWriter writer = new FileWriter(outputPath)) {
            gson.toJson(project, writer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write SVP file: " + outputPath, e);
        }
    }

    private JsonObject buildTempoObject(long tick, double bpm) {
        JsonObject obj = new JsonObject();
        obj.addProperty("position", tick * BLICKS_PER_TICK);
        obj.addProperty("bpm", bpm);
        return obj;
    }

    private JsonObject buildNoteObject(String lyric, long tickStart, long tickEnd, int midiKey) {
        JsonObject obj = new JsonObject();
        obj.addProperty("musicalType",   "singing");
        obj.addProperty("onset",         tickStart * BLICKS_PER_TICK);
        obj.addProperty("duration",      (tickEnd - tickStart) * BLICKS_PER_TICK);
        obj.addProperty("lyrics",        lyric);
        obj.addProperty("phonemes",      "");
        obj.addProperty("accent",        "");
        obj.addProperty("pitch",         midiKey);
        obj.addProperty("detune",        DEFAULT_DETUNE);
        obj.addProperty("instantMode",   DEFAULT_INSTANT_MODE);
        obj.add("attributes",       new JsonObject());
        obj.add("systemAttributes", buildSystemAttributes());
        obj.add("pitchTakes",       buildPitchTakes());
        obj.add("timbreTakes",      buildTimbreTakes());
        return obj;
    }

    private JsonObject buildSystemAttributes() {
        JsonObject sa = new JsonObject();
        sa.addProperty("tF0Offset", DEFAULT_F0_OFFSET);
        sa.addProperty("tF0Left",   DEFAULT_F0_TRANSITION);
        sa.addProperty("tF0Right",  DEFAULT_F0_TRANSITION);
        sa.addProperty("dF0Left",   DEFAULT_F0_DEPTH);
        sa.addProperty("dF0Right",  DEFAULT_F0_DEPTH);
        sa.addProperty("dF0Vbr",    DEFAULT_F0_VBR);
        return sa;
    }

    private JsonObject buildPitchTakes() {
        JsonObject takes    = new JsonObject();
        JsonArray  takeArr  = new JsonArray();
        JsonObject takeItem = new JsonObject();

        takes.addProperty("activeTakeId", DEFAULT_ACTIVE_TAKE_ID);
        takeItem.addProperty("id",    DEFAULT_ACTIVE_TAKE_ID);
        takeItem.addProperty("expr",  DEFAULT_EXPRESSION);
        takeItem.addProperty("liked", DEFAULT_LIKED);
        takeArr.add(takeItem);
        takes.add("takes", takeArr);
        return takes;
    }

    private JsonObject buildTimbreTakes() {
        JsonObject takes    = new JsonObject();
        JsonArray  takeArr  = new JsonArray();
        JsonObject takeItem = new JsonObject();

        takes.addProperty("activeTakeId", DEFAULT_ACTIVE_TAKE_ID);
        takes.addProperty("expr",         DEFAULT_EXPRESSION);
        takeItem.addProperty("id",    DEFAULT_ACTIVE_TAKE_ID);
        takeItem.addProperty("expr",  DEFAULT_EXPRESSION);
        takeItem.addProperty("liked", DEFAULT_LIKED);
        takeArr.add(takeItem);
        takes.add("takes", takeArr);
        return takes;
    }

    /**
     * Generates the pitch-delta point list and writes it into {@code mainGroup}.
     *
     * <p>SVP's {@code pitchDelta} channel stores data as an interleaved flat array:
     * {@code [blick0, cents0, blick1, cents1, ...]}.
     */
    private void writePitchDelta(JsonObject mainGroup, Pitch pitch) {
        JsonObject parameters = mainGroup.getAsJsonObject("parameters");
        JsonObject pitchDelta = parameters.getAsJsonObject("pitchDelta");
        JsonArray  points     = pitchDelta.getAsJsonArray("points");

        List<Pair<Long, Double>> relativeData = PitchConverter.getRelativeData(pitch, notes);
        if (relativeData != null && !relativeData.isEmpty()) {
            List<Pair<Long, Double>> prepared = SynthVPitchConversion.prepareForSvpOutput(relativeData);
            for (Pair<Long, Double> p : prepared) {
                points.add((double) (p.first * BLICKS_PER_TICK));   // position in blicks
                points.add(p.second * CENTS_PER_SEMITONE);          // value in cents
            }
        }

        pitchDelta.add("points", points);
        parameters.add("pitchDelta", pitchDelta);
        mainGroup.add("parameters", parameters);
    }

    private void writeTrackBack(
            JsonObject track, JsonObject mainGroup,
            JsonArray notesArray, JsonArray tracks) {

        mainGroup.add("notes", notesArray);
        track.add("mainGroup", mainGroup);

        JsonArray updatedTracks = new JsonArray();
        updatedTracks.add(track);
        project.add("tracks", updatedTracks);
    }

    private static void validateTick(long tick) {
        if (tick < 0) throw new IllegalArgumentException("Tick cannot be negative: " + tick);
    }

    private static void validateBpm(double bpm) {
        if (bpm <= 0) throw new IllegalArgumentException("BPM must be positive: " + bpm);
    }

    private static void validateLyric(String lyric) {
        if (lyric == null || lyric.isBlank()) {
            throw new IllegalArgumentException("Lyric must not be null or blank");
        }
    }

    private static void validateTickRange(long tickStart, long tickEnd) {
        if (tickStart < 0 || tickEnd < 0) {
            throw new IllegalArgumentException("Tick values must be non-negative");
        }
        if (tickEnd < tickStart) {
            throw new IllegalArgumentException(
                    "tickEnd (" + tickEnd + ") must be > tickStart (" + tickStart + ")");
        }
    }

    private static void validateMidiKey(int key) {
        if (key < 0 || key > 127) {
            throw new IllegalArgumentException("MIDI key must be in [0, 127]: " + key);
        }
    }

    private static void validatePitchBend(int value) {
        if (value < -8192 || value > 8191) {
            throw new IllegalArgumentException("Pitch-bend value out of range: " + value);
        }
    }
}
