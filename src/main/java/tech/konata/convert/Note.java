package tech.konata.convert;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author IzumiiKonata
 * Date: 2025/5/17 09:52
 */
@Getter
@AllArgsConstructor
public class Note {

    private int key;
    private long tickOn, tickOff;
    private String lyric;

}
