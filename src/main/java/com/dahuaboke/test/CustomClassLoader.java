package com.dahuaboke.test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class CustomClassLoader extends ClassLoader {

    public static void main(String[] args) {
        try {
            String jarPath = "C:/Users/23195/Desktop/ttt/mvc.jar";
            File jarFile = new File(jarPath);
            URL url = jarFile.toURL();
            URL[] urls = new URL[]{url};
            CustomClassLoader customClassLoader = new CustomClassLoader(urls);
            // 假设加载一个类，全限定名为com.example.MyClass
            String classname = "com.dahuaboke.mvc.annotation.ValidComponent";
            Class<?> myClass = customClassLoader.loadClass(classname);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final URL[] urls;
    public CustomClassLoader(URL[] urls) {
        super();
        this.urls = urls;
    }
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (URL url : urls) {
            try {
                // 处理Spring Boot JAR结构中的BOOT-INF/classes目录
                if (url.getPath().endsWith(".jar")) {
                    JarFile jarFile = new JarFile(new File(url.getPath()));
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().startsWith("BOOT-INF/classes/") && entry.getName().endsWith(".class") && entry.getName().contains(name.replace('.', '/'))) {
                            try {
                                byte[] classData = loadClassData(jarFile.getInputStream(entry));
                                return defineClass(name, classData, 0, classData.length);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        throw new ClassNotFoundException("Class " + name + " not found.");
    }
    private byte[] loadClassData(java.io.InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
        while ((bytesRead = inputStream.read(buffer))!= -1) {
            byteStream.write(buffer, 0, bytesRead);
        }
        return byteStream.toByteArray();
    }
}