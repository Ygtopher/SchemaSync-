package com.dbtool.util;

import java.util.concurrent.CopyOnWriteArrayList;

public class ThreadSafeHistory {
    private final CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
    public void add(String s) { list.add(0, s); }
}
