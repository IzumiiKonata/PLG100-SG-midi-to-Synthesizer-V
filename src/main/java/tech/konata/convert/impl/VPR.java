package tech.konata.convert.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import tech.konata.convert.*;
import tech.konata.convert.pitch.VocaloidPitchConverter;
import tech.konata.convert.pitch.VocaloidPitchConverter.VocaloidPartPitchData;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Converts MIDI data to the VOCALOID 6 {@code .vpr} project format.
 *
 * <h2>File layout</h2>
 * A {@code .vpr} file is a ZIP archive (DEFLATE, level 0) containing:
 * <ul>
 *   <li>{@code Project/sequence.json} – the main project JSON.</li>
 *   <li>{@code Project/Audio/}        – an empty directory placeholder.</li>
 * </ul>
 *
 * <h2>BPM encoding</h2>
 * VOCALOID stores tempo as {@code round(bpm * 100)}, so 120.0 BPM → {@code 12000}.
 *
 * <h2>Multi-character lyrics</h2>
 * If a lyric syllable contains more than one character it is split into
 * individual single-character notes that evenly share the original tick range.
 * This is a best-effort fallback; the PLG100-SG normally sends single-character
 * or combined-phoneme syllables.
 */
public final class VPR extends ProjectConverter {

    private static final int    DEFAULT_VELOCITY              = 64;
    private static final int    DEFAULT_ACCENT                = 50;
    private static final int    DEFAULT_DECAY                 = 50;
    private static final int    DEFAULT_BEND_DEPTH            = 0;
    private static final int    DEFAULT_BEND_LENGTH           = 0;
    private static final int    DEFAULT_OPENING               = 127;
    private static final double DEFAULT_AI_EXPRESSION         = 0.5;
    private static final int    DEFAULT_SINGING_SKILL_DURATION = 160;
    private static final int    DEFAULT_WEIGHT_PRE_POST        = 64;
    private static final int    DEFAULT_VIBRATO_TYPE           = 0;
    private static final int    DEFAULT_VIBRATO_DURATION       = 320;
    private static final int    DEFAULT_VIBRATO_POSITION       = 0;
    private static final int    DEFAULT_VIBRATO_VALUE          = 0;
    private static final boolean DEFAULT_IS_PROTECTED          = false;
    private static final int    DEFAULT_LANG_ID                = 0;
    private static final int    BPM_SCALE                      = 100;

    private static final String PHONEME_DEFAULT = "a";

    private final Gson         gson          = new GsonBuilder().setPrettyPrinting().create();
    private final DecimalFormat bpmFormatter = new DecimalFormat("##.##");

    private JsonObject project;
    private final List<Note>               notes         = new ArrayList<>();
    private final List<Pair<Long, Double>> pitchBendData = new ArrayList<>();

