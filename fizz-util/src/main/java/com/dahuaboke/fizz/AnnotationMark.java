package com.dahuaboke.fizz;

import com.alibaba.fastjson2.JSON;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * author: dahua
 * date: 2024/12/23 14:43
 */
public class AnnotationMark {

    public List<Map<String, String>> markAnnotations(Fizz fizz, Map<String, String> annotations) {
        if (annotations == null) {
            return null;
        }
        List<Map<String, String>> result = new ArrayList<>();
        annotations.forEach((markAnnotation, name) -> {
            try {
                Map<String, String> map = new LinkedHashMap<>();
                Class<? extends Annotation> markClass = (Class<? extends Annotation>) Class.forName(markAnnotation);
                Set<Class<?>> tempMarkClasses = fizz.searchClassByAnnotation(markClass);
                map.put("name", name);
                HashSet<String> classes = new HashSet<>();
                for (Class<?> tempMarkClass : tempMarkClasses) {
                    classes.add(tempMarkClass.getName());
                }
                map.put("classes", JSON.toJSONString(classes));
                map.put("annotation", markAnnotation);
                map.put("size", String.valueOf(tempMarkClasses.size()));
                result.add(map);
            } catch (ClassNotFoundException e) {
            }
        });
        return result;
    }
}
