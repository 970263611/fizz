package com.wjy.mapper2sql;

import com.alibaba.druid.DbType;
import com.wjy.mapper2sql.bo.JdbcConnProperties;
import com.wjy.mapper2sql.bo.MapperSqlInfo;
import com.wjy.mapper2sql.mock.SqlMock;
import com.wjy.mapper2sql.parse.SqlParse;
import com.wjy.mapper2sql.util.JdbcConnUtil;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.type.JdbcType;

import java.io.InputStream;
import java.sql.Connection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author weijiayu
 * @date 2024/3/11 10:20
 */
public class SqlUtil {

    public static List<MapperSqlInfo> parseMapper(String filePath, InputStream in, DbType dbType, boolean isMockParam) throws Exception {
        List<MapperSqlInfo> mapperSqlInfos = new LinkedList<>();
        mapperSqlInfos.add(parseMapperFile(filePath, in,dbType, isMockParam));
        return mapperSqlInfos;
    }

    public static List<MapperSqlInfo> parseMapperAndRunTest( String filePath, InputStream in, DbType dbType,
        JdbcConnProperties connProperties) throws Exception {
        List<MapperSqlInfo> mapperSqlInfos = parseMapper(filePath,in, dbType, true);
        try (Connection conn = JdbcConnUtil.newConnect(connProperties.getJdbcDriver(), connProperties.getJdbcUrl(),
            connProperties.getUserName(), connProperties.getPassword())) {
            for (MapperSqlInfo mapperSqlInfo : mapperSqlInfos) {
                for (Map.Entry<String, String> entry : mapperSqlInfo.getSqlIdMap().entrySet()) {
                    boolean result = true;
                    String msg = "";
                    String sqlId = entry.getKey();
                    String sql = entry.getValue();
                    try {
                        // 只要不抛异常
                        boolean rs = conn.createStatement().execute(sql);
                    } catch (Throwable t) {
                        result = false;
                        msg = t.getMessage();
                    }
                    mapperSqlInfo.getSqlTestResultInfoMap().put(sqlId,
                        mapperSqlInfo.new SqlTestResultInfo(result, msg));
                }
            }
        }
        return mapperSqlInfos;
    }

    private static MapperSqlInfo parseMapperFile( String filePath,InputStream in,  DbType dbType, boolean isMockParam)
        throws Exception {
        // 1、解析mapper xml文件，并提取出带占位符?的sql
        MapperSqlInfo mapperSqlInfo = SqlParse.parseMapperFile(filePath, in,dbType);
        if (!isMockParam) {
            return mapperSqlInfo;
        }

        // 2、合并定义的字段类型
        HashMap<String, JdbcType> columnJdbcTypeMap = mergeResultMappings(mapperSqlInfo.getPropertyResultMappings());
        HashMap<String, String> sqlIdMap = mapperSqlInfo.getSqlIdMap();
        if (sqlIdMap.isEmpty() || columnJdbcTypeMap.isEmpty()) {
            return mapperSqlInfo;
        }

        // 3、sql参数mock
        // sql格式化：对sql做AST语法解析整形，方便后续解析
        // 占位符?类型推断：在表达式里找到?最可能匹配的表字段，在结果集里查找字段对应的jdbc类型， 并mock值
        for (Map.Entry<String, String> entry : sqlIdMap.entrySet()) {
            try {
                String sqlId = entry.getKey();
                String sql = entry.getValue();
                sqlIdMap.put(sqlId, SqlMock.mockSql(sql, dbType, "?", columnJdbcTypeMap));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return mapperSqlInfo;
    }

    private static HashMap<String, JdbcType> mergeResultMappings(List<ResultMapping> propertyResultMappings) {
        HashMap<String, JdbcType> columnJdbcTypeMap = new HashMap<>();
        if (propertyResultMappings == null) {
            return columnJdbcTypeMap;
        }
        for (ResultMapping rm : propertyResultMappings) {
            columnJdbcTypeMap.put(rm.getColumn(), rm.getJdbcType());
        }
        return columnJdbcTypeMap;
    }
}
