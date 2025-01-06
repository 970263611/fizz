package com.dahuaboke.fizz.util;

import com.alibaba.druid.DbType;
import com.dahuaboke.fizz.Constants;
import com.wjy.mapper2sql.SqlUtil;
import com.wjy.mapper2sql.bo.MapperSqlInfo;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlUtils {

    private static final String TMP_DIC = "tmp";

    private static final String SUFFIX = "Mapper.xml";

    private static final String ANNO_BASE_MAPPER = "com.baomidou.mybatisplus.core.mapper.BaseMappe";

    private static final String ANNO_TABLE_NAME = "com.baomidou.mybatisplus.annotation.TableName";

    private static final String ANNO_TABLE_NAME_FIELD = "value";

    /**
     * 解压jar包，在jar包所在的路径的tmp文件夹解压
     *
     * @param jarFilePath
     */
    private static List<String> findAllJarList(String jarFilePath, String tmpdic) {
        List<String> jarPathList = new ArrayList<>();
        jarPathList.add(jarFilePath);
        try {
            JarFile jarFile = new JarFile(jarFilePath);
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.endsWith(".jar")) {
                    int lastIndex = entryName.lastIndexOf('/');
                    String jarPath = entryName;
                    if (lastIndex != -1) {
                        jarPath = entryName.substring(lastIndex + 1);
                    }
                    jarPath = tmpdic + "/" + jarPath;
                    FileOutputStream fos = new FileOutputStream(jarPath);
                    InputStream is = jarFile.getInputStream(entry);
                    byte[] buffer = new byte[1024];
                    int length = -1;
                    while ((length = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, length);
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                    jarPathList.add(jarPath);
                }
            }
            jarFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jarPathList;
    }


    /**
     * 在jar包中查找文件，并返回文件流，未找到则返回null
     *
     * @param jarPath jar包全路径名
     * @return 文件的输入流
     */
    private static Map<String, InputStream> findStreamByEndInJar(String jarPath, String suffix) {
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
                    InputStream inputStream = jarFile.getInputStream(jarEntry);
                    byte[] bytes = new byte[inputStream.available()];
                    inputStream.read(bytes);
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    inputStream.close();
                    map.put(entryName, bais);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (jarFile != null) {
            try {
                jarFile.close();
            } catch (IOException e) {
            }
        }
        return map;
    }

    /**
     * 去掉字符串两侧的双引号
     *
     * @param str
     * @return
     */
    private static String removeQuotes(String str) {
        Pattern pattern = Pattern.compile("^\"(.*)\"$");
        Matcher matcher = pattern.matcher(str);
        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            return str;
        }
    }

    /**
     * 从sql中提取表名
     *
     * @param sql sql语句
     * @param way 方式编号
     * @return 表名set
     */
    private static Set<String> findTableNameInSql(String sql, int way) {
        Set<String> set = new HashSet<>();
        if (sql == null || sql.length() == 0) {
            return set;
        } else {
            sql = sql.replace("?", "''");
        }
        List<String> tableNames = null;
        switch (way) {
            case 0:
                try {
                    Statement statement = CCJSqlParserUtil.parse(sql);
                    if (statement instanceof Select) {
                        tableNames = extractSelectTableNames(sql);
                    } else if (statement instanceof Update) {
                        Update update = (Update) statement;
                        tableNames = new ArrayList() {{
                            add(update.getTable().getName());
                        }};
                    } else if (statement instanceof Delete) {
                        Delete delete = (Delete) statement;
                        tableNames = new ArrayList() {{
                            add(delete.getTable().getName());
                        }};
                    } else if (statement instanceof Insert) {
                        Insert insert = (Insert) statement;
                        tableNames = new ArrayList() {{
                            add(insert.getTable().getName());
                        }};
                    }
                } catch (Exception e) {
                }
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
     * 删除文件夹及文件夹内的文件
     */
    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        try {
            // 删除文件或空文件夹
            folder.delete();
        } catch (SecurityException e) {
            System.err.println("没有权限删除: " + folder.getAbsolutePath());
        }
    }

    private static List<String> extractSelectTableNames(String sql) {
        List<String> tableNames = new ArrayList<>();
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select) {
                Select select = (Select) statement;
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                FromItem fromItem = plainSelect.getFromItem();
                if (fromItem instanceof Table) {
                    Table table = (Table) fromItem;
                    tableNames.add(table.getName());
                }
                List<Join> joins = plainSelect.getJoins();
                if (joins != null) {
                    for (Join join : joins) {
                        if (join.getRightItem() instanceof Table) {
                            Table table = (Table) join.getRightItem();
                            tableNames.add(table.getName());
                        }
                    }
                }
            }
        } catch (JSQLParserException e) {
            //TODO e.printStackTrace();
        }
        return tableNames;
    }


    /**
     * 从指定jar包的中找到所有mapper，并找到每个sql对应的表名
     *
     * @param jarPath jar包路径
     * @return key: namespace.id    value: 表名 Set<String>
     */
    public static Map<Constants.SqlMap, Map<String, Set<String>>> findTableNameInJar(String jarPath) {
        Map<String, Set<String>> map = new HashMap();
        //创建解压路径
        if (jarPath == null || !jarPath.endsWith(".jar")) {
            return endExcute(map);
        }
        jarPath = jarPath.replace("\\", "/");
        int lastIndex = jarPath.lastIndexOf('/');
        String tmpdic = null;
        if (lastIndex != -1) {
            tmpdic = jarPath.substring(0, lastIndex + 1) + TMP_DIC;
        }
        File outputFile = new File(tmpdic);
        if (!outputFile.exists()) {
            outputFile.mkdirs();
        }
        try {
            //解压并获取全部jar包路径
            List<String> jarPathList = findAllJarList(jarPath, tmpdic);
            Map<String, InputStream> streamByEndInJar = new HashMap<>();
            for (String str : jarPathList) {
                streamByEndInJar.putAll(findStreamByEndInJar(str, SUFFIX));
            }
            //mapper解析
            for (Map.Entry<String, InputStream> entry : streamByEndInJar.entrySet()) {
                List<MapperSqlInfo> infos = null;
                try {
                    infos = SqlUtil.parseMapper(entry.getKey(), entry.getValue(), DbType.postgresql, false);
                } catch (Exception e) {
                    System.out.println("解析存在异常的xml ：" + entry.getKey());
                }//TODO 此处解析异常应该如何处理
                if (infos == null) {
                    continue;
                }
                for (MapperSqlInfo mapperSqlInfo : infos) {
                    HashMap<String, String> sqlIdMap = mapperSqlInfo.getSqlIdMap();
                    for (Map.Entry<String, String> sqlEntry : sqlIdMap.entrySet()) {
                        //从sql中获取表名
                        Set<String> set = findTableNameInSql(sqlEntry.getValue(), 0);
                        //没获取到用另外一种方法尝试
                        if (set == null || set.size() == 0) {
                            set = findTableNameInSql(sqlEntry.getValue(), 1);
                        }
                        map.put(mapperSqlInfo.getNamespace() + "." + sqlEntry.getKey(), set);
                    }
                }
            }
            //关闭所有InputStream
            for (Map.Entry<String, InputStream> entry : streamByEndInJar.entrySet()) {
                try {
                    if (entry.getValue() != null) {
                        entry.getValue().close();
                    }
                } catch (Exception e) {
                }
            }
        } finally {
            deleteFolder(outputFile);
        }
        return endExcute(map);
    }

    /**
     * 从Entity中@TableName上找到表名
     *
     * @param map Constants.SqlMap.SQLID_TABLES
     * @return 整合后map
     */
    private static Map<Constants.SqlMap, Map<String, Set<String>>> endExcute(Map<String, Set<String>> map) {
        Map<Constants.SqlMap, Map<String, Set<String>>> rmap = new HashMap<>();
        rmap.put(Constants.SqlMap.SQLID_TABLES, map);
        Map<String, Set<String>> defaultMap = new HashMap();
        rmap.put(Constants.SqlMap.ENTITY_TABLES, defaultMap);
        //这两个注解必须存在
        Class<?> bmClz = null;
        Class<? extends Annotation> tnClz = null;
        try {
            bmClz = Class.forName(ANNO_BASE_MAPPER);
            tnClz = (Class<? extends Annotation>) Class.forName(ANNO_TABLE_NAME);
        } catch (ClassNotFoundException e) {
        }
        if (bmClz != null && tnClz != null) {
            for (String s : map.keySet()) {
                String ss = s.substring(0, s.lastIndexOf("."));
                try {
                    Class<?> clz = Class.forName(ss);
                    //找到mapper所有的父接口
                    Type[] interfaces = clz.getGenericInterfaces();
                    if (interfaces != null) {
                        for (Type anInterface : interfaces) {
                            if (anInterface instanceof ParameterizedType) {
                                ParameterizedType pt = (ParameterizedType) anInterface;
                                //在所有的父接口中找到BaseMapper这个接口
                                if (pt.getRawType() != null && pt.getRawType() == bmClz) {
                                    Type[] actualTypeArguments = pt.getActualTypeArguments();
                                    if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                                        //BaseMapper接口有一个泛型，指向一个Entity
                                        Type baseArg = actualTypeArguments[0];
                                        try {
                                            Class<?> aClass = Class.forName(baseArg.getTypeName());
                                            if (aClass != null) {
                                                //Entity上有@TableName注解指定表名
                                                Annotation annoTn = aClass.getAnnotation(tnClz);
                                                if (annoTn != null) {
                                                    Method value = annoTn.annotationType().getDeclaredMethod(ANNO_TABLE_NAME_FIELD);
                                                    value.setAccessible(true);
                                                    Object obj = value.invoke(annoTn);
                                                    if (obj != null) {
                                                        defaultMap.put(ss, new HashSet<String>() {{
                                                            add(obj.toString());
                                                        }});
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        return rmap;
    }

}
