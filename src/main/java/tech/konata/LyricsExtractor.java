package tech.konata;

import tech.konata.convert.ProjectConverter;
import tech.konata.convert.impl.SVP;
import tech.konata.convert.impl.VPR;
import tech.konata.parser.MidiParser;
import tech.konata.parser.SgData;
import tech.konata.parser.SgTableLoader;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class LyricsExtractor {

    private static final String DEFAULT_MIDI_PATH = "D:\\MidiTest\\tokinona.MID";

    public static void main(String[] args) {
        String midiPath = (args.length > 0) ? args[0] : DEFAULT_MIDI_PATH;
        new LyricsExtractor().run(new File(midiPath));
    }

    /**
     * Loads the SG table, initialises all converters, parses the MIDI file, and
     * saves the resulting project files.
     *
     * @param midiFile the PLG100-SG MIDI file to convert
     */
    public void run(File midiFile) {
        List<SgData> sgTable = SgTableLoader.load();

        // initialize converters
        List<ProjectConverter> converters = Arrays.asList(new SVP(), new VPR());
        converters.forEach(ProjectConverter::load);

        // parse the MIDI file and dispatch events to every converter
        MidiParser parser = new MidiParser(converters, sgTable);
        parser.parse(midiFile);

        // save each converter's output
        String baseName = stripExtension(midiFile.getName());
        converters.forEach(c -> c.save(baseName));

        System.out.println("Conversion complete: " + baseName);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(0, dot) : filename;
    }
}
