package com.dahuaboke.stest2;

import com.dahuaboke.javaparser.annotation.ValidComponent;

@ValidComponent
public class Test1 {

    public void abc(){
        new Test2().abc();
    }

}
