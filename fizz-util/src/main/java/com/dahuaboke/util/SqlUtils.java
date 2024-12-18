package com.dahuaboke.util;

import com.alibaba.druid.DbType;
import com.wjy.mapper2sql.SqlUtil;
import com.wjy.mapper2sql.bo.MapperSqlInfo;
import com.wjy.mapper2sql.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlUtils {

    /**
     * 在jar包中查找文件，并返回文件流，未找到则返回null
     *
     * @param jarPath  jar包全路径名
     * @param fileName 要查找的文件名 例: Abc.xml  Bcd.class
     * @return 文件的输入流
     */
    public static InputStream findStreamByNameInJar(String jarPath, String fileName) {
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(jarPath);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String entryName = jarEntry.getName();
            entryName = entryName.substring(entryName.lastIndexOf("/") + 1);
            if (entryName.equals(fileName)) {
                try {
                    return jarFile.getInputStream(jarEntry);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * 在jar包中查找文件，并返回文件流，未找到则返回null
     *
     * @param jarPath jar包全路径名
     * @return 文件的输入流
     */
    public static Map<String, InputStream> findStreamByEndInJar(String jarPath, String suffix) {
        Map<String, InputStream> map = new HashMap();
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(jarPath);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String entryName = jarEntry.getName();
            entryName = entryName.substring(entryName.lastIndexOf("/") + 1);
            if (entryName.endsWith(suffix)) {
                try {
                    map.put(entryName, jarFile.getInputStream(jarEntry));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return map;
    }

    /**
     * 去掉字符串两侧的双引号
     * @param str
     * @return
     */
    public static String removeQuotes(String str){
        Pattern pattern = Pattern.compile("^\"(.*)\"$");
        Matcher matcher = pattern.matcher(str);
        if (matcher.matches()) {
            return matcher.group(1);
        }else {
            return str;
        }
    }

    /**
     * 从sql中提取表名
     * @param sql sql语句
     * @param way 方式编号
     * @return 表名set
     */
    public static Set<String> findTableNameInSql(String sql,int way){
        Set<String> set = new HashSet<>();
        if(sql == null || sql.length() == 0){
            return set;
        }else{
            sql = sql.replace("?","''");
        }
        List<String> tableNames;
        switch (way){
            case 0:
                JSQLParserExample jsqlParserExample = new JSQLParserExample();
                tableNames = jsqlParserExample.extractTableNames(sql);
                break;
            default:
                tableNames = SQLTableExtractor.extractTableNames(sql);
        }
        if (tableNames != null) {
            for (String tableName : tableNames) {
                set.add(removeQuotes(tableName));
            }
        }
        return set;
    }

    /**
     * 从指定jar包的中找到所有mapper，并找到每个sql对应的表名
     * @param jarPath jar包路径
     * @return  key: namespace.id    value: 表名 Set<String>
     */
    public static Map<String,Set<String>> findTableNameInJar(String jarPath) {
        Map<String,Set<String>> map = new HashMap();
        final String suffix = ".xml";
        // 指定扫描的mapper文件路径，也可以是整个项目I
        Map<String, InputStream> streamByEndInJar = findStreamByEndInJar(jarPath, suffix);
        Map<String, InputStream> mapperXmlMap = new HashMap();
        for (Map.Entry<String, InputStream> entry : streamByEndInJar.entrySet()) {
            if (FileUtil.isMapperXml(entry.getValue())) {
                mapperXmlMap.put(entry.getKey(), null);
            }
        }
        //关闭所有InputStream
        for (Map.Entry<String, InputStream> entry : streamByEndInJar.entrySet()) {
            try{
                if(entry.getValue() != null){
                    entry.getValue().close();
                }
            }catch (Exception e){}
        }
        //获取所有mapper文件流
        streamByEndInJar = findStreamByEndInJar(jarPath, ".xml");
        Set<String> keys = mapperXmlMap.keySet();
        for (String key : keys) {
            mapperXmlMap.put(key, streamByEndInJar.get(key));
        }
        //mapper解析
        for (Map.Entry<String, InputStream> entry : mapperXmlMap.entrySet()) {
            List<MapperSqlInfo> infos = null;
            try {
                infos = SqlUtil.parseMapper("", entry.getValue(), DbType.postgresql, false);
            } catch (Exception e) {
                e.printStackTrace();
                return map;
            }
            for (MapperSqlInfo mapperSqlInfo : infos) {
                HashMap<String, String> sqlIdMap = mapperSqlInfo.getSqlIdMap();
                for (Map.Entry<String,String> sqlEntry: sqlIdMap.entrySet()) {
                    Set<String> set = findTableNameInSql(sqlEntry.getValue(),0);
                    if (set == null) {
                        set = findTableNameInSql(sqlEntry.getValue(),1);
                    }
                    map.put(mapperSqlInfo.getNamespace() + "." + sqlEntry.getKey(), set);
                }
            }
        }
        //关闭所有InputStream
        for (Map.Entry<String, InputStream> entry : streamByEndInJar.entrySet()) {
            try{
                if(entry.getValue() != null){
                    entry.getValue().close();
                }
            }catch (Exception e){}
        }
        return map;
    }



//    public static void main(String[] args) {
//        String jarPath = "C:\\Users\\23195\\Desktop\\ttt\\knowledge.jar";
//        Map<String,Set<String>> map = findTableNameInJar(jarPath);
//        System.out.println(map);
//    }
//    }

}
