package com.dahuaboke.fizz;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.dahuaboke.fizz.io.FilesReader;
import com.dahuaboke.fizz.io.FilesWriter;
import com.dahuaboke.fizz.io.Reader;
import com.dahuaboke.fizz.io.Writer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChainsJoin {

    static String rootPath = "C:\\Users\\changdongliang\\Desktop\\day1225\\a1\\";

    private static String[] filepaths = new String[]{rootPath + "a1.json"};

    private static String project = "ifund";

    private static String version = "1.0.0";

    private static String writePath = rootPath + "ifund_1.0.0.json";

    public static void main(String[] args) {
        Map<String, Map<String, Object>> componentMap = new HashMap<>();
        Map<String, Object> allMap = new HashMap() {{
            put("project", project);
            put("version", version);
            put("chainNode", new LinkedHashMap<>());
            put("feignNode", new LinkedHashMap<>());
        }};
        Reader reader = new FilesReader();
        for (String filepath : filepaths) {
            try {
                String str = reader.read(filepath);
                if (str != null && !"".equals(str)) {
                    JSONObject json = JSONObject.parseObject(str);
                    if (project.equals(json.getString("project")) && version.equals(json.getString("version"))) {
                        Map cn = JSONObject.parseObject(json.getString("chainNode"), Map.class);
                        if (cn != null) {
                            ((Map) allMap.get("chainNode")).putAll(cn);
                        }
                        Map fn = JSONObject.parseObject(json.getString("feignNode"), Map.class);
                        if (fn != null) {
                            ((Map) allMap.get("feignNode")).putAll(fn);
                        }
                        JSONArray ja = json.getJSONArray("component");
                        if (ja != null) {
                            for (int i = 0; i < ja.size(); i++) {
                                JSONObject cm = ja.getJSONObject(i);
                                String name = cm.getString("name");
                                if (componentMap.containsKey(name)) {
                                    Map cMap = componentMap.get(name);
                                    //添加classes
                                    Map<String, Map> cmClasses = JSONObject.parseObject(cm.getString("classes"), new TypeReference<Map<String, Map>>() {
                                    });
                                    if (cmClasses != null) {
                                        ((Map) cMap.get("classes")).putAll(cmClasses);
                                    }
                                    //size累加
                                    int size = (int) cMap.get("size");
                                    cMap.put("size", size + cm.getInteger("size"));
                                } else {
                                    Map cMap = new HashMap();
                                    componentMap.put(name, cMap);
                                    cMap.put("name", name);
                                    cMap.put("size", cm.getInteger("size"));
                                    cMap.put("classes", JSONObject.parseObject(cm.getString("classes"), new TypeReference<Map<String, Map>>() {
                                    }));
                                    cMap.put("annotation", cm.getString("annotation"));
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<Map> componentList = new ArrayList<>();
        componentMap.forEach((k, v) -> {
            componentList.add(v);
        });
        allMap.put("component", componentList);
        Map<String, Map> annoMap = new LinkedHashMap<>();
        for (Map<String, Object> map : componentList) {
            if (map.get("classes") != null) {
                Map<String, Object> classes = (Map<String, Object>) map.get("classes");
                classes.forEach((k, v) -> {
                    Map<String, String> vm = new LinkedHashMap() {{
                        putAll(v instanceof Map ? (Map<String, String>) v : new LinkedHashMap<>());
                    }};
                    vm.put("name", (String) map.get("name"));
                    annoMap.put(k, vm);
                });
            }
        }
        Map resultMap = mergeNode(allMap);
        SimpleChain simpleChain = new SimpleChain(annoMap);
        resultMap.put("simple", simpleChain.run(resultMap));
        Writer writer = new FilesWriter();
        try {
            writer.write(writePath, JSON.toJSONString(resultMap, Constants.FASTJSON_FILTER, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNullListAsEmpty));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static Map mergeNode(Map allNode) {
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
            put("component", allNode.get("component"));
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
