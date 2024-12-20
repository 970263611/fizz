package com.dahuaboke.fizz;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONType;
import com.alibaba.fastjson2.filter.PropertyFilter;
import com.dahuaboke.fizz.util.LoadJarClassUtil;
import com.dahuaboke.fizz.util.SqlUtils;
import org.objectweb.asm.*;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
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
    private String searchAnnotation;
    private String[] searchClasses;
    private String[] markAnnotations;
    private String[] packages;
    private Reflections reflections;
    private Map<String, ClassMetadata> CACHE_CLASSES = new HashMap<>();
    private Set<String> FEIGN_CLASSNAMES = new HashSet<>();
    private Set<String> MAPPER_CLASSNAMES = new HashSet<>();
    private Map<String, Map<String, String>> annotationMetadata = new HashMap<>();
    private String project;
    private String version;
    private Map<String, Set<String>> mapperSql = new HashMap<>();
    private LoadJarClassUtil loadJarClassUtil;

    public Fizz(String project, String version, String jarPath, String searchAnnotation, String[] searchClasses, String[] markAnnotations, String[] packages) throws MalformedURLException, ClassNotFoundException {
        this.project = project;
        this.version = version;
        this.jarPath = jarPath;
        this.searchAnnotation = searchAnnotation;
        this.searchClasses = searchClasses;
        this.markAnnotations = markAnnotations;
        this.packages = packages;
        this.loadJarClassUtil = new LoadJarClassUtil(jarPath, packages, null);
        ConfigurationBuilder builder = new ConfigurationBuilder();
        if (jarPath != null) {
            URLClassLoader classloader = loadJarClassUtil.getClassLoader();
            builder.addClassLoaders(classloader);
            builder.setUrls(new URL("file:" + jarPath));
        }
        builder.forPackages(packages);
        this.reflections = new Reflections(builder);
    }

    public String run() throws Exception {
        try {
            mapperSql = SqlUtils.findTableNameInJar(jarPath);
            Set<String> mappers = mapperSql.keySet();
            for (String mapper : mappers) {
                int i = mapper.lastIndexOf(".");
                this.MAPPER_CLASSNAMES.add(mapper.substring(0, i).replaceAll("\\.", "/"));
            }
        } catch (Exception e) {
        }
        Map<String, Map<String, List<Node>>> feignNode = new HashMap<>();
        buildFeignData(feignNode);
        findMapper();
        Class<? extends Annotation> aClass = (Class<? extends Annotation>) Class.forName(searchAnnotation);
        Set<Class<?>> classes = searchClassByAnnotation(aClass);
        try {
            parseAnnotationMetadata(aClass, classes);
            for (String markClassName : markAnnotations) {
                Class<? extends Annotation> markClass = (Class<? extends Annotation>) Class.forName(markClassName);
                Set<Class<?>> tempMarkClasses = searchClassByAnnotation(markClass);
                parseAnnotationMetadata(markClass, tempMarkClasses);
            }
        } catch (Exception e) {
        }
        if (searchClasses != null && searchClasses.length > 0) {
            for (String s : searchClasses) {
                try {
                    classes.add(Class.forName(s));
                } catch (ClassNotFoundException e) {
                }
            }
        }
        Map<String, Map<String, Object>> chainNode = buildChain(classes);
        HashMap result = new LinkedHashMap();
        result.put("project", project);
        result.put("version", version);
        result.put("chainNode", chainNode);
        result.put("feignNode", feignNode);
        return JSON.toJSONString(result, (PropertyFilter) (o, k, v) -> {
            if (o instanceof Node) {
                if (v == null) {
                    return false;
                }
                if (v instanceof List && ((List) v).isEmpty()) {
                    return false;
                }
                if ("feign".equals(k) || "mapper".equals(k)) {
                    if (v instanceof Boolean) {
                        if (!((Boolean) v).booleanValue()) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNullListAsEmpty);
    }

    private void findMapper() {
        try {
            Class<? extends Annotation> mapperClass = (Class<? extends Annotation>) Class.forName(MAPPER_ANNO_PATH.replaceAll("/", "\\."));
            for (Class<?> clz : searchClassByAnnotation(mapperClass)) {
                MAPPER_CLASSNAMES.add(clz.getName().replaceAll("\\.", "/"));
            }
        } catch (ClassNotFoundException e) {
        }
    }

    private void buildFeignData(Map<String, Map<String, List<Node>>> feignNode) throws IOException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Class<? extends Annotation> feignClass = null;
        try {
            feignClass = (Class<? extends Annotation>) Class.forName(FEIGN_ANNO_PATH.replaceAll("/", "\\."));
        } catch (ClassNotFoundException e) {
        }
        if (feignClass != null) {
            Set<Class<?>> interfaceClasses = searchClassByAnnotation(feignClass);
            Set<Class<?>> excludeClass = new HashSet<>();
            if (!interfaceClasses.isEmpty()) {
                for (Class<?> clz : interfaceClasses) {
                    if (clz.isInterface()) {
                        FEIGN_CLASSNAMES.add(clz.getName().replaceAll("\\.", "/"));
                        Annotation annotation = clz.getAnnotation(feignClass);
                        if (annotation != null) {
                            Method fallbackFactoryMethod = annotation.annotationType().getDeclaredMethod("fallbackFactory");
                            fallbackFactoryMethod.setAccessible(true);
                            Object obj = fallbackFactoryMethod.invoke(annotation);
                            if (obj != null) {
                                Class<?> fallbackClass = (Class<?>) obj;
                                excludeClass.add(fallbackClass);
                            }
                        }
                    }
                }
            }
            for (Class<?> interfaceClass : interfaceClasses) {
                if (interfaceClass.isInterface()) {
                    Set<Class<?>> classes = searchClassByInterface(interfaceClass);
                    classes.removeAll(excludeClass);
                    Map<String, List<Node>> nodes = buildFeign(classes);
                    if (!nodes.isEmpty()) {
                        feignNode.put(interfaceClass.getName(), nodes);
                    }
                }
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

    private Set<Class<?>> searchClassByInterface(Class<?> interfaceClass) {
        Set<Class<?>> classes = (Set<Class<?>>) reflections.getSubTypesOf(interfaceClass);
        return classes == null ? new HashSet<>() : classes;
    }

    private Map<String, List<Node>> buildFeign(Set<Class<?>> classes) throws IOException {
        for (Class<?> clz : classes) {
            if (!clz.isInterface()) {
                cache(clz.getName().replaceAll("\\.", "/"));
            }
        }
        Map<String, List<Node>> chainMap = new LinkedHashMap<>();
        for (Class<?> clz : classes) {
            List<Node> chains = new ArrayList<>();
            draw(clz.getName(), chains);
            chainMap.put(clz.getName(), chains);
        }
        return chainMap;
    }

    private Map<String, Map<String, Object>> buildChain(Set<Class<?>> classes) throws IOException {
        for (Class<?> clz : classes) {
            if (!clz.isInterface()) {
                cache(clz.getName().replaceAll("\\.", "/"));
            }
        }
        Map<String, Map<String, Object>> chainMap = new LinkedHashMap<>();
        for (Class<?> clz : classes) {
            String name = clz.getName();
            List<Node> chains = new ArrayList<>();
            Map<String, Object> temp = new LinkedHashMap<>();
            draw(name, chains);
            if (annotationMetadata.containsKey(name)) {
                Map<String, String> annotationData = annotationMetadata.get(name);
                temp.put("annotationData", annotationData);
            }
            temp.put("chain", chains);
            chainMap.put(name, temp);
        }
        return chainMap;
    }

    private void cache(String cname) throws IOException {
        if (!startWithPackages(cname)) {
            return;
        }
        ClassMetadata classMetadataCache = CACHE_CLASSES.get(cname);
        if (classMetadataCache != null) {
            return;
        }
        final ClassMetadata classMetadata = new ClassMetadata();
        classMetadata.setName(cname);
        if (MAPPER_CLASSNAMES.contains(cname)) {
            classMetadata.setMapper(true);
        }
        CACHE_CLASSES.put(cname, classMetadata);
        ClassReader classReader;
        if (jarPath == null) {
            classReader = new ClassReader(cname);
        } else {
            InputStream classInputStream = loadJarClassUtil.getClassInputStream(cname);
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

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.contains(FEIGN_ANNO_PATH)) {
                    isFeign = true;
                }
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public MethodVisitor visitMethod(int access, String cMethod, String cDescriptor, String cSignature, String[] exceptions) {
                if (cMethod.equals("<init>") || cMethod.equals("<clinit>")) {
                    return null;
                }
                classMetadata.setFeign(isFeign);
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
                                    if (!FEIGN_CLASSNAMES.contains(owner) && !MAPPER_CLASSNAMES.contains(owner)) {
                                        Class<?> interfaceClassName = Class.forName(owner.replaceAll("/", "\\."));
                                        Set<Class<?>> classes = searchClassByInterface(interfaceClassName);
                                        for (Class<?> c : classes) {
                                            loadTrace(c.getName(), name, descriptor, methodMetadata.get(), cname, finalCMethod.get(), finalCDescriptor.get(), true);
                                        }
                                    } else if (FEIGN_CLASSNAMES.contains(owner) || MAPPER_CLASSNAMES.contains(owner)) {
                                        loadTrace(owner, name, descriptor, methodMetadata.get(), cname, finalCMethod.get(), finalCDescriptor.get(), false);
                                    }
                                } else {
                                    loadTrace(owner, name, descriptor, methodMetadata.get(), cname, finalCMethod.get(), finalCDescriptor.get(), false);
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

    private void loadTrace(String className, String methodName, String methodParam, MethodMetadata methodMetadata, String cname, String cMethod, String cDescriptor, boolean isInterface) throws Exception {
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
            if (isInterface) {
                methodMetadata.setInterfaceTrace(traceMetadata);
            } else {
                methodMetadata.setTrace(traceMetadata);
            }
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
            node.setMapper(classMetadata.isMapper());
            String temp = cname.replaceAll("\\.", "/") + "#" + methodName + "#" + method.getParam();
            if (alreadyInvoke.contains(temp)) {
                node.setCycle(temp);
                continue;
            }
            node.setName(temp);
            alreadyInvoke.add(temp);
            List<TraceMetadata> traces = method.getTraces();
            if (traces != null && !traces.isEmpty()) {
                drawTraces(traces, node, alreadyInvoke, false, null);
            } else {
                if (classMetadata.isMapper()) {
                    String classN = classMetadata.getName();
                    String methodN = method.getName();
                    node.setTables(mapperSql.get(classN + "\\." + methodN));
                }
                Map<String, List<TraceMetadata>> interfaceTraces = method.getInterfaceTraces();
                if (interfaceTraces != null && !interfaceTraces.isEmpty()) {
                    interfaceTraces.forEach((k, v) -> {
                        drawTraces(v, node, alreadyInvoke, true, k);
                    });
                }
            }
        }
    }

    private void drawTraces(List<TraceMetadata> traces, Node node, HashSet<String> alreadyInvoke, boolean isInterface, String implementClassName) {
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
        drawTrace(distinctTraces, node, alreadyInvoke, isInterface, implementClassName);
    }

    private void drawTrace(List<TraceMetadata> traces, Node node, HashSet<String> alreadyInvoke, boolean isInterface, String implementClassName) {
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
            childNode.setMapper(classMetadata.isMapper());
            childNode.setName(temp);
            alreadyInvoke.add(temp);
            if (isInterface) {
                node.setInterfaceChildren(implementClassName, childNode);
            } else {
                node.setChildren(childNode);
            }
            List<MethodMetadata> methods = classMetadata.getMethods();
            if (methods == null) {
                continue;
            }
            for (MethodMetadata dataMethod : classMetadata.getMethods()) {
                String name = dataMethod.getName();
                String param = dataMethod.getParam();
                if (methodName.equals(name) && methodParam.equals(param)) {
                    List<TraceMetadata> childTraces = dataMethod.getTraces();
                    if (childTraces == null) {
                        if (classMetadata.isMapper()) {
                            String classN = classMetadata.getName();
                            String methodN = dataMethod.getName();
                            childNode.setTables(mapperSql.get(classN.replaceAll("/", "\\.") + "." + methodN));
                        }
                    } else {
                        drawTrace(childTraces, childNode, alreadyInvoke, isInterface, implementClassName);
                    }
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
        private Map<String, List<TraceMetadata>> interfaceTraces;

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

        public Map<String, List<TraceMetadata>> getInterfaceTraces() {
            return interfaceTraces;
        }

        public void setInterfaceTraces(Map<String, List<TraceMetadata>> interfaceTraces) {
            this.interfaceTraces = interfaceTraces;
        }

        public void setInterfaceTrace(TraceMetadata data) {
            String key = data.getClassName();
            if (this.interfaceTraces != null && this.interfaceTraces.containsKey(key)) {
                List<TraceMetadata> traceMetadata = this.interfaceTraces.get(key);
                traceMetadata.add(data);
            } else {
                this.interfaceTraces = new LinkedHashMap() {{
                    put(key, new ArrayList<TraceMetadata>() {{
                        add(data);
                    }});
                }};
            }
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

    @JSONType(orders = {"name", "cycle", "feign", "mapper", "children", "annotationMetadata"})
    public class Node {
        private String name;
        private boolean feign;
        private boolean mapper;
        private List<Node> children;
        private Map<String, List<Node>> interfaceChildren;
        private String cycle;
        private Set<String> tables;

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

        public Set<String> getTables() {
            return tables;
        }

        public void setTables(Set<String> tables) {
            this.tables = tables;
        }

        public boolean isMapper() {
            return mapper;
        }

        public void setMapper(boolean mapper) {
            this.mapper = mapper;
        }

        public Map<String, List<Node>> getInterfaceChildren() {
            return interfaceChildren;
        }

        public void setInterfaceChildren(Map<String, List<Node>> interfaceChildren) {
            this.interfaceChildren = interfaceChildren;
        }

        public void setInterfaceChildren(String key, Node node) {
            if (this.interfaceChildren != null && this.interfaceChildren.containsKey(key)) {
                List<Node> nodeList = this.interfaceChildren.get(key);
                nodeList.add(node);
            } else {
                this.interfaceChildren = new LinkedHashMap() {{
                    put(key, new ArrayList<Node>() {{
                        add(node);
                    }});
                }};
            }
        }
    }

    public Map mergeNode(Map allNode) {
        String project = (String) allNode.get("project");
        String version = (String) allNode.get("version");
        Object chainNodeStr = allNode.get("chainNode");
        LinkedHashMap chainNode = JSON.parseObject(JSON.toJSONString(chainNodeStr), LinkedHashMap.class);
        Object feignNodeStr = allNode.get("feignNode");
        Map feignNodeTemp = JSON.parseObject(JSON.toJSONString(feignNodeStr), LinkedHashMap.class);
        Map<String, Map<String, List<Fizz.Node>>> feignNode = new LinkedHashMap<>();
        feignNodeTemp.forEach((k, v) -> {
            String key = k.toString();
            LinkedHashMap value = JSON.parseObject(JSON.toJSONString(v), LinkedHashMap.class);
            Map<String, List<Fizz.Node>> tempNode = new LinkedHashMap<>();
            value.forEach((k1, v1) -> {
                String key1 = k1.toString();
                List<Fizz.Node> value1 = JSON.parseArray(JSON.toJSONString(v1), Fizz.Node.class);
                tempNode.put(key1, value1);
            });
            feignNode.put(key, tempNode);
        });
        chainNode.forEach((k, v) -> {
            LinkedHashMap temp = JSON.parseObject(JSON.toJSONString(v), LinkedHashMap.class);
            Object chain = temp.get("chain");
            List<Fizz.Node> nodes = JSON.parseArray(JSON.toJSONString(chain), Fizz.Node.class);
            appendFeign(nodes, feignNode);
            temp.put("chain", nodes);
            chainNode.put(k, temp);
        });
        LinkedHashMap result = new LinkedHashMap() {{
            put("project", project);
            put("version", version);
            put("data", chainNode);
        }};
        return result;
    }

    private static void appendFeign(List<Fizz.Node> nodes, Map<String, Map<String, List<Fizz.Node>>> feignNode) {
        for (Fizz.Node node : nodes) {
            boolean feign = node.isFeign();
            if (feign) {
                String feignClassName = node.getName().split("#")[0].replaceAll("/", "\\.");
                Map<String, List<Fizz.Node>> feignNodes = feignNode.get(feignClassName);
                if (feignNodes != null) {
                    node.setInterfaceChildren(feignNodes);
                }
            } else {
                List<Fizz.Node> children = node.getChildren();
                if (children != null) {
                    appendFeign(children, feignNode);
                }
            }
        }
    }
}
