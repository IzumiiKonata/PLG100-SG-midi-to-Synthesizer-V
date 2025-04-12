package tech.konata.convert;

/**
 * @author IzumiiKonata
 * Date: 2025/4/12 08:25
 */
public abstract class ProjectConvertor {

    public abstract void load();

    public abstract void insertTempo(long tick, double bpm);

    public abstract void insertNote(String note, long tickStart, long tickEnd, int pitch);

    public abstract void save(String name);

}
