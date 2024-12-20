package com.dahuaboke.fizz;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.PropertyFilter;
import com.dahuaboke.fizz.io.FilesReader;
import com.dahuaboke.fizz.io.FilesWriter;
import com.dahuaboke.fizz.io.Reader;
import com.dahuaboke.fizz.io.Writer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DoFizz {

    public static void main(String[] args) {
        String tempFilePath = "C:\\Users\\dahua\\Desktop\\xc\\a.json";
        String finalFilePath = "C:\\Users\\dahua\\Desktop\\xc\\b.json";
        String jarPath = "C:\\Users\\dahua\\Documents\\WeChat Files\\dingweiqiang872226\\FileStorage\\File\\2024-12\\ifund-trade-deployment-project-0.0.1-SNAPSHOT.jar";
//        String jarPath = "C:\\Users\\dahua\\Documents\\WeChat Files\\dingweiqiang872226\\FileStorage\\File\\2024-12\\nacostest-1.0-SNAPSHOT.jar";
//        String annotationClass = "com.psbc.otsp.base.trade.annotation.annotation.OtspService";
        String annotationClass = "com.psbc.otsp.base.trade.annotation.annotation.PluginComponent";
//        String annotationClass = "org.springframework.web.bind.annotation.RestController";
        String[] packages = {"com.dimple", "com.psbc"};
        try {
            Fizz fizz = new Fizz("ifund", "1.0.0", jarPath, annotationClass, null,null, packages);
            String projectMessage = fizz.run();
            Writer writer = new FilesWriter();
            writer.write(tempFilePath, projectMessage);
            Reader reader = new FilesReader();
            String read = reader.read(tempFilePath);
            LinkedHashMap allNode = JSON.parseObject(read, LinkedHashMap.class);
            Map result = fizz.mergeNode(allNode);
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
}
