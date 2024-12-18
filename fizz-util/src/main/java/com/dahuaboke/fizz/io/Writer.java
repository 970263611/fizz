package com.dahuaboke.fizz.io;

import java.io.IOException;

public interface Writer {

    boolean write(String path, String message) throws IOException;

}
