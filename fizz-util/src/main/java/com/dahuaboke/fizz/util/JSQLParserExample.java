package com.dahuaboke.fizz.util;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.ArrayList;
import java.util.List;

public class JSQLParserExample {
    public static List<String> extractTableNames(String sql) {
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

//    public static void main(String[] args) {
//        String sql = "SELECT * FROM user u JOIN order o ON u.id = o.user_id";
//        List<String> tableNames = extractTableNames(sql);
//        for (String tableName : tableNames) {
//            System.out.println(tableName);
//        }
//    }
}