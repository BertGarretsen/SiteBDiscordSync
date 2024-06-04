package me.shurikennen;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Tuple<K, V> {

    private final K k;
    private final V v;

}
