package com.dahuaboke.demo.annotation;

/**
 * 层级展示
 */
public enum ComponentLevel {

    LEVEL_ONE("aaa","bbb"),
    LEVEL_TWO("ccc","ddd");

    private String key;

    private String value;

    ComponentLevel(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
