package com.dahuaboke.fizz;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.PropertyFilter;
import com.dahuaboke.fizz.io.FilesReader;
import com.dahuaboke.fizz.io.FilesWriter;
import com.dahuaboke.fizz.io.Reader;
import com.dahuaboke.fizz.io.Writer;

import java.io.IOException;
import java.util.*;

public class DoFizz {

    private static PropertyFilter filter =
            (o, k, v) -> {
                if (o instanceof Fizz.Node) {
                    if (v == null) {
                        return false;
                    }
                    if (v instanceof List && ((List) v).isEmpty()) {
                        return false;
                    }
                    if (v instanceof Set && ((Set) v).isEmpty()) {
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
            };

    public static void main(String[] args) {
        String tempFilePath = "C:\\Users\\dahua\\Desktop\\xc\\a.json";
        String finalFilePath = "C:\\Users\\dahua\\Desktop\\xc\\b.json";
        String jarPath = "C:\\Users\\dahua\\Documents\\WeChat Files\\dingweiqiang872226\\FileStorage\\File\\2024-12\\ifund-trade-deployment-project-0.0.1-SNAPSHOT.jar";
//        String annotationClass = "com.psbc.otsp.base.trade.annotation.annotation.OtspService";
        String annotationClass = "com.psbc.otsp.base.trade.annotation.annotation.PluginComponent";
        String[] packages = {"com.psbc.ifund"};
        Map<String, String> marks = new HashMap() {{
            put(annotationClass, "业务组件");
        }};
        try {
            Fizz fizz = new Fizz("ifund", "1.0.0", jarPath, annotationClass, null, packages);
            String projectMessage = JSON.toJSONString(fizz.run(), filter, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNullListAsEmpty);
            Writer writer = new FilesWriter();
            writer.write(tempFilePath, projectMessage);
            Reader reader = new FilesReader();
            String read = reader.read(tempFilePath);
            LinkedHashMap allNode = JSON.parseObject(read, LinkedHashMap.class);
            Map result = fizz.mergeNode(allNode);
            SimpleChain simpleChain = new SimpleChain(marks.keySet());
            result.put("simple", simpleChain.run(result));
            AnnotationMark annotationMark = new AnnotationMark();
            result.put("component", annotationMark.markAnnotations(fizz, marks));
            writer.write(finalFilePath, JSON.toJSONString(result, filter, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNullListAsEmpty));
        } catch (IOException e) {
            System.out.println(e);
        } catch (ClassNotFoundException e) {
            System.out.println(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
