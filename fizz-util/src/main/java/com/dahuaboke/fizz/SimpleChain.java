package com.dahuaboke.fizz;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimpleChain {

    private Map<String, Map> chainsMap = new HashMap<>();

    /**
     * 标注解的列表，为空时不进行过滤；不为空时进行过滤，仅展示annoClass中的节点(链条的第一个节点不进行过滤)
     */
    private Set<String> annoClass;

    private Map<String,Map> annoClassValue;

    private Map<String,Set<String>> classTable = new HashMap<>();


    public SimpleChain(Map<String,Map> annoClassValue) {
        if(annoClassValue != null) {
            this.annoClass = annoClassValue.keySet();
            this.annoClassValue = annoClassValue;
        }

    }

    /**
     * 主要方法
     *
     * @param map 全量链路
     * @return 简化后的链路字符串
     */
    //TODO 异常处理没做太好
    public List<Map> run(Map map) {
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
        List<Map> list = new ArrayList<>();
        endExcute(list,chainsMap);
        return list;
    }

    /**
     * 插入注解信息
     * @param childList 循环中的子节点列表
     * @param childMap 循环中的子节点类名
     */
    private void endExcute(List<Map> childList,Map<String,Map> childMap){
        for (Map.Entry<String, Map> entry : childMap.entrySet()) {
            Map<String,Object> map = new HashMap();
            childList.add(map);
            if(entry.getKey().toLowerCase().endsWith("mapper")){
                map.put("tables",classTable.get(entry.getKey()));
            }
            map.put("className",entry.getKey());
            if(annoClassValue != null){
                map.put("annoValue",annoClassValue.get(entry.getKey().replace("/",".")));
            }
            if(entry.getValue() != null){
                List<Map> list = new ArrayList<>();
                map.put("childs",list);
                endExcute(list,entry.getValue());
            }
        }
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
            if(name.toLowerCase().endsWith("mapper")){
                JSONArray tables = json.getJSONArray("tables");
                if(tables != null && tables.size() > 0){
                    Set<String> set = null;
                    if(classTable.get(name) == null){
                        set = new HashSet<>();
                        classTable.put(name,set);
                    }else{
                        set = classTable.get(name);
                    }
                    for (int i=0;i<tables.size();i++) {
                        set.add(tables.get(i).toString());
                    }
                }
            }
            //获取子方法列表和子接口列表
            JSONArray children = json.getJSONArray("children");
            JSONObject ifChildren = json.getJSONObject("interfaceChildren");
            if (children != null && children.size() > 0) {
                //子方法列表直接带入下一层
                for (int i = 0; i < children.size(); i++) {
                    recursion(chains, children.getJSONObject(i));
                }
            }
            if (ifChildren != null && ifChildren.size() > 0) {
                //子接口列表，把本机name删掉，因为要接口替换为实现类，之后进行递归
//                chains.remove(chains.size() - 1);
                for (String key : ifChildren.keySet()) {
                    JSONArray implArr = ifChildren.getJSONArray(key);
                    for (int j = 0; j < implArr.size(); j++) {
                        recursion(chains, implArr.getJSONObject(j));
                    }
                }
                //最后把接口名重新加上，因为补偿最后的移除操作
//                chains.add(name);
            }
            //如果既没有子方法列表，也没有子接口列表，链路已完整，可以添加到chainsMap中
            if((children == null || children.size() == 0) && (ifChildren == null || ifChildren.size() == 0)) {
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
        //过滤非注解界定，头节点和mapper节点不过滤
        for(int i=0;i<chains.size();i++){
            String className = chains.get(i).replace(".","/");
            if (i == 0 || chains.get(i).toLowerCase().endsWith("mapper") || ((annoClass == null || annoClass.size() == 0 || annoClass.contains(className.replace("/",".")) && !list.get(list.size() - 1).equals(className)))) {
                list.add(className);
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
