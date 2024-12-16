package com.dahuaboke.test;

import com.dahuaboke.javaparser.annotation.ValidComponent;

import java.util.HashMap;
import java.util.Map;

@ValidComponent
public class Test1 {

    private Test2 test2 = new Test2();
    private Test3 test3 = new Test3();
    private Test4 test4 = new Test4();

    public void abc() {
        Map<String, String> map = new HashMap<>();
        map.put("a", "b");
        map.put("c", "d");
        map.entrySet().stream()
                .filter(a -> test4.abc(a.getKey()))
                .forEach(a -> {
                    test2.print(a.getKey());
                    test3.print(a.getValue());
                });

        map.forEach((k, v) -> {
            test2.print(k);
            test3.print(v);
        });
    }

    public void abc(String a) {
        Map<String, String> map = new HashMap<>();
        map.put("a", "b");
        map.put("c", "d");
        map.forEach((k, v) -> {
            test2.print(k);
            test3.print(v);
        });
    }

}
