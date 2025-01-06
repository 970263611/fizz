package com.dahuaboke.fizz;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.dahuaboke.fizz.io.FilesWriter;
import com.dahuaboke.fizz.io.Writer;
import com.dahuaboke.fizz.util.LoadJarClassUtil;
import com.dahuaboke.fizz.util.SqlUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DoFizz {


    static String rootPath = "C:\\Users\\changdongliang\\Desktop\\day1226\\";

    public static void main(String[] args) {
        String tempFilePath = rootPath + "a1.json";
        String jarPath = rootPath + "cmbs-account-batch-boot-1.0.0.0115-CMBS-SNAPSHOT.jar";
//        String tempFilePath = rootPath + "a2.json";
//        String jarPath = rootPath + "ifund-batch-deployment-batch-0.0.1-SNAPSHOT.jar";
        String annotationClass = "com.psbc.ifund.common.utill.annotation.EnableIFundTradeStarter";
//        String annotationClass = "com.psbc.otsp.base.trade.annotation.annotation.OtspService";
        String[] packages = {"com.psbc.ifund"};
        Map<String, String> marks = new HashMap() {{
            put("com.psbc.ifund.common.utill.annotation.IFundTradeService", "联机交易服务");
            put("com.psbc.ifund.common.utill.annotation.IFundBatchService", "批量交易服务");
            put("com.psbc.ifund.common.utill.annotation.IFundBusinessComponent", "业务组件");
            put("com.psbc.ifund.common.utill.annotation.IFundCommonComponent", "公共组件");
            put("com.psbc.ifund.common.utill.annotation.IFundDataQueryComponent", "数据查询组件");
            put("com.psbc.ifund.common.utill.annotation.IFundDataModifyComponent", "数据维护组件");
            put("com.psbc.ifund.common.utill.annotation.IFundDataOutBoundComponent", "外呼组件");
        }};
        marks.put(annotationClass, "联机入口");
        try {
            LoadJarClassUtil loadJarClassUtil = new LoadJarClassUtil(jarPath, packages, null);
            Map<Constants.SqlMap, Map<String, Set<String>>> mapperSql = SqlUtils.findTableNameInJar(jarPath);
            Fizz fizz = new Fizz("ifund", "1.0.0", jarPath, annotationClass, null, packages, loadJarClassUtil, mapperSql);
            AnnotationMark annotationMark = new AnnotationMark();
            Map fizzMap = fizz.run();
            fizzMap.put("component", annotationMark.markAnnotations(fizz, marks));
            String projectMessage = JSON.toJSONString(fizzMap, Constants.FASTJSON_FILTER, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteNullListAsEmpty);
            Writer writer = new FilesWriter();
            writer.write(tempFilePath, projectMessage);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            try {
                System.exit(0);
            } catch (Exception e) {
            }
        }
    }
}
