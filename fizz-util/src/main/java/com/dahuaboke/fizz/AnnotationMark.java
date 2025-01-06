package com.dahuaboke.fizz;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * author: dahua
 * date: 2024/12/23 14:43
 */
public class AnnotationMark {

    public List<Map<String, Object>> markAnnotations(Fizz fizz, Map<String, String> annotations) {
        if (annotations == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        annotations.forEach((markAnnotation, name) -> {
            try {
                Map map = new LinkedHashMap<>();
                Class<? extends Annotation> markClass = (Class<? extends Annotation>) Class.forName(markAnnotation);
                Set<Class<?>> tempMarkClasses = fizz.searchClassByAnnotation(markClass);
                map.put("name", name);
                Map<String, Object> classes = new LinkedHashMap<>();
                for (Class<?> targetClass : tempMarkClasses) {
                    String targetClassName = targetClass.getName();
                    classes.put(targetClassName, "");
                    Map<String, String> annotationsData = fizz.parseAnnotationMetadata(targetClass, markClass);
                    if (annotationsData == null) {
                        continue;
                    }
                    classes.put(targetClassName, annotationsData);
                }
                map.put("classes", classes);
                map.put("annotation", markAnnotation);
                map.put("size", tempMarkClasses.size());
                result.add(map);
            } catch (Exception e) {
            }
        });
        return result;
    }
}
