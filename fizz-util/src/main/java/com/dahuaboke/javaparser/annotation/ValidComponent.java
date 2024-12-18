package com.dahuaboke.javaparser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidComponent {

    ComponentLevel level() default ComponentLevel.LEVEL_ONE;

    String description() default "";


}