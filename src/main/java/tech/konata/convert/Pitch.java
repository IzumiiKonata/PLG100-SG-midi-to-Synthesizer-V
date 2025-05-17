package tech.konata.convert;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * @author IzumiiKonata
 * Date: 2025/5/17 09:57
 */
@AllArgsConstructor
@Getter
public class Pitch {

    public List<Pair<Long, Double>> data;
    private boolean absolute;

}
