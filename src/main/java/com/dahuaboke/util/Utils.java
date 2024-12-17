package com.dahuaboke.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Utils {

    /**
     * 在jar包中查找文件，并返回文件流，未找到则返回null
     * @param jarPath jar包全路径名
     * @param fileName 要查找的文件名 例: Abc.xml  Bcd.class
     * @return 文件的输入流
     */
    public static InputStream findStreamInJar(String jarPath,String fileName) {
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(jarPath);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()){
            JarEntry jarEntry = entries.nextElement();
            String entryName = jarEntry.getName();
            entryName = entryName.substring(entryName.lastIndexOf("/") + 1);
            System.out.println(entryName);
            if(entryName.equals(fileName)){
                try {
                    return jarFile.getInputStream(jarEntry);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
        return null;
    }

}
