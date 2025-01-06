package com.dahuaboke.fizz;

import com.alibaba.fastjson2.filter.PropertyFilter;

import java.util.List;
import java.util.Set;

public class Constants {

    public final static PropertyFilter FASTJSON_FILTER =
            (o, k, v) -> {
                if (o instanceof Fizz.Node) {
                    if (v == null) {
                        return false;
                    }
                    if (v instanceof List && ((List) v).isEmpty()) {
                        return false;
                    }
                    if (v instanceof Set && ((Set) v).isEmpty()) {
                        return false;
                    }
                    if ("feign".equals(k) || "mapper".equals(k)) {
                        if (v instanceof Boolean) {
                            if (!((Boolean) v).booleanValue()) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            };

    public enum SqlMap{

        //根据mapper中的id定义设计的表明
        SQLID_TABLES,
        //根据mapper涉及的ENTITY上的TableName标签定义的表名
        ENTITY_TABLES;

    }

}
