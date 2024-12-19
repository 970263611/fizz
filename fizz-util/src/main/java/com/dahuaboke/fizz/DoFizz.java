package com.dahuaboke.fizz;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.PropertyFilter;
import com.dahuaboke.fizz.io.FilesReader;
import com.dahuaboke.fizz.io.FilesWriter;
import com.dahuaboke.fizz.io.Reader;
import com.dahuaboke.fizz.io.Writer;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DoFizz {

    public static void main(String[] args) {
        String tempFilePath = "C:\\Users\\dahua\\Desktop\\xc\\a.json";
        String finalFilePath = "C:\\Users\\dahua\\Desktop\\xc\\b.json";
        String jarPath = "C:\\Users\\dahua\\Documents\\WeChat Files\\dingweiqiang872226\\FileStorage\\File\\2024-12\\ifund-trade-deployment-project-0.0.1-SNAPSHOT.jar";
//        String jarPath = "C:\\Users\\dahua\\Documents\\WeChat Files\\dingweiqiang872226\\FileStorage\\File\\2024-12\\nacostest-1.0-SNAPSHOT.jar";
        String annotationClass = "com.psbc.otsp.base.trade.annotation.annotation.OtspService";
//        String annotationClass = "com.psbc.otsp.base.trade.annotation.annotation.PluginComponent";
//        String annotationClass = "org.springframework.web.bind.annotation.RestController";
        String[] packages = {"com.dimple", "com.psbc"};
        try {
            String projectMessage = new Fizz("ifund", "1.0.0", jarPath, annotationClass, null, packages).run();
            Writer writer = new FilesWriter();
            writer.write(tempFilePath, projectMessage);
            Reader reader = new FilesReader();
            String read = reader.read(tempFilePath);
            LinkedHashMap linkedHashMap = JSON.parseObject(read, LinkedHashMap.class);
            String project = (String) linkedHashMap.get("project");
            String version = (String) linkedHashMap.get("version");
            Object chainNodeStr = linkedHashMap.get("chainNode");
            LinkedHashMap chainNode = JSON.parseObject(JSON.toJSONString(chainNodeStr), LinkedHashMap.class);
            Object feignNodeStr = linkedHashMap.get("feignNode");
            Map feignNodeTemp = JSON.parseObject(JSON.toJSONString(feignNodeStr), HashMap.class);
            Map<String, List<Fizz.Node>> feignNode = new HashMap<>();
            feignNodeTemp.forEach((k, v) -> {
                String key = k.toString();
                List<Fizz.Node> value = JSON.parseArray(JSON.toJSONString(v), Fizz.Node.class);
                feignNode.put(key, value);
            });
            chainNode.forEach((k, v) -> {
                List<Fizz.Node> nodes = JSON.parseArray(JSON.toJSONString(v), Fizz.Node.class);
                appendFeign(nodes, feignNode);
                chainNode.put(k, nodes);
            });
            LinkedHashMap result = new LinkedHashMap() {{
                put("project", project);
                put("version", version);
                put("data", chainNode);
            }};
            writer.write(finalFilePath, JSON.toJSONString(result, (PropertyFilter) (o, k, v) -> {
                if (o instanceof Fizz.Node) {
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
            }, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNullListAsEmpty));
        } catch (IOException e) {
            System.out.println(e);
        } catch (ClassNotFoundException e) {
            System.out.println(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void appendFeign(List<Fizz.Node> nodes, Map<String, List<Fizz.Node>> feignNode) {
        for (Fizz.Node node : nodes) {
            boolean feign = node.isFeign();
            if (feign) {
                String feignClassName = node.getName().split("#")[0].replaceAll("/", "\\.");
                List<Fizz.Node> feignNodes = feignNode.get(feignClassName);
                if (feignNodes != null) {
                    Fizz.Node childNode = feignNodes.get(0);
                    node.setName(childNode.getName());
                    node.setChildren(childNode.getChildren());
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
