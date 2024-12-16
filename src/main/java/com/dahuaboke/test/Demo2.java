package com.dahuaboke.test;


import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.Launcher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Demo2 {

//    public static void main(String[] args) throws ClassNotFoundException, MalformedURLException {
//        String jarPath = "C:\\Users\\23195\\Desktop\\ttt\\mvc.jar";
//        URL url = new URL("file:" + jarPath);
//        URLClassLoader classLoader = new URLClassLoader(new URL[]{url});
//        String classname = "com.dahuaboke.mvc.annotation.ValidComponent";
//        URL[] urLs = classLoader.getURLs();
//
//        Class<?> springBootClass = classLoader.loadClass(classname);
//        System.out.println(springBootClass);
//    }

//    public static void main(String[] args) throws ClassNotFoundException, MalformedURLException {
//        String jarPath = "C:/Users/23195/Desktop/ttt/mvc.jar";
//        File jarFile = new File(jarPath);
//        System.out.println(jarFile.exists());
//        URL url = jarFile.toURL();
////        URL url = new URL("file:" + jarPath);
////        String classname = "com.dahuaboke.mvc.annotation.ValidComponent";
//        String classname = "org.springframework.context.annotation.Configuration";
//        List<URL> urls = new ArrayList<>();
//        urls.add(url);
//
//        // 创建LaunchedURLClassLoader实例来加载JAR包
//        LaunchedURLClassLoader classLoader = new LaunchedURLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
//
//        // 加载Spring Boot应用中的某个类（比如启动类或者其他业务类等，这里假设名为com.example.MyClass，需替换成实际类名）
//        Class<?> myClass = classLoader.loadClass(classname);
//        System.out.println("Loaded class: " + myClass.getName());
//    }

    public static void main(String[] args) throws Exception {
        try {
            String mainJarPath = "C:/Users/23195/Desktop/ttt/mvc.jar";
            List<URL> urlList = new ArrayList<>();
            // 添加主JAR包的URL
            urlList.add(new File(mainJarPath).toURL());
            // 读取主JAR包中的lib目录下的JAR文件
            JarFile mainJar = new JarFile(mainJarPath);
            Enumeration<JarEntry> entries = mainJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith("BOOT-INF/lib/")) {
                    String libJarPath = "jar:file:" + mainJarPath + "!/" + entry.getName();
                    urlList.add(new URL(libJarPath));
                }
            }
            // 转换为URL数组
            URL[] urls = urlList.toArray(new URL[0]);
            URLClassLoader classLoader = new URLClassLoader(urls);
            try {
                String classname = "com.dahuaboke.mvc.annotation.ValidComponent";
                // 假设要加载主JAR包中的com.example.MainClass类
                Class<?> loadedClass = classLoader.loadClass(classname);
                // 可以通过反射进一步操作这个类，如创建实例等
                loadedClass.getConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
