package vn.pmgteam.lotus2d.core.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LotusPlugin {
    String name();
    String author() default "Unknown";
    String version() default "0.0.1";
    boolean isBackground() default true; // Phân loại tầng render
}