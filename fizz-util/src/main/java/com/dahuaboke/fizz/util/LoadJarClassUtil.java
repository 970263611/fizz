package com.dahuaboke.fizz.util;

import org.objectweb.asm.ClassReader;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 动态加载jar或者class
 *
 * @author JustryDeng
 * @since 2021/6/17 0:31:53
 */
public final class LoadJarClassUtil {

    private static final String JAR_SUFFIX = ".jar";
    private static final int JAR_SUFFIX_LENGTH = ".jar".length();
    private static final String CLASS_SUFFIX = ".class";
    private static final String TMP_DIR_SUFFIX = "__temp__";
    private static final int CLASS_SUFFIX_LENGTH = CLASS_SUFFIX.length();

    /**
     * 添加资源的方法
     */
    private Method addUrlMethod;

    /**
     * 类加载器
     */
    private URLClassLoader classLoader;

    /**
     * 类对应类文件流
     */
    private Map<String, ClassReader> CLASSES_FILE = new HashMap<>();

    /**
     * 实例化的类
     */
    private Set<Class<?>> classInstanceSet = new HashSet<>();

    private Map<String,Class<?>> classInstanceMap = new HashMap<>();

    private String jarPath;

    private String[] includePrefixArr;

    private String[] excludePrefixArr;

