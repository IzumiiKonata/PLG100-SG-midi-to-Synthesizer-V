package tech.konata.convert.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import tech.konata.LyricsExtractor;
import tech.konata.convert.ProjectConvertor;

import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author IzumiiKonata
 * Date: 2025/4/12 08:26
 */
public class SVP extends ProjectConvertor {

    private final long TICK_RATE = 1470000L;

    private JsonObject proj;

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void load() {

        InputStream is = LyricsExtractor.class.getResourceAsStream("/SynthesizerV_Project_Template.json");

        proj = gson.fromJson(new InputStreamReader(is), JsonObject.class);

    }

    public void insertTempo(long tick, double bpm) {
        JsonObject time = proj.getAsJsonObject("time");

        JsonArray tempo = time.getAsJsonArray("tempo");

        JsonObject obj = new JsonObject();
        obj.addProperty("position", tick * TICK_RATE);
        obj.addProperty("bpm", bpm);

        tempo.add(obj);

        time.add("tempo", tempo);
        proj.add("time", time);
    }

    public void insertNote(String note, long tickStart, long tickEnd, int pitch) {

        JsonArray tracks = proj.getAsJsonArray("tracks");

        JsonObject jObj = tracks.get(0).getAsJsonObject();

        JsonObject mainGroup = jObj.getAsJsonObject("mainGroup");
        JsonArray notes = mainGroup.getAsJsonArray("notes");

        JsonObject n = new JsonObject();

        n.addProperty("musicalType", "singing");
        n.addProperty("onset", tickStart * TICK_RATE);
        n.addProperty("duration", (tickEnd - tickStart) * TICK_RATE);
        n.addProperty("lyrics", note);
        n.addProperty("phonemes", "");
        n.addProperty("accent", "");
        n.addProperty("pitch", pitch);
        n.addProperty("detune", 0);
        n.addProperty("instantMode", true);

        JsonObject attributes = new JsonObject();
        n.add("attributes", attributes);

        JsonObject systemAttributes = new JsonObject();
        systemAttributes.addProperty("tF0Offset", -0.0);
        systemAttributes.addProperty("tF0Left", 0.05000000074505806);
        systemAttributes.addProperty("tF0Right", 0.05000000074505806);
        systemAttributes.addProperty("dF0Left", 0.0);
        systemAttributes.addProperty("dF0Right", 0.0);
        systemAttributes.addProperty("dF0Vbr", 0.0);
        n.add("systemAttributes", systemAttributes);

        JsonObject pitchTakes = new JsonObject();
        pitchTakes.addProperty("activeTakeId", 0);

        JsonArray takes = new JsonArray();

        JsonObject obj = new JsonObject();

        obj.addProperty("id", 0);
        obj.addProperty("expr", 1.0);
        obj.addProperty("liked", false);

        takes.add(obj);
        pitchTakes.add("takes", takes);

        n.add("pitchTakes", pitchTakes);

        JsonObject timbreTakes = new JsonObject();
        timbreTakes.addProperty("activeTakeId", 0);
        timbreTakes.addProperty("expr", 1.0);

        timbreTakes.add("takes", takes);

        n.add("timbreTakes", timbreTakes);

        notes.add(n);

        mainGroup.add("notes", notes);
        jObj.add("mainGroup", mainGroup);

        tracks = new JsonArray();
        tracks.add(jObj);

        proj.add("tracks", tracks);

    }

    @SneakyThrows
    public void save(String name) {
        FileWriter writer = new FileWriter(name + ".svp");
        gson.toJson(proj, writer);

        writer.flush();
        writer.close();
    }


}
