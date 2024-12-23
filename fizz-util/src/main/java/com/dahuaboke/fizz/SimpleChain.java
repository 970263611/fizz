package com.dahuaboke.fizz;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dahuaboke.fizz.io.FilesReader;

import java.util.*;

public class SimpleChain {

    private Map<String, Map> chainsMap = new HashMap<>();

    /**
     * 标注解的列表，为空时不进行过滤；不为空时进行过滤，仅展示annoClass中的节点(链条的第一个节点不进行过滤)
     */
    private Set<String> annoClass;


    public SimpleChain(Set<String> annoClass) {
        this.annoClass = annoClass;
    }

    /**
     * 主要方法
     *
     * @param map 全量链路
     * @return 简化后的链路字符串
     */
    //TODO 异常处理没做太好
    public Map<String, Map> run(Map map) {
        JSONObject json = JSONObject.parseObject(JSON.toJSONString(map));
        JSONObject data = json.getJSONObject("data");
        for (String key : data.keySet()) {
            JSONObject j1 = data.getJSONObject(key);
            JSONArray arr = j1.getJSONArray("chain");
            if (arr != null && arr.size() > 0) {
                for (int i = 0; i < arr.size(); i++) {
                    recursion(null, arr.getJSONObject(i));
                }
            }
        }
        return chainsMap;
    }

    /**
     * 递归执行
     *
     * @param chains 类名链路列表
     * @param json   本层级node
     */
    private void recursion(List<String> chains, JSONObject json) {
        if (json != null) {
            //第一次且仅第一次进来一定是null
            if (chains == null) {
                chains = new ArrayList<>();
            }
            //把类名放进去
            String name = json.getString("name").split("#")[0];
            chains.add(name);
            //获取子方法列表和子接口列表
            JSONArray children = json.getJSONArray("children");
            JSONObject ifChildren = json.getJSONObject("interfaceChildren");
            if (children != null && children.size() > 0) {
                //子方法列表直接带入下一层
                for (int i = 0; i < children.size(); i++) {
                    recursion(chains, children.getJSONObject(i));
                }
            } else if (ifChildren != null && ifChildren.size() > 0) {
                //子接口列表，把本机name删掉，因为要接口替换为实现类，之后进行递归
                chains.remove(chains.size() - 1);
                for (String key : ifChildren.keySet()) {
                    JSONArray implArr = ifChildren.getJSONArray(key);
                    for (int j = 0; j < implArr.size(); j++) {
                        recursion(chains, implArr.getJSONObject(j));
                    }
                }
                //最后把接口名重新加上，因为补偿最后的移除操作
                chains.add(name);
            } else {
                //如果既没有子方法列表，也没有子接口列表，链路已完整，可以添加到chainsMap中
                addChains(chains);
            }
            //离开本层级时，删掉本层级的类名，才能回到上一个层级
            chains.remove(chains.size() - 1);
        }
    }

    /**
     * 拼接简化chains
     *
     * @param chains 一条完整的类链路
     */
    private void addChains(List<String> chains) {
        List<String> list = new ArrayList<>();
        //过滤非注解界定，头节点不过滤
        for (int i = 0; i < chains.size(); i++) {
            //相邻相同过滤
            if (i == 0 || ((annoClass == null || annoClass.size() == 0 || annoClass.contains(chains.get(i))) && !chains.get(i - 1).equals(chains.get(i)))) {
                list.add(chains.get(i));
            }
        }
        if (list.size() == 0) {
            return;
        }
        //第一层一定是chainsMap本身
        Map<String, Map> map = chainsMap;
        //此时父节点类名一定是空
        String parentStr = null;
        //此时父map一定是null
        Map<String, Map> parentMap = null;
        for (String str : list) {
            if (map == null) {
                map = new HashMap<>();
                parentMap.put(parentStr, map);
                map.put(str, null);
            } else if (!map.containsKey(str)) {//map有这个类名了，就不用添加了
                map.put(str, null);
            }
            //留存当前节点类名作为下一次循环的父
            parentStr = str;
            //留存当前节点map作为下一次循环的父
            parentMap = map;
            //当前节点类名对应的值，作为下一次循环的map
            map = map.get(str);
        }
    }

}