    public LoadJarClassUtil(String jarPath,String[] includePrefixArr,String[] excludePrefixArr){
        try {
            addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrlMethod.setAccessible(true);
            classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            this.jarPath = jarPath;
            this.includePrefixArr = includePrefixArr;
            this.excludePrefixArr = excludePrefixArr;
            loadJar(new File(jarPath),
                    this.includePrefixArr == null ? null : Arrays.stream(includePrefixArr).collect(Collectors.toSet()),
                    this.excludePrefixArr == null ? null : Arrays.stream(excludePrefixArr).collect(Collectors.toSet()));
            classInstanceSet.forEach(a -> {
                classInstanceMap.put(a.getName(),a);
            });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 加载指定的jar文件中的所有class（或： 加载指定目录(含其子孙目录)下的所有jar文件中的所有class）
     * <p>
     * 注:普通的jar包与spring-boot jar包都支持。
     *
     * @param jarOrDirFile     要加载的jar文件(或jar文件所在的目录)
     *                         <br/>
     *                         注：如果jarOrDir是目录，那么该目录包括其子孙目录下的所有jar都会被加载。
     * @param includePrefixSet 当通过前缀控制是否实例化Class对象
     *                         <br />
     *                         注: 若includePrefixSet为null或者为空集合，那么默认实例化所有的class
     * @param excludePrefixSet 通过前缀控制是否排除实例化Class对象
     *                         <br />
     *                         注: excludePrefixSet优先级高于includePrefixSet。
     * @return 已加载了的class实例集合
     */
    public void loadJar(File jarOrDirFile,
                        Set<String> includePrefixSet,
                        Set<String> excludePrefixSet) {
        List<File> jarFileList = IOUtil.listFileOnly(jarOrDirFile, JAR_SUFFIX);
        List<File> bootJarFileList = new ArrayList<>(16);
        List<File> normalJarFileList = new ArrayList<>(16);
        jarFileList.forEach(jar -> {
            if (isBootJar(jar)) {
                bootJarFileList.add(jar);
            } else {
                normalJarFileList.add(jar);
            }
        });
        classInstanceSet.addAll(loadBootJar(bootJarFileList, includePrefixSet, excludePrefixSet));
        classInstanceSet.addAll(loadNormalJar(normalJarFileList, true, includePrefixSet, excludePrefixSet));
    }

    /**
     * 加载指定路径下所有class文件
     *
     * @param classLongNameRootDirSet classLongNameRootDir集合，
     *                                其中classLongNameRootDir为顶级包的父目录 <br/>
     *                                举例说明:
     *                                假设,现有结构/dir1/dir2/com/aaa/bbb/ccc/Qwer.class, 其中Qwer的全类名为 com.aaa.bbb.ccc.Qwer
     *                                那么，在这里面，顶级包就是com, classLongNameRootDir就应该是/dir1/dir2/
     * @param includePrefixSet        通过前缀控制是否实例化Class对象
     *                                <br />
     *                                注: 若includePrefixSet为null或者为空集合，那么默认实例化所有的class
     * @param excludePrefixSet        通过前缀控制是否排除实例化Class对象
     *                                <br />
     *                                注: excludePrefixSet优先级高于includePrefixSet。
     * @return 已加载了的class实例集合
     */
    public Set<Class<?>> loadClass(Set<File> classLongNameRootDirSet,
                                   Set<String> includePrefixSet,
                                   Set<String> excludePrefixSet) {

        if (classLongNameRootDirSet == null || classLongNameRootDirSet.size() == 0) {
            //System.out.println(("classLongNameRootDirSet is empty.");
            return new HashSet<>();
        }
        classLongNameRootDirSet = classLongNameRootDirSet.stream()
                .filter(x -> x.exists() && x.isDirectory())
                .collect(Collectors.toSet());
        if (classLongNameRootDirSet.isEmpty()) {
            //System.out.println(("Valid classLongNameRootDir is empty.");
            return new HashSet<>();
        }
        // 加载
        classLongNameRootDirSet.forEach(classLongNameRootDir -> {
            try {
                addUrlMethod.invoke(classLoader, classLongNameRootDir.toURI().toURL());
            } catch (IllegalAccessException | InvocationTargetException | MalformedURLException e) {
                throw new RuntimeException(e);
            }
        });
        // (去重)采集所有类全类名
        Set<String> classLongNameSet = new HashSet<>();
        classLongNameRootDirSet.forEach(classLongNameRootDir -> {
            int classLongNameStartIndex = classLongNameRootDir.getAbsolutePath().length() + 1;
            List<File> classFileList = IOUtil.listFileOnly(classLongNameRootDir, CLASS_SUFFIX);
            classLongNameSet.addAll(classFileList.stream()
                    .map(classFile -> {
                        String absolutePath = classFile.getAbsolutePath();
                        // 形如: com/aaa/bbb/ccc/Qwer
                        String classLongPath = absolutePath.substring(classLongNameStartIndex,
                                absolutePath.length() - CLASS_SUFFIX_LENGTH);
                        String className = classLongPath.replace('\\', '.').replace("/", ".");
                        try(FileInputStream fis = new FileInputStream(classFile)) {
                            CLASSES_FILE.put(className, new ClassReader(fis));
                        } catch (Exception e) {}
                        return className;
                    }).filter(classLongName -> {
                        if (excludePrefixSet != null && excludePrefixSet.size() > 0) {
                            if (excludePrefixSet.stream().anyMatch(classLongName::startsWith)) {
                                return false;
                            }
                        }
                        if (includePrefixSet != null && includePrefixSet.size() > 0) {
                            return includePrefixSet.stream().anyMatch(classLongName::startsWith);
                        }
                        return true;
                    })
                    .collect(Collectors.toSet())
            );
        });
        // 转换为class实例
        return classLongNameSet.stream()
                .map(this::createClassInstance)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 加载(spring-boot打包出来的)jar文件(中的所有class)
     * <p>
     * 注: jar文件中，BOOT-INF/lib目录(含其子孙目录)下的所有jar文件，会被当做normal-jar，也一并进行加载。
     * 注: jar文件中其余位置的jar文件（如果有的话）不会被加载.
     *
     * @param jarFileList      要加载的jar文件集合
     * @param includePrefixSet 通过前缀控制是否实例化Class对象
     *                         <br />
     *                         注: 若includePrefixSet为null或者为空集合，那么默认实例化所有的class
     * @param excludePrefixSet 通过前缀控制是否排除实例化Class对象
     *                         <br />
     *                         注: excludePrefixSet优先级高于includePrefixSet。
     * @return 已加载了的class文件全类名集合
     */
    private Set<Class<?>> loadBootJar(List<File> jarFileList,
                                      Set<String> includePrefixSet,
                                      Set<String> excludePrefixSet) {
        Set<Class<?>> classInstanceSet = new HashSet<>();
        if (jarFileList == null || jarFileList.size() == 0) {
            return classInstanceSet;
        }
        verifyJarFile(jarFileList);

        Set<File> bootClassRootDirSet = new HashSet<>();
        Set<File> bootLibSet = new HashSet<>();
        Set<File> tmpDirSet = new HashSet<>();
        for (File file : jarFileList) {
            String absolutePath = file.getAbsolutePath();
            String tmpDir = absolutePath.substring(0, absolutePath.length() - JAR_SUFFIX_LENGTH) + TMP_DIR_SUFFIX;
            // 记录临时目录
            tmpDirSet.add(new File(tmpDir));
            JarUtil.unJarWar(absolutePath, tmpDir);
            // 记录bootClassRootDir
            File f = new File(tmpDir, "BOOT-INF/classes");
            bootClassRootDirSet.add(f);
            // 记录bootLib
            List<File> libs = IOUtil.listFileOnly(new File(tmpDir, "BOOT-INF/lib"), JAR_SUFFIX);
            bootLibSet.addAll(libs);
        }

        // 加载BOOT-INF/lib/下的.jar
        classInstanceSet.addAll(loadNormalJar(new ArrayList<>(bootLibSet), true, includePrefixSet, excludePrefixSet));
        // 加载BOOT-INF/classes/下的.class
        bootClassRootDirSet.forEach(bootClassRootDir -> {
            Set<File> tmpSet = new HashSet<>();
            tmpSet.add(bootClassRootDir);
            classInstanceSet.addAll(loadClass(tmpSet, includePrefixSet, excludePrefixSet));
            // 删除BOOT-INF目录
            IOUtil.delete(bootClassRootDir.getParentFile());
        });
        // 加载jar中与BOOT-INF平级的其他类
        bootClassRootDirSet.forEach(bootClassRootDir -> {
                    Set<File> tmpSet = new HashSet<>();
                    tmpSet.add(bootClassRootDir.getParentFile().getParentFile());
                    classInstanceSet.addAll(
                            loadClass(tmpSet, includePrefixSet, excludePrefixSet)
                    );
                }
        );
        // 删除临时目录
        tmpDirSet.forEach(IOUtil::delete);
        return classInstanceSet;
    }

    /**
     * 加载(普通)jar文件(中的所有class)
     * <p>
     * 注: jar文件中若包含其他的的jar文件，其他的jar文件里面的class是不会被加载的。
     *
     * @param jarFileList      要加载的jar文件集合
     * @param instanceClass    是否实例化Class对象
     * @param includePrefixSet 当instanceClass为true时， 通过前缀控制是否实例化Class对象
     *                         <br />
     *                         注: 若includePrefixSet为null或者为空集合，那么默认实例化所有的class
     * @param excludePrefixSet 当instanceClass为true时， 通过前缀控制是否排除实例化Class对象
     *                         <br />
     *                         注: excludePrefixSet优先级高于includePrefixSet。
     * @return 已加载了的class集合
     */
    private Set<Class<?>> loadNormalJar(List<File> jarFileList,
                                        boolean instanceClass,
                                        Set<String> includePrefixSet,
                                        Set<String> excludePrefixSet) {
        Set<Class<?>> classInstanceSet = new HashSet<>();
        if (jarFileList == null || jarFileList.size() == 0) {
            return classInstanceSet;
        }
        verifyJarFile(jarFileList);
        try {
            for (File jar : jarFileList) {
                URL url = jar.toURI().toURL();
                addUrlMethod.invoke(classLoader, url);
                if (!instanceClass) {
                    continue;
                }
                ZipFile zipFile = null;
                try {
                    zipFile = new ZipFile(jar);
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry zipEntry = entries.nextElement();
                        String zipEntryName = zipEntry.getName();
                        if (!zipEntryName.endsWith(CLASS_SUFFIX)) {
                            continue;
                        }
                        String classLongName = zipEntryName
                                .substring(0, zipEntryName.length() - CLASS_SUFFIX_LENGTH)
                                .replace("/", ".");
                        if (excludePrefixSet != null && excludePrefixSet.size() > 0) {
                            if (excludePrefixSet.stream().anyMatch(classLongName::startsWith)) {
                                continue;
                            }
                        }
                        if (includePrefixSet != null && includePrefixSet.size() > 0) {
                            if (includePrefixSet.stream().noneMatch(classLongName::startsWith)) {
                                continue;
                            }
                        }
                        try(InputStream is = classLoader.getResourceAsStream(zipEntryName)){
                            CLASSES_FILE.put(classLongName, new ClassReader(is));
                        } catch (Exception e) {}
                        //TODO
//                        Class<?> instance = createClassInstance(classLongName);
//                        if (instance != null) {
//                            classInstanceSet.add(instance);
//                        }
                    }
                } finally {
                    IOUtil.close(zipFile);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return classInstanceSet;
    }

    /**
     * 校验jar文件合法性(存在 && 是.jar后缀的文件)
     *
     * @param jarFileList 要校验的jar文件
     */
    private void verifyJarFile(List<File> jarFileList) {
        Objects.requireNonNull(jarFileList, "jarFileList cannot be empty.");
        jarFileList.forEach(file -> {
            if (!file.exists()) {
                throw new IllegalArgumentException("file [" + file.getAbsolutePath() + "] non-exist.");
            }
            if (!file.getName().endsWith(JAR_SUFFIX)) {
                throw new IllegalArgumentException("file [" + file.getAbsolutePath() + "] is not a jar file.");
            }
        });
    }

    /**
     * 根据全类名创建class实例
     *
     * @param classLongName 全类名
     * @return class实例(注 : 当创建异常时 ， 返回null)
     */
    private Class<?> createClassInstance(String classLongName) {
        Class<?> instance = null;
        try {
            instance = Class.forName(classLongName);
        } catch (Throwable e) {
        }
        return instance;
    }

    /**
     * 判断jar文件是否是boot-jar文件
     *
     * @param jar 带判断的jar文件
     * @return true-是boot-jar, false-普通jar
     */
    private boolean isBootJar(File jar) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(jar);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String zipEntryName = zipEntry.getName();
                if (zipEntryName.startsWith("BOOT-INF/classes")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtil.close(zipFile);
        }
    }

    /**
     * IO工具类
     *
     * @author JustryDeng
     * @since 2021/4/23 20:40:47
     */
    @SuppressWarnings("AlibabaClassNamingShouldBeCamel")
    private static final class IOUtil {

        /**
         * 只罗列文件(即：只返回文件)
         * <p>
         * 注：dirOrFile对象本身也会被作为罗列对象。
         * </p>
         *
         * @param dirOrFile 要罗列的文件夹(或者文件)
         * @param suffix    要筛选的文件的后缀(若suffix为null， 则不作筛选)
         * @return 罗列结果
         */
        public static List<File> listFileOnly(File dirOrFile, String... suffix) {
            if (!dirOrFile.exists()) {
                throw new IllegalArgumentException("listFileOnly [" + dirOrFile.getAbsolutePath() + "] non exist.");
            }
            return listFile(dirOrFile, 1).stream()
                    .filter(file -> {
                        if (suffix == null) {
                            return true;
                        }
                        String fileName = file.getName();
                        return Arrays.stream(suffix).anyMatch(fileName::endsWith);
                    }).collect(Collectors.toList());
        }

        /**
         * 罗列所有文件文件夹
         * <p>
         * 注：dirOrFile对象本身也会被作为罗列对象。
         * </p>
         *
         * @param dirOrFile 要罗列的文件夹(或者文件)
         * @param mode      罗列模式(0-罗列文件和文件夹； 1-只罗列文件； 2-只罗列文件夹)
         * @return 罗列结果
         */
        public static List<File> listFile(File dirOrFile, int mode) {
            List<File> fileContainer = new ArrayList<>(16);
            listFile(dirOrFile, fileContainer, mode);
            return fileContainer;
        }

        /**
         * 罗列所有文件文件夹
         * <p>
         * 注：dirOrFile对象本身也会被作为罗列对象。
         * </p>
         *
         * @param dirOrFile     要罗列的文件夹(或者文件)
         * @param fileContainer 罗列结果
         * @param mode          罗列模式(0-罗列文件和文件夹； 1-只罗列文件； 2-只罗列文件夹)
         */
        public static void listFile(File dirOrFile, List<File> fileContainer, int mode) {
            if (!dirOrFile.exists()) {
                return;
            }
            int onlyDirMode = 2;
            if (mode != 0 && mode != 1 && mode != onlyDirMode) {
                throw new IllegalArgumentException("mode [" + mode + "] is non-supported. 0,1,2is only support.");
            }
            if (dirOrFile.isDirectory()) {
                File[] files = dirOrFile.listFiles();
                if (files != null) {
                    for (File f : files) {
                        listFile(f, fileContainer, mode);
                    }
                }
                if (mode == 0 || mode == onlyDirMode) {
                    fileContainer.add(dirOrFile);
                }
            } else {
                if (mode == 0 || mode == 1) {
                    fileContainer.add(dirOrFile);
                }
            }
        }

        /**
         * 将srcFileBytes写出为destFile文件
         * <p>
         * 注: 若源文件存在，则会覆盖原有的内容。
         * </p>
         *
         * @param srcFileBytes      字节
         * @param destFile          文件
         * @param createIfNecessary 如果需要的话，创建文件
         */
        public static void toFile(byte[] srcFileBytes, File destFile, boolean createIfNecessary) {
            OutputStream os = null;
            try {
                if (destFile.isDirectory()) {
                    throw new RuntimeException("destFile [" + destFile.getAbsolutePath() + "] must be file rather than dir.");
                }

                if (createIfNecessary && !destFile.exists()) {
                    File parentFile = destFile.getParentFile();
                    if (!parentFile.exists() || !parentFile.isDirectory()) {
                        /*
                         * 进入此if，即代表parentFile存在，且为file, 而我们又需要创建一个同名的文件夹。
                         * 如果系统不支持创建与文件同名(大小写不敏感)的文件夹的话，那么创建结果为false
                         */
                        boolean mkdirs = parentFile.mkdirs();
                        if (!mkdirs) {
                            // step0. 将与与文件夹名冲突的文件重命名为：原文件名_时间戳
                            Arrays.stream(Objects.requireNonNull(parentFile.getParentFile().listFiles()))
                                    .filter(file -> file.getName().equalsIgnoreCase(parentFile.getName())).findFirst()
                                    .ifPresent(conflictFile -> {
                                        String renameFilePath =
                                                conflictFile.getAbsolutePath() + "_" + System.currentTimeMillis();
                                        boolean renameResult = conflictFile.renameTo(new File(renameFilePath));
                                        //System.out.println(("rename file [" + conflictFile.getAbsolutePath() + "] to ["
//                                                + renameFilePath + "] " + (renameResult ? "success" : "fail") + ".");
                                    });
                            // step1. 再次创建文件夹
                            mkdirs = parentFile.mkdirs();
                            if (!mkdirs) {
                                //System.out.println(("create dir [" + parentFile.getAbsolutePath() + "] fail.");
                            }
                        }
                    }
                    //noinspection ResultOfMethodCallIgnored
                    destFile.createNewFile();
                } else if (!destFile.exists()) {
                    throw new IllegalArgumentException("destFile [" + destFile.getAbsolutePath() + "] non exist.");
                }
                os = new FileOutputStream(destFile);
                os.write(srcFileBytes, 0, srcFileBytes.length);
                os.flush();
            } catch (IOException e) {
                throw new RuntimeException(" toFile [" + destFile.getAbsolutePath() + "] occur exception.", e);
            } finally {
                close(os);
            }
        }

        /**
         * 将inputStream转换为byte[]
         * <p>
         * 注：此方法会释放inputStream
         * </p>
         *
         * @param inputStream 输入流
         * @return 字节
         */
        public static byte[] toBytes(InputStream inputStream) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[4096];
                int n;
                while (-1 != (n = inputStream.read(buffer))) {
                    output.write(buffer, 0, n);
                }
                return output.toByteArray();
            } finally {
                close(output, inputStream);
            }
        }

        /**
         * 删除文件/文件夹
         *
         * @param dirOrFile 要删的除文件/文件夹
         */
        public static void delete(File dirOrFile) {
            if (!dirOrFile.exists()) {
                return;
            }
            if (dirOrFile.isFile()) {
                boolean success = dirOrFile.delete();
                if (!success) {
                    System.out.println(("delete file [" + dirOrFile.getAbsolutePath() + "] fail."));
                }
            } else {
                File[] files = dirOrFile.listFiles();
                if (files != null) {
                    for (File f : files) {
                        delete(f);
                    }
                }
            }
            //noinspection ResultOfMethodCallIgnored
            dirOrFile.delete();
        }

        /**
         * 关闭流
         *
         * @param ioArr 待关闭的io
         */
        public static void close(Closeable... ioArr) {
            if (ioArr == null) {
                return;
            }
            for (Closeable io : ioArr) {
                if (io == null) {
                    continue;
                }
                try {
                    io.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * jar/war操作工具类
     *
     * @author JustryDeng
     * @since 2021/4/25 21:58:52
     */
    private static final class JarUtil {

        /**
         * 解压jar(or war)至指定的目录
         *
         * @see JarUtil#unJarWar(String, String, boolean, Collection)
         */
        public static <T extends Collection<String>> List<String> unJarWar(String jarWarPath, String targetDir) {
            return unJarWar(jarWarPath, targetDir, true, null);
        }

        /**
         * 解压jar(or war)至指定的目录
         *
         * @param jarWarPath                    待解压的jar(or war)文件
         * @param targetDir                     解压后文件放置的文件夹
         * @param delOldTargetDirIfAlreadyExist 若targetDir已存在，是否先将原来的targetDir进行删除
         * @param entryNamePrefixes             只有当entryName为指定的前缀时，才对该entry进行解压(若为null或者长度为0， 则解压所有文件)   如: ["BOOT-INF/classes/", "BOOT-INF/classes/com/example/ssm/author/JustryDeng.class"]
         *                                      <br/>
         *                                      注:当entry对应jar或者war中的目录时，那么其值形如 BOOT-INF/classes/
         *                                      <br/>
         *                                      注:当entry对应jar或者war中的文件时，那么其值形如 BOOT-INF/classes/com/example/ssm/author/JustryDeng.class
         * @return 解压出来的文件(包含目录)的完整路径
         */
        public static <T extends Collection<String>> List<String> unJarWar(String jarWarPath, String targetDir,
                                                                           boolean delOldTargetDirIfAlreadyExist,
                                                                           T entryNamePrefixes) {
            List<String> list = new ArrayList<>();
            File target = new File(targetDir);
            if (delOldTargetDirIfAlreadyExist) {
                IOUtil.delete(target);
            }
            guarantyDirExist(target);

            ZipFile zipFile = null;
            try {
                zipFile = new ZipFile(new File(jarWarPath));
                ZipEntry entry;
                File targetFile;
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    entry = entries.nextElement();
                    String entryName = entry.getName();
                    // 若entryNamePrefixes不为空，则不解压前缀不匹配的文件或文件夹
                    if (entryNamePrefixes != null && entryNamePrefixes.size() > 0
                            && entryNamePrefixes.stream().noneMatch(entryName::startsWith)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        targetFile = new File(target, entryName);
                        guarantyDirExist(targetFile);
                    } else {
                        // 有时遍历时，文件先于文件夹出来，所以也得保证目录存在
                        int lastSeparatorIndex = entryName.lastIndexOf("/");
                        if (lastSeparatorIndex > 0) {
                            guarantyDirExist(new File(target, entryName.substring(0, lastSeparatorIndex)));
                        }
                        // 解压文件
                        targetFile = new File(target, entryName);
                        byte[] bytes = IOUtil.toBytes(zipFile.getInputStream(entry));
                        IOUtil.toFile(bytes, targetFile, true);
                        list.add(targetFile.getAbsolutePath());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                IOUtil.close(zipFile);
            }
            return list;
        }

        /**
         * 保证目录存在
         *
         * @param dir 目录
         */
        public static void guarantyDirExist(File dir) {
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
        }
    }

    /**
     * 获取文件流
     * @param name
     * @return
     */
    public ClassReader getClassReader(String name) {
        return CLASSES_FILE.get(name.replaceAll("/", "\\."));
    }

    /**
     * 获取类解析器
     * @return
     */
    public URLClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * 获取所有的Mapper
     * @return
     */
    public Set<Class<?>> findMapperSet() {
        Set<Class<?>> set = new HashSet<>();
        //查找是否有mapperScan注解
        Class<? extends Annotation> mapperScanAnno;
        try {
            mapperScanAnno = (Class<? extends Annotation>) classLoader.loadClass("org.mybatis.spring.annotation.MapperScan");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        //如果有mapperScan注解，看看这个注解有没有被用到
        Set<String> mapperPath = new HashSet<>();
        if (mapperScanAnno != null) {
            ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.addClassLoaders(this.classLoader);
            if(this.includePrefixArr == null || includePrefixArr.length == 0){
                builder.forPackages("/");
            }else{
                builder.forPackages(this.includePrefixArr);
            }
            Reflections reflections = new Reflections(builder);
            Set<Class<?>> mapperScanList = reflections.getTypesAnnotatedWith(mapperScanAnno);
            //如果mapperScan注解有被用到，获取mapperScan注解中的path，并放到mapperPath中
            if (mapperScanList != null) {
                for (Class<?> clz : mapperScanList) {
                    Annotation annotation = clz.getAnnotation(mapperScanAnno);
                    try {
                        if (annotation != null) {
                            Method value = annotation.annotationType().getDeclaredMethod("value");
                            value.setAccessible(true);
                            Object obj = value.invoke(annotation);
                            if (obj != null) {
                                String[] s = (String[]) obj;
                                mapperPath.addAll(Arrays.stream(s).collect(Collectors.toSet()));
                            }
                        }
                    }catch (Exception e) {}
                }
            }
        }
        //看看有没有mapper注解
        Class<? extends Annotation> mapperAnno;
        try {
            mapperAnno = (Class<? extends Annotation>) classLoader.loadClass("org.apache.ibatis.annotations.Mapper");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        //在实例化的范围内查找符合条件的mapper
        for (Class<?> clz : classInstanceSet) {
            //如果有mapperScan指定的路径，则路径下的接口都是mapper
            if(mapperPath.size() > 0){
                for (String p : mapperPath) {
                    if(clz.getName().startsWith(p) && clz.isInterface()){
                        set.add(clz);
                        break;
                    }
                }
            }
            //或者直接声明Mapper的接口也算mapper
            if(mapperAnno != null){
                Annotation annotation = clz.getAnnotation(mapperAnno);
                if(annotation != null && clz.isInterface()){
                    set.add(clz);
                }
            }
        }
        return set;
    }

    public Class<?> getClassByName(String className) {
        return classInstanceMap.get(className);
    }
}
