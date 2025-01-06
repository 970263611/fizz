package com.dahuaboke.fizz.io;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FilesSend {

    public boolean send(String url ,ChainInfo chainInfo) {
        try {
            URL urls = new URL(url);
            HttpURLConnection con = (HttpURLConnection) urls.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type","application/json");
            con.setDoOutput(true);
            String s = JSONObject.toJSONString(chainInfo);
            try(DataOutputStream dos = new DataOutputStream(con.getOutputStream())){
                dos.writeBytes(s);
                dos.flush();
            }
            int responseCode = con.getResponseCode();
            if(responseCode == 200){
                try(BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))){
                    String rs;
                    StringBuilder sb = new StringBuilder();
                    while((rs = br.readLine()) != null){
                        sb.append(rs);
                    }
                    JSONObject json = JSON.parseObject(rs);
                    if("00".equals(json.getString("code"))){
                        return true;
                    }
                }
            }
            con.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void main(String[] args) throws IOException {
        ChainInfo chainInfo = new ChainInfo();
        chainInfo.setProject("aaa");
        chainInfo.setModule("bbb");
        chainInfo.setVersion("1.0.0");
        chainInfo.setChainStr("hahaha");
        new FilesSend().send("http://localhost:10001/fizz/addchain",chainInfo);
    }

}
