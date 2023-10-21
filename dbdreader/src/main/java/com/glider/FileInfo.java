package com.glider;

import java.io.RandomAccessFile;

public class FileInfo {
    public RandomAccessFile fd;  // Assuming you want to use FileInputStream for input streams
    public long binOffset;
    public int n_state_bytes;
    public int n_sensors;
    public int[] byteSizes;

    public FileInfo(RandomAccessFile fd, long binOffset, int n_state_bytes, int n_sensors, int[] byteSizes) {
        this.fd = fd;
        this.binOffset = binOffset;
        this.n_state_bytes = n_state_bytes;
        this.n_sensors = n_sensors;
        this.byteSizes = byteSizes;
    }
}

