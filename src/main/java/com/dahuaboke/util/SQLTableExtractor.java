package com.dahuaboke.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SQLTableExtractor {

    /**
     * 从给定的SQL语句中提取表名
     *
     * @param sql SQL语句
     * @return 表名列表
     */
    public static List<String> extractTableNames(String sql) {
        List<String> tableNames = new ArrayList<>();

        // 处理简单的SELECT、INSERT、UPDATE、DELETE语句中的表名（FROM关键字后）
        extractSimpleTableNames(sql, tableNames);

        // 处理多表连接中的表名（JOIN关键字后）
        extractJoinedTableNames(sql, tableNames);

        // 处理子查询中的表名（递归处理子查询语句）
        extractSubqueryTableNames(sql, tableNames);

        return tableNames.stream().distinct().collect(Collectors.toList());
    }

    private static void extractSimpleTableNames(String sql, List<String> tableNames) {
        String[] sqlParts = sql.split("\\s+");
        for (int i = 0; i < sqlParts.length; i++) {
            if ("FROM".equalsIgnoreCase(sqlParts[i])) {
                String tablePart = "";
                for (int j = i + 1; j < sqlParts.length; j++) {
                    tablePart += sqlParts[j] + " ";
                }
                tablePart = tablePart.trim();
                String[] tables = tablePart.split(",");
                for (String table : tables) {
                    tableNames.add(table.trim());
                }
            }
        }
    }

    private static void extractJoinedTableNames(String sql, List<String> tableNames) {
        Pattern joinPattern = Pattern.compile("JOIN\\s+(\\w+)");
        Matcher joinMatcher = joinPattern.matcher(sql);
        while (joinMatcher.find()) {
            tableNames.add(joinMatcher.group(1));
        }
    }

    private static void extractSubqueryTableNames(String sql, List<String> tableNames) {
        int startIndex = sql.indexOf("(");
        int endIndex = sql.lastIndexOf(")");
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            String subquery = sql.substring(startIndex + 1, endIndex);
            List<String> subqueryTableNames = extractTableNames(subquery);
            tableNames.addAll(subqueryTableNames);
        }
    }

//    public static void main(String[] args) {
//        String sql = "SELECT * FROM user u JOIN order o ON u.id = o.user_id WHERE u.age > (SELECT AVG(age) FROM user)";
//        List<String> tableNames = extractTableNames(sql);
//        for (String tableName : tableNames) {
//            System.out.println(tableName);
//        }
//    }
}