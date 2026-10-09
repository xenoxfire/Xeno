package com.example;

interface IXenoTimeService {
    void destroy();
    boolean shiftTime(long deltaMs);
    boolean setTime(long epochMs);
    boolean setAutoTime(int enabled);
    boolean triggerPaste();
}
