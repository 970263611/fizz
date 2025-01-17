package com.wjy.mapper2sql.parse.type;

import org.apache.ibatis.type.TypeAliasRegistry;

import java.util.Map;

/**
 * 静态代理
 *
 * @author weijiayu
 * @date 2024/3/9 10:24
 * @see TypeAliasRegistry
 */
public class SimpleTypeAliasRegistry extends TypeAliasRegistry {

    @Override
    public <T> Class<T> resolveAlias(String string) {
        try {
            return super.resolveAlias(string);
        } catch (Exception e) {
            // ignore
            return null;
        }
    }

    @Override
    public void registerAliases(String packageName) {
        try {
            super.registerAliases(packageName, Object.class);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void registerAliases(String packageName, Class<?> superType) {
        try {
            super.registerAliases(packageName, superType);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void registerAlias(Class<?> type) {
        try {
            super.registerAlias(type);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void registerAlias(String alias, Class<?> value) {
        try {
            super.registerAlias(alias, value);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void registerAlias(String alias, String value) {
        try {
            super.registerAlias(alias, value);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public Map<String, Class<?>> getTypeAliases() {
        return super.getTypeAliases();
    }
}
