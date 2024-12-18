package com.dahuaboke.fizz;


import java.io.IOException;

public class DoFizz {

    public static void main(String[] args) {
        //后续可以基于此名称进行相关操作，比如文件查找、验证等
        System.out.println("执行了fizz方法,start");
        String jarpath = "C:\\Users\\23195\\Desktop\\ttt\\demo\\demo.jar";
        System.out.println("打包后的jar文件路径: " + jarpath);
//        String annotationClass = "com.dahuaboke.mvc.annotation.ValidComponent";
        String annotationClass = "com.psbc.";
        String[] packages = {"com.psbc"};
        try {
            new Fizz("ifund", "1.0.0", null, annotationClass, null, packages).run();
        } catch (IOException e) {
            System.out.println(e);
        } catch (ClassNotFoundException e) {
            System.out.println(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("执行了fizz方法,end");
    }
}