    @Override
    public void load() {
        String resourcePath = "/VOCALOID6_Project_Template.json";
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "VOCALOID project template not found: " + resourcePath);
            }
            project = gson.fromJson(new InputStreamReader(in), JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load VOCALOID project template", e);
        }
    }

    @Override
    public void insertTempo(long tick, double bpm) {
        validateTick(tick);
        validateBpm(bpm);

        JsonObject masterTrack = project.getAsJsonObject("masterTrack");
        JsonObject tempo       = masterTrack.getAsJsonObject("tempo");
        JsonArray  events      = tempo.getAsJsonArray("events");

        boolean isFirstTempo = events.isEmpty();

        events.add(buildTempoEvent(tick, bpm));

        if (isFirstTempo) {
            // Populate the "global" field used for display purposes
            tempo.add("global", buildGlobalTempo(bpm));
        }

        tempo.add("events", events);
        masterTrack.add("tempo", tempo);
        project.add("masterTrack", masterTrack);
    }

    /**
     * Inserts a note. Multi-character lyrics are split into individual notes
     * that divide the tick range equally.
     */
    @Override
    public void insertNote(String lyric, long tickStart, long tickEnd, int midiKey) {
        validateLyric(lyric);
        validateTickRange(tickStart, tickEnd);
        validateMidiKey(midiKey);

        if (lyric.length() > 1) {
            splitAndInsertMultiCharNote(lyric, tickStart, tickEnd, midiKey);
            return;
        }

        notes.add(new Note(midiKey, tickStart, tickEnd, lyric));

        JsonArray  tracks    = project.getAsJsonArray("tracks");
        JsonObject track     = tracks.get(0).getAsJsonObject();
        JsonArray  parts     = track.getAsJsonArray("parts");
        JsonObject part      = parts.get(0).getAsJsonObject();
        JsonArray  noteArr   = part.getAsJsonArray("notes");

        noteArr.add(buildNoteObject(lyric, tickStart, tickEnd, midiKey));
        writeTrackBack(part, parts, track, tracks);
    }

    @Override
    public void onPitchBend(int value, long tick) {
        validatePitchBend(value);
        pitchBendData.add(new Pair<>(tick, value / 768.0));
    }

    @Override
    public void save(String baseName) {
        if (baseName == null || baseName.isBlank()) {
            throw new IllegalArgumentException("Output file base name must not be blank");
        }

        Pitch pitch = new Pitch(pitchBendData, /* absolute */ false);
        VocaloidPartPitchData pitchData = VocaloidPitchConverter.generateForVocaloid(pitch, notes);

        if (pitchData != null) {
            writePitchControllers(pitchData);
        }

        String outputPath = baseName + ".vpr";
        try {
            writeVprZip(outputPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write VPR file: " + outputPath, e);
        }
    }

    private void splitAndInsertMultiCharNote(
            String lyric, long tickStart, long tickEnd, int midiKey) {

        int  charCount    = lyric.length();
        long totalDuration = tickEnd - tickStart;

        for (int i = 0; i < charCount; i++) {
            long charStart = tickStart + (totalDuration / charCount) * i;
            long charEnd   = tickStart + (totalDuration / charCount) * (i + 1);
            // Ensure the last character extends exactly to tickEnd
            if (i == charCount - 1) charEnd = tickEnd;
            insertNote(String.valueOf(lyric.charAt(i)), charStart, charEnd, midiKey);
        }
    }

    private JsonObject buildTempoEvent(long tick, double bpm) {
        JsonObject obj = new JsonObject();
        obj.addProperty("pos",   tick);
        obj.addProperty("value", scaledBpm(bpm));
        return obj;
    }

    private JsonObject buildGlobalTempo(double bpm) {
        JsonObject obj = new JsonObject();
        obj.addProperty("isEnabled", false);
        obj.addProperty("value",     scaledBpm(bpm));
        return obj;
    }

    private int scaledBpm(double bpm) {
        return (int) (Double.parseDouble(bpmFormatter.format(bpm)) * BPM_SCALE);
    }

    private JsonObject buildNoteObject(String lyric, long tickStart, long tickEnd, int midiKey) {
        JsonObject obj = new JsonObject();
        obj.addProperty("lyric",       lyric);
        obj.addProperty("phoneme",     PHONEME_DEFAULT);
        obj.addProperty("langID",      DEFAULT_LANG_ID);
        obj.addProperty("isProtected", DEFAULT_IS_PROTECTED);
        obj.addProperty("pos",         tickStart);
        obj.addProperty("duration",    tickEnd - tickStart);
        obj.addProperty("number",      midiKey);
        obj.addProperty("velocity",    DEFAULT_VELOCITY);
        obj.add("exp",          buildExpression());
        obj.add("aiExp",        buildAiExpression());
        obj.add("singingSkill", buildSingingSkill());
        obj.add("vibrato",      buildVibrato());
        return obj;
    }

    private JsonObject buildExpression() {
        JsonObject exp = new JsonObject();
        exp.addProperty("accent",     DEFAULT_ACCENT);
        exp.addProperty("decay",      DEFAULT_DECAY);
        exp.addProperty("bendDepth",  DEFAULT_BEND_DEPTH);
        exp.addProperty("bendLength", DEFAULT_BEND_LENGTH);
        exp.addProperty("opening",    DEFAULT_OPENING);
        return exp;
    }

    private JsonObject buildAiExpression() {
        JsonObject ai = new JsonObject();
        String[] fields = {
            "pitchFine", "pitchDriftStart", "pitchDriftEnd",
            "pitchScalingCenter", "pitchScalingOrigin",
            "pitchTransitionStart", "pitchTransitionEnd",
            "amplitudeWhole", "amplitudeStart", "amplitudeEnd",
            "vibratoLeadingDepth", "vibratoFollowingDepth"
        };
        for (String f : fields) {
            ai.addProperty(f, DEFAULT_AI_EXPRESSION);
        }
        return ai;
    }

    private JsonObject buildSingingSkill() {
        JsonObject skill  = new JsonObject();
        JsonObject weight = new JsonObject();
        weight.addProperty("pre",  DEFAULT_WEIGHT_PRE_POST);
        weight.addProperty("post", DEFAULT_WEIGHT_PRE_POST);
        skill.addProperty("duration", DEFAULT_SINGING_SKILL_DURATION);
        skill.add("weight", weight);
        return skill;
    }

    private JsonObject buildVibrato() {
        JsonObject vibrato = new JsonObject();
        vibrato.addProperty("type",     DEFAULT_VIBRATO_TYPE);
        vibrato.addProperty("duration", DEFAULT_VIBRATO_DURATION);

        JsonArray depths = new JsonArray();
        JsonObject d = new JsonObject();
        d.addProperty("pos",   DEFAULT_VIBRATO_POSITION);
        d.addProperty("value", DEFAULT_VIBRATO_VALUE);
        depths.add(d);
        vibrato.add("depths", depths);

        JsonArray rates = new JsonArray();
        JsonObject r = new JsonObject();
        r.addProperty("pos",   DEFAULT_VIBRATO_POSITION);
        r.addProperty("value", DEFAULT_VIBRATO_VALUE);
        rates.add(r);
        vibrato.add("rates", rates);

        return vibrato;
    }

    private void writePitchControllers(VocaloidPartPitchData pitchData) {
        JsonArray  tracks      = project.getAsJsonArray("tracks");
        JsonObject track       = tracks.get(0).getAsJsonObject();
        JsonArray  parts       = track.getAsJsonArray("parts");
        JsonObject part        = parts.get(0).getAsJsonObject();
        JsonArray  controllers = part.getAsJsonArray("controllers");

        if (!pitchData.getPbs().isEmpty()) {
            controllers.add(buildController("pitchBendSens", pitchData.getPbs()));
        }
        if (!pitchData.getPit().isEmpty()) {
            controllers.add(buildController("pitchBend", pitchData.getPit()));
        }

        part.add("controllers", controllers);
        writeTrackBack(part, parts, track, tracks);
    }

    private JsonObject buildController(String name, List<VocaloidPartPitchData.Event> events) {
        JsonObject ctrl      = new JsonObject();
        JsonArray  eventsArr = new JsonArray();

        ctrl.addProperty("name", name);
        for (VocaloidPartPitchData.Event ev : events) {
            JsonObject e = new JsonObject();
            e.addProperty("pos",   ev.getPos());
            e.addProperty("value", ev.getValue());
            eventsArr.add(e);
        }
        ctrl.add("events", eventsArr);
        return ctrl;
    }

    private void writeVprZip(String outputPath) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputPath))) {
            zip.setMethod(ZipOutputStream.DEFLATED);
            zip.setLevel(0);

            // sequence.json
            ByteArrayOutputStream jsonBuf = new ByteArrayOutputStream();
            try (OutputStreamWriter w = new OutputStreamWriter(jsonBuf)) {
                gson.toJson(project, w);
            }
            zip.putNextEntry(new ZipEntry("Project/sequence.json"));
            zip.write(jsonBuf.toByteArray());
            zip.closeEntry();

            // Audio directory placeholder
            zip.putNextEntry(new ZipEntry("Project/Audio/"));
            zip.closeEntry();
        }
    }

    private void writeTrackBack(
            JsonObject part, JsonArray parts,
            JsonObject track, JsonArray tracks) {

        JsonArray updatedParts = new JsonArray();
        updatedParts.add(part);
        track.add("parts", updatedParts);

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
