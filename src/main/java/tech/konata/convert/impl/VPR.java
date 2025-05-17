package tech.konata.convert.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.SneakyThrows;
import tech.konata.LyricsExtractor;
import tech.konata.convert.*;
import tech.konata.convert.pitch.VocaloidPitchConverter;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author IzumiiKonata
 * Date: 2025/4/12 08:29
 */
public class VPR extends ProjectConvertor {

    private JsonObject proj;

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void load() {
        InputStream is = LyricsExtractor.class.getResourceAsStream("/VOCALOID6_Project_Template.json");

        proj = gson.fromJson(new InputStreamReader(is), JsonObject.class);
    }

    @Override
    public void insertNote(String note, long tickStart, long tickEnd, int pitch) {

        if (note.length() > 1) {

            System.out.println("note.length() > 1");

            long duration = tickEnd - tickStart;

            char[] charArray = note.toCharArray();
            for (int i = 0; i < charArray.length; i++) {
                char c = charArray[i];

                insertNote(String.valueOf(c), tickStart + (duration / charArray.length) * i, tickEnd - (duration / charArray.length) * (charArray.length - (i + 1)), pitch);
            }

            return;
        }

        notes.add(new Note(pitch, tickStart, tickEnd, note));

        JsonArray tracks = proj.getAsJsonArray("tracks");

        JsonObject track = tracks.get(0).getAsJsonObject();

        JsonArray parts = track.getAsJsonArray("parts");

        JsonObject part = parts.get(0).getAsJsonObject();

        JsonArray notes = part.getAsJsonArray("notes");

        JsonObject objNote = new JsonObject();

        objNote.addProperty("lyric", note);
        objNote.addProperty("phoneme", "a");
        objNote.addProperty("langID", 0);
        objNote.addProperty("isProtected", false);
        objNote.addProperty("pos", tickStart);
        objNote.addProperty("duration", tickEnd - tickStart);
        objNote.addProperty("number", pitch);
        objNote.addProperty("velocity", 64);

        JsonObject exp = new JsonObject();
        exp.addProperty("accent", 50);
        exp.addProperty("decay", 50);
        exp.addProperty("bendDepth", 0);
        exp.addProperty("bendLength", 0);
        exp.addProperty("opening", 127);
        objNote.add("exp", exp);

        JsonObject aiExp = new JsonObject();
        aiExp.addProperty("pitchFine", 0.5);
        aiExp.addProperty("pitchDriftStart", 0.5);
        aiExp.addProperty("pitchDriftEnd", 0.5);
        aiExp.addProperty("pitchScalingCenter", 0.5);
        aiExp.addProperty("pitchScalingOrigin", 0.5);
        aiExp.addProperty("pitchTransitionStart", 0.5);
        aiExp.addProperty("pitchTransitionEnd", 0.5);
        aiExp.addProperty("amplitudeWhole", 0.5);
        aiExp.addProperty("amplitudeStart", 0.5);
        aiExp.addProperty("amplitudeEnd", 0.5);
        aiExp.addProperty("vibratoLeadingDepth", 0.5);
        aiExp.addProperty("vibratoFollowingDepth", 0.5);
        objNote.add("aiExp", aiExp);

        JsonObject singingSkill = new JsonObject();
        singingSkill.addProperty("duration", 160);

        JsonObject weight = new JsonObject();
        weight.addProperty("pre", 64);
        weight.addProperty("post", 64);
        singingSkill.add("weight", weight);

        objNote.add("singingSkill", singingSkill);

        JsonObject vibrato = new JsonObject();
        vibrato.addProperty("type", 0);
        vibrato.addProperty("duration", 320);

        JsonArray depths = new JsonArray();
        JsonObject depthsObj = new JsonObject();
        weight.addProperty("pos", 0);
        weight.addProperty("value", 0);
        depths.add(depthsObj);
        vibrato.add("depths", depths);

        JsonArray rates = new JsonArray();
        JsonObject ratesObj = new JsonObject();
        weight.addProperty("pos", 0);
        weight.addProperty("value", 0);
        rates.add(ratesObj);
        vibrato.add("rates", rates);

        objNote.add("vibrato", vibrato);

        notes.add(objNote);

        part.add("notes", notes);

        parts = new JsonArray();
        parts.add(part);

        track.add("parts", parts);

        tracks = new JsonArray();
        tracks.add(track);

        proj.add("tracks", tracks);
    }

