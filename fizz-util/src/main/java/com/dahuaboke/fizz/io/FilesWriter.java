package com.dahuaboke.fizz.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FilesWriter implements Writer{

    @Override
    public boolean write(String path, String message) throws IOException {
        File file = new File(path);
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(message);
        writer.flush();
        writer.close();
        return true;
    }

}
