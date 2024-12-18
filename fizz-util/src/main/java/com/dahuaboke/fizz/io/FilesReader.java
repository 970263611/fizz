package com.dahuaboke.fizz.io;

import java.io.*;

public class FilesReader implements Reader {

    @Override
    public String read(String path) throws IOException {
        File file = new File(path);
        if(!file.exists()) {
            throw new FileNotFoundException(path);
        }
        if(!file.canRead()){
            throw new IOException("Cannot read file " + path);
        }
        if(!file.isFile()){
            throw new IOException("path not a file " + path);
        }
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder sb = new StringBuilder();
        reader.lines().forEach(sb::append);
        reader.close();
        return sb.toString();
    }

}
