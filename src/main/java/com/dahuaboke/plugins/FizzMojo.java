package com.dahuaboke.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;


@Mojo(name = "fizz", requiresProject = false, defaultPhase = LifecyclePhase.PACKAGE)
public class FizzMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(required = true)
    private String annotationClass;

    @Parameter
    private String[] packages;

    @Override
    public void execute() {
        //后续可以基于此名称进行相关操作，比如文件查找、验证等
        Log log = getLog();
        log.info("执行了fizz方法,start");
        String jarpath = project.getBuild().getDirectory() + "/" + project.getBuild().getFinalName() + ".jar";
        System.out.println("打包后的jar文件路径: " + jarpath);
        try {
            new Fizz(jarpath,annotationClass,packages,log).run();
        } catch (IOException e) {
            log.error(e);
        } catch (ClassNotFoundException e) {
            log.error(e);
        } catch (Exception e) {
            log.error(e);
        }
        log.info("执行了fizz方法,end");
    }

}
