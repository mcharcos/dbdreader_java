package com.glider;

import java.io.IOException;
import java.io.RandomAccessFile;

public class Main {
    public static void main(String[] args) {
        String filename = "data/amadeus-2014-204-05-000.sbd"; // Replace with your compressed file path

        try {
            DBDReaderFile dbd = new DBDReaderFile();
            RandomAccessFile f= dbd.openDbdFile(filename);
            dbd.closeDbdFile(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
