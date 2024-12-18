package com.dahuaboke.demo.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Demo {

    public static void main(String[] args) {
        String jarPath = "C:\\Users\\23195\\Desktop\\ttt\\general-main\\mvc\\target\\mvc.jar";
        MyClassLoader myClassLoader = new MyClassLoader();
        try {
            Class<?> springBootClass = myClassLoader.loadClass("com.dahuaboke.mvc.annotation.ValidComponent");
            // 可以通过反射来启动Spring Boot应用
            Method mainMethod = springBootClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    static class MyClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            // 首先检查是否已经加载过这个类
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    // 尝试从自定义的位置加载类
                    c = findClass(name);
                } catch (ClassNotFoundException e) {
                    // 如果在自定义位置找不到，委托给父类加载器
                    c = super.loadClass(name);
                }
            }
            return c;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            // 在这里实现从特定位置（如自定义的JAR文件）加载类的逻辑
            // 例如，可以使用字节流读取JAR中的类文件字节码并定义类
            // 以下是一个简单的示例，实际应用中需要更复杂的操作
            byte[] classData = null;
            // 假设从某个自定义JAR文件读取字节码，这里省略读取逻辑
//            try {
//                JarFile jarFile = new JarFile(new File("C:\\Users\\23195\\Desktop\\ttt\\general-main\\mvc\\target\\mvc.jar"));
//                jarFile.
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
            if (classData == null) {
                throw new ClassNotFoundException();
            } else {
                return defineClass(name, classData, 0, classData.length);
            }
        }
    }

}
