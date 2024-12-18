package com.dahuaboke.fizz;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONType;
import com.dahuaboke.util.LoadJarClassUtil;
import org.objectweb.asm.*;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Fizz {

    private final String FEIGN_ANNO_PATH = "org/springframework/cloud/openfeign/FeignClient";
    private final String MAPPER_ANNO_PATH = "org/apache/ibatis/annotations/Mapper";
    private String jarPath;
    private String search;
    private String[] marks;
    private String[] packages;
    private Reflections reflections;
    private Map<String, ClassMetadata> CACHE_CLASSES = new HashMap<>();
    private Set<String> FEIGN_CLASSNAMES = new HashSet<>();
    private InterfaceHandler interfaceHandler = new IFundInterfaceHandler();
    private Map<String, Map<String, String>> annotationMetadata = new HashMap<>();
    private String project;
    private String version;

<<<<<<< Updated upstream:fizz-util/src/main/java/com/dahuaboke/fizz/Fizz.java
    public Fizz(String jarPath, String search, String[] marks, String[] packages) throws MalformedURLException, ClassNotFoundException {
=======
    public Fizz(String project, String version, String jarPath, String search, String[] marks, String[] packages, Log log) throws MalformedURLException, ClassNotFoundException {
        this.project = project;
        this.version = version;
>>>>>>> Stashed changes:src/main/java/com/dahuaboke/plugins/Fizz.java
        this.jarPath = jarPath;
        this.search = search;
        this.marks = marks;
        this.packages = packages;
        ConfigurationBuilder builder;
        if (jarPath != null) {
            builder = new ConfigurationBuilder();
            URLClassLoader classloader = LoadJarClassUtil.getClassloader(packages, jarPath);
            builder.addClassLoaders(classloader);
            builder.setUrls(new URL("file:" + jarPath));
        } else {
            builder = new ConfigurationBuilder().forPackages(packages);
        }
        this.reflections = new Reflections(builder);
    }

    public void run() throws Exception {
        Map<String, List<Node>> feignNode = new HashMap<>();
        buildFeignData(feignNode);
        Class<? extends Annotation> aClass = (Class<? extends Annotation>) Class.forName(search);
        Set<Class<?>> classes = searchClassByAnnotation(aClass);
        try {
            parseAnnotationMetadata(aClass, classes);
            for (String markClassName : marks) {
                Class<? extends Annotation> markClass = (Class<? extends Annotation>) Class.forName(markClassName);
                Set<Class<?>> tempMarkClasses = searchClassByAnnotation(markClass);
                parseAnnotationMetadata(markClass, tempMarkClasses);
            }
        } catch (Exception e) {
        }
        List<Node> chainNode = buildChain(classes);
        HashMap result = new LinkedHashMap();
        result.put("project", project);
        result.put("version", version);
        result.put("chainNode", chainNode);
        result.put("feignNode", feignNode);
        String jsonString = JSON.toJSONString(result, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNullListAsEmpty);
        System.out.println(jsonString);
    }

    private void buildFeignData(Map<String, List<Node>> feignNode) throws IOException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Class<? extends Annotation> feignClass = null;
        try {
            feignClass = (Class<? extends Annotation>) Class.forName(FEIGN_ANNO_PATH.replaceAll("/", "\\."));
        } catch (ClassNotFoundException e) {
        }
        if (feignClass != null) {
            Set<Class<?>> interfaceClasses = searchClassByAnnotation(feignClass);
            if (!interfaceClasses.isEmpty()) {
                for (Class<?> clz : interfaceClasses) {
                    FEIGN_CLASSNAMES.add(clz.getName().replaceAll("\\.", "/"));
                }
            }
            for (Class<?> interfaceClass : interfaceClasses) {
                Set<Class<?>> classes = searchClassByInterface(interfaceClass);
                if (!classes.isEmpty()) {
                    Iterator<Class<?>> iterator = classes.iterator();
                    while (iterator.hasNext()) {
                        Class<?> clz = iterator.next();
                        if (clz.isInterface()) {
                            iterator.remove();
                        } else {
                            Annotation annotation = clz.getAnnotation(feignClass);
                            Class<? extends Annotation> aClass = annotation.getClass();
                            Field fallbackFactory = aClass.getField("fallbackFactory");
                            Class<?> fallbackClass = (Class<?>) fallbackFactory.get(annotation);
                            if (fallbackClass == feignClass) {
                                iterator.remove();
                            }
                        }
                    }
                }
                List<Node> nodes = buildChain(classes);
                feignNode.put(interfaceClass.getName(), nodes);
            }
        }
    }

    private boolean startWithPackages(String path) {
        for (String pkg : packages) {
            if (path.startsWith(pkg.replaceAll("\\.", "/"))) {
                return true;
            }
        }
        return false;
    }

    private Set<Class<?>> searchClassByAnnotation(Class<? extends Annotation> clazz) {
        Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(clazz);
        return typesAnnotatedWith == null ? new HashSet<>() : typesAnnotatedWith;
    }

    private Set<Class<?>> searchClassByInterface(Set<Class<?>> interfaceClasses) {
        Set<Class<?>> classes = new HashSet<>();
        for (Class<?> interfaceClass : interfaceClasses) {
            classes.addAll(searchClassByInterface(interfaceClass));
        }
        return classes;
    }

    private Set<Class<?>> searchClassByInterface(Class<?> interfaceClass) {
        Set<Class<?>> classes = (Set<Class<?>>) reflections.getSubTypesOf(interfaceClass);
        return classes == null ? new HashSet<>() : classes;
    }

    private List<Node> buildChain(Set<Class<?>> classes) throws IOException, ClassNotFoundException {
        for (Class<?> clz : classes) {
            if (!clz.isInterface()) {
                cache(clz.getName().replaceAll("\\.", "/"));
            }
        }
        List<Node> chains = new ArrayList<>();
        for (Class<?> clz : classes) {
            draw(clz.getName(), chains);
        }
        return chains;
    }

    private void cache(String cname) throws IOException, ClassNotFoundException {
        if (!startWithPackages(cname)) {
            return;
        }
        ClassMetadata classMetadataCache = CACHE_CLASSES.get(cname);
        if (classMetadataCache != null) {
            return;
        }
        final ClassMetadata classMetadata = new ClassMetadata();
        classMetadata.setName(cname);
        CACHE_CLASSES.put(cname, classMetadata);
        ClassReader classReader;
        if (jarPath == null) {
            classReader = new ClassReader(cname);
        } else {
            InputStream classInputStream = LoadJarClassUtil.getClassInputStream(cname);
            if (classInputStream == null) {
                return;
            }
            classReader = new ClassReader(classInputStream);
        }
        Map<String, Map<String, Integer>> methodLineMap = new HashMap<>();
        AtomicReference<String> tempMethodNameAndParam = new AtomicReference<>("");
        AtomicInteger beforeLastLine = new AtomicInteger(0);
        AtomicReference<String> beforeMethodNameAndParam = new AtomicReference<>("");
        AtomicInteger nowMethodLine = new AtomicInteger(0);
        classReader.accept(new ClassVisitor(Opcodes.ASM9) {
            boolean isFeign = false;
            boolean isMapper = false;

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.contains(FEIGN_ANNO_PATH)) {
                    isFeign = true;
                }
                if (descriptor.contains(MAPPER_ANNO_PATH)) {
                    isMapper = true;
                }
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public MethodVisitor visitMethod(int access, String cMethod, String cDescriptor, String cSignature, String[] exceptions) {
                if (cMethod.equals("<init>") || cMethod.equals("<clinit>")) {
                    return null;
                }
                classMetadata.setFeign(isFeign);
                classMetadata.setMapper(isMapper);
                AtomicReference<MethodMetadata> methodMetadata = new AtomicReference<>(new MethodMetadata());
                methodMetadata.get().setName(cMethod);
                methodMetadata.get().setParam(cDescriptor);
                classMetadata.setMethod(methodMetadata.get());
                AtomicReference<String> finalCMethod = new AtomicReference<>(cMethod);
                AtomicReference<String> finalCDescriptor = new AtomicReference<>(cDescriptor);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLineNumber(int line, Label start) {
                        nowMethodLine.set(line);
                        String nowMethodNameAndParam = cMethod + "#" + cDescriptor;
                        if (!tempMethodNameAndParam.get().equals(nowMethodNameAndParam)) {
                            tempMethodNameAndParam.set(nowMethodNameAndParam);
                            methodLineMap.put(nowMethodNameAndParam, new HashMap<String, Integer>() {{
                                put("begin", line);
                            }});
                            Map<String, Integer> beforeMethodNameAndParamMap = methodLineMap.get(beforeMethodNameAndParam.get());
                            if (beforeMethodNameAndParamMap != null) {
                                beforeMethodNameAndParamMap.put("end", beforeLastLine.get());
                            }
                        }
                        beforeLastLine.set(line);
                        beforeMethodNameAndParam.set(nowMethodNameAndParam);
                        super.visitLineNumber(line, start);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (!classMetadata.isFeign && !classMetadata.isMapper && startWithPackages(owner)) {
                            try {
                                if (finalCMethod.get().startsWith("lambda$")) {
                                    AtomicBoolean match = new AtomicBoolean(false);
                                    String tempMethodName = finalCMethod.get().split("\\$")[1];
                                    methodLineMap.forEach((k, v) -> {
                                        String mName = k.split("#")[0];
                                        String mParam = k.split("#")[1];
                                        if (tempMethodName.equals(mName)) {
                                            int line = nowMethodLine.get();
                                            Integer begin = v.get("begin");
                                            Integer end = v.get("end");
                                            if (line >= begin && line <= end) {
                                                match.set(true);
                                                ClassMetadata tempClassMetadata = CACHE_CLASSES.get(cname);
                                                List<MethodMetadata> methods = tempClassMetadata.getMethods();
                                                for (MethodMetadata data : methods) {
                                                    String n = data.getName();
                                                    String p = data.getParam();
                                                    if (mName.equals(n) && mParam.equals(p)) {
                                                        finalCMethod.set(mName);
                                                        finalCDescriptor.set(mParam);
                                                        methodMetadata.set(data);
                                                    }
                                                }
                                            }
                                        }
                                    });
                                    if (!match.get()) {
                                        return;
                                    }
                                }
                                if (isInterface) {
                                    if (!FEIGN_CLASSNAMES.contains(owner)) {
                                        String implementClassNameByInterface = interfaceHandler.findImplementClassNameByInterface(owner.replaceAll("/", "\\."));
                                        loadTrace(implementClassNameByInterface, name, descriptor, methodMetadata.get(), cname, finalCMethod.get(), finalCDescriptor.get());
                                    }
                                } else {
                                    loadTrace(owner, name, descriptor, methodMetadata.get(), cname, finalCMethod.get(), finalCDescriptor.get());
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
    }

    private void loadTrace(String className, String methodName, String methodParam, MethodMetadata methodMetadata, String cname, String cMethod, String cDescriptor) throws Exception {
        if (className == null) {
            return;
        }
        if (cname.equals(className) && cMethod.equals(methodName) && cDescriptor.equals(methodParam)) {
            return;
        }
        if (startWithPackages(className) && !methodName.equals("<init>") && !methodName.equals("<clinit>")) {
            TraceMetadata traceMetadata = new TraceMetadata();
            traceMetadata.setClassName(className);
            traceMetadata.setMethodName(methodName);
            traceMetadata.setMethodParam(methodParam);
            methodMetadata.setTrace(traceMetadata);
            try {
                cache(className);
            } catch (IOException e) {
                throw new Exception(e);
            }
        }
    }

    private void draw(String cname, List<Node> chains) {
        ClassMetadata classMetadata = CACHE_CLASSES.get(cname.replaceAll("\\.", "/"));
        if (classMetadata == null) {
            System.err.println(cname);
            return;
        }
        List<MethodMetadata> methods = classMetadata.getMethods();
        if (methods == null) {
            return;
        }
        for (MethodMetadata method : methods) {
            HashSet<String> alreadyInvoke = new HashSet<>();
            Node node = new Node();
            String methodName = method.getName();
            if (methodName.startsWith("lambda$")) {
                return;
            }
            chains.add(node);
            node.setFeign(classMetadata.isFeign());
            if (annotationMetadata.containsKey(cname)) {
                Map<String, String> annotationData = annotationMetadata.get(cname);
                node.setAnnotationMetadata(annotationData);
            }
            String temp = cname.replaceAll("\\.", "/") + "#" + methodName + "#" + method.getParam();
            if (alreadyInvoke.contains(temp)) {
                node.setCycle(temp);
                continue;
            }
            node.setName(temp);
            alreadyInvoke.add(temp);
            List<TraceMetadata> traces = method.getTraces();
            if (traces != null) {
                List<TraceMetadata> distinctTraces = new ArrayList<>();
                for (TraceMetadata trace : traces) {
                    boolean has = false;
                    String c1 = trace.getClassName();
                    String m1 = trace.getMethodName();
                    String p1 = trace.getMethodParam();
                    for (TraceMetadata distinctTrace : distinctTraces) {
                        String c2 = distinctTrace.getClassName();
                        String m2 = distinctTrace.getMethodName();
                        String p2 = distinctTrace.getMethodParam();
                        if (c1.equals(c2) && m1.equals(m2) && p1.equals(p2)) {
                            has = true;
                            break;
                        }
                    }
                    if (!has) {
                        distinctTraces.add(trace);
                    }
                }
                drawTrace(distinctTraces, node, alreadyInvoke);
            }
        }
    }

    private void drawTrace(List<TraceMetadata> traces, Node node, HashSet<String> alreadyInvoke) {
        if (traces == null) {
            return;
        }
        for (TraceMetadata trace : traces) {
            String className = trace.getClassName();
            String methodName = trace.getMethodName();
            String methodParam = trace.getMethodParam();
            String temp = className + "#" + methodName + "#" + methodParam;
            if (alreadyInvoke.contains(temp)) {
                node.setCycle(temp);
                continue;
            }
            ClassMetadata classMetadata = CACHE_CLASSES.get(className);
            Node childNode = new Node();
            childNode.setFeign(classMetadata.isFeign());
            childNode.setName(temp);
            alreadyInvoke.add(temp);
            node.setChildren(childNode);
            List<MethodMetadata> methods = classMetadata.getMethods();
            if (methods == null) {
                continue;
            }
            for (MethodMetadata dataMethod : classMetadata.getMethods()) {
                String name = dataMethod.getName();
                String param = dataMethod.getParam();
                if (methodName.equals(name) && methodParam.equals(param)) {
                    drawTrace(dataMethod.getTraces(), childNode, alreadyInvoke);
                }
            }
        }
    }

    private void parseAnnotationMetadata(Class markClass, Set<Class<?>> tempMarkClasses) throws InvocationTargetException, IllegalAccessException {
        for (Class<?> tempMarkClass : tempMarkClasses) {
            Annotation annotation = tempMarkClass.getAnnotation(markClass);
            Method[] methods = annotation.annotationType().getMethods();
            Map<String, String> metadata = new HashMap<>();
            for (Method method : methods) {
                if (method.getName().equals("equals") ||
                        method.getName().equals("hashCode") ||
                        method.getName().equals("toString") ||
                        method.getName().equals("annotationType")) {
                } else {
                    Object value = method.invoke(annotation);
                    metadata.put(method.getName(), value.toString());
                }
            }
            annotationMetadata.put(tempMarkClass.getName(), metadata);
        }
    }

    static class ClassMetadata {
        private String name;
        private List<MethodMetadata> methods;
        private boolean isMapper = false;
        private boolean isFeign = false;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<MethodMetadata> getMethods() {
            return methods;
        }

        public void setMethods(List<MethodMetadata> methods) {
            this.methods = methods;
        }

        public void setMethod(MethodMetadata method) {
            if (this.methods == null) {
                this.methods = new LinkedList<>();
            }
            this.methods.add(method);
        }

        public boolean isMapper() {
            return isMapper;
        }

        public void setMapper(boolean mapper) {
            isMapper = mapper;
        }

        public boolean isFeign() {
            return isFeign;
        }

        public void setFeign(boolean feign) {
            isFeign = feign;
        }
    }

    static class MethodMetadata {
        private String name;
        private String param;
        private List<TraceMetadata> traces;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getParam() {
            return param;
        }

        public void setParam(String param) {
            this.param = param;
        }

        public List<TraceMetadata> getTraces() {
            return traces;
        }

        public void setTraces(List<TraceMetadata> traces) {
            this.traces = traces;
        }

        public void setTrace(TraceMetadata trace) {
            if (this.traces == null) {
                this.traces = new LinkedList<>();
            }
            this.traces.add(trace);
        }
    }

    static class TraceMetadata {
        private String className;
        private String methodName;
        private String methodParam;

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getMethodParam() {
            return methodParam;
        }

        public void setMethodParam(String methodParam) {
            this.methodParam = methodParam;
        }
    }

    @JSONType(orders = {"name", "cycle", "feign", "children", "annotationMetadata"})
    static class Node {
        private String name;
        private boolean feign;
        private List<Node> children;
        private String cycle;
        private Map<String, String> annotationMetadata;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Node> getChildren() {
            return children;
        }

        public void setChildren(List<Node> children) {
            this.children = children;
        }

        public void setChildren(Node child) {
            if (this.children == null) {
                this.children = new LinkedList<>();
            }
            this.children.add(child);
        }

        public boolean isFeign() {
            return feign;
        }

        public void setFeign(boolean feign) {
            this.feign = feign;
        }

        public String getCycle() {
            return cycle;
        }

        public void setCycle(String cycle) {
            this.cycle = cycle;
        }

        public Map<String, String> getAnnotationMetadata() {
            return annotationMetadata;
        }

        public void setAnnotationMetadata(Map<String, String> annotationMetadata) {
            this.annotationMetadata = annotationMetadata;
        }
    }

    interface InterfaceHandler {
        String findImplementClassNameByInterface(String interfaceName) throws ClassNotFoundException;
    }

    private class IFundInterfaceHandler implements InterfaceHandler {
        @Override
        public String findImplementClassNameByInterface(String interfaceName) throws ClassNotFoundException {
            Class<?> interfaceClassName = Class.forName(interfaceName);
            Set<Class<?>> classes = searchClassByInterface(interfaceClassName);
            if (!classes.isEmpty()) {
                return new ArrayList<>(classes).get(0).getName();
            }
            return null;
        }
    }

    interface Writer {
        boolean write(String path, String message);
    }

    private class FileWriter implements Writer {

        @Override
        public boolean write(String path, String message) {
            return false;
        }
    }
}