    DecimalFormat df = new DecimalFormat("##.##");

    @Override
    public void insertTempo(long tick, double bpm) {

        JsonObject masterTrack = proj.getAsJsonObject("masterTrack");

        JsonObject tempo = masterTrack.getAsJsonObject("tempo");

        JsonArray events = tempo.getAsJsonArray("events");

        boolean eventsEmpty = events.isEmpty();

        JsonObject tempoEvent = new JsonObject();
        tempoEvent.addProperty("pos", tick);
        tempoEvent.addProperty("value", (int) (Double.parseDouble(df.format(bpm)) * 100));

        events.add(tempoEvent);

//        JsonObject global = tempo.getAsJsonObject("global");

        if (eventsEmpty) {
            JsonObject globalTempo = new JsonObject();
            tempoEvent.addProperty("isEnabled", false);
            tempoEvent.addProperty("value", (int) (Double.parseDouble(df.format(bpm)) * 100));
            tempo.add("global", globalTempo);
        }

        tempo.add("events", events);

        masterTrack.add("tempo", tempo);
        proj.add("masterTrack", masterTrack);

    }

    @Getter
    Pitch pitch;
    List<Pair<Long, Double>> pitchBendData = new ArrayList<>();

    @Override
    public void onPitchBend(int value, long tick) {
        pitchBendData.add(new Pair<>(tick, value / 768.0));
    }

    List<Note> notes = new ArrayList<>();

    @Override
    @SneakyThrows
    public void save(String name) {

        this.pitch = new Pitch(this.pitchBendData, false);

        VocaloidPitchConverter.VocaloidPartPitchData pitchRawData = VocaloidPitchConverter.generateForVocaloid(this.pitch, this.notes);

        if (pitchRawData != null) {
            JsonArray tracks = proj.getAsJsonArray("tracks");

            JsonObject track = tracks.get(0).getAsJsonObject();

            JsonArray parts = track.getAsJsonArray("parts");

            JsonObject part = parts.get(0).getAsJsonObject();

            JsonArray controllers = part.getAsJsonArray("controllers");

            if (!pitchRawData.getPbs().isEmpty()) {

                JsonObject pitchBendSensObj = new JsonObject();

                pitchBendSensObj.addProperty("name", "pitchBendSens");

                JsonArray events = new JsonArray();

                for (VocaloidPitchConverter.VocaloidPartPitchData.Event pbEvent : pitchRawData.getPbs()) {
                    JsonObject eventObj = new JsonObject();

                    eventObj.addProperty("pos", pbEvent.getPos());
                    eventObj.addProperty("value", pbEvent.getValue());

                    events.add(eventObj);
                }

                pitchBendSensObj.add("events", events);
                controllers.add(pitchBendSensObj);
            }

            if (!pitchRawData.getPit().isEmpty()) {

                JsonObject pitchBendObj = new JsonObject();

                pitchBendObj.addProperty("name", "pitchBend");

                JsonArray events = new JsonArray();

                for (VocaloidPitchConverter.VocaloidPartPitchData.Event pbEvent : pitchRawData.getPit()) {
                    JsonObject eventObj = new JsonObject();

                    eventObj.addProperty("pos", pbEvent.getPos());
                    eventObj.addProperty("value", pbEvent.getValue());

                    events.add(eventObj);
                }

                pitchBendObj.add("events", events);
                controllers.add(pitchBendObj);
            }

            part.add("controllers", controllers);

            parts = new JsonArray();
            parts.add(part);

            track.add("parts", parts);

            tracks = new JsonArray();
            tracks.add(track);

            proj.add("tracks", tracks);
        }

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(name + ".vpr"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos);
        gson.toJson(proj, writer);

        writer.flush();
        writer.close();

        zos.setMethod(ZipOutputStream.DEFLATED);
        zos.setLevel(0);

        zos.putNextEntry(new ZipEntry("Project/sequence.json"));

        zos.write(baos.toByteArray());
        zos.closeEntry();

        zos.putNextEntry(new ZipEntry("Project/Audio/"));
        zos.closeEntry();

        zos.flush();
        zos.close();

    }

}
