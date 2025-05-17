package tech.konata.convert;

/**
 * @author IzumiiKonata
 * Date: 2025/5/17 10:15
 */ // Helper Pair class (需要自行实现)
public class Pair<A, B> {
    public final A first;
    public final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    // 需要实现equals和hashCode
}
