package com.glider;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;


public class DBDReaderFile {

    private static final int BLOCKSIZE = 1024; // Define the value of BLOCKSIZE
    private static final DBDCompressFile compress = new DBDCompressFile(); // NOPMD

    public DBDReaderFile() {
            // nothing for now
    }

    public RandomAccessFile openDbdFile(String filename) throws IOException {
        RandomAccessFile fileInputStream;

        boolean compressed = compress.isFileCompressed(filename);

        if (compressed) {
            fileInputStream = compress.openCompressedFile(filename);
        } else {
            File file = new File(filename);
            fileInputStream = new RandomAccessFile(file, "r");
        }

        return fileInputStream;
    }

    public void closeDbdFile(RandomAccessFile fileInputStream) {
        if (fileInputStream != null) {
            try {
                // Close the FileInputStream
                fileInputStream.close();
            } catch (IOException e) {
                // Handle any exceptions that might occur during the closing process.
                e.printStackTrace();
            }
        }
    }

    public double[][][] getVariable(int ti, int[] vi, int nv, FileInfo fileInfo, int returnNans, int[] ndata, int skipInitialLine) {
        int i, j;
        double[][][] data;
        int[] vit;
        int nvt;
        int nti;

        nvt = nv + 1;
        vit = new int[nvt];

        // Create an array of pointers of nv layers, 2 rows, BLOCKSIZE columns
        data = new double[nv][][];

        for (i = 0; i < nv; i++) {
            data[i] = new double[2][BLOCKSIZE];
        }

        // Check whether the operation has succeeded:
        if (data == null) {
            System.out.println("Memory fault!");
            System.exit(1);
        }

        // Insert ti in vi such that vi remains sorted
        for (i = 0; i < nv; i++) {
            if (vi[i] > ti) {
                break;
            }
            vit[i] = vi[i];
        }

        vit[i] = ti; // Inserts ti
        nti = i; // ti is the nti'th variable
        i++;

        for (i = i; i < nv + 1; i++) {
            vit[i] = vi[i - 1];
        }

        getByReadPerByte(nti, vit, nvt, fileInfo, returnNans, data, ndata, skipInitialLine);
        return data;
    }

    private short bswap_s(short val) {
        short retVal = 0;
        for (int i = 0; i < 2; i++) {
            retVal = (short) ((retVal << 8) | ((val >> (i * 8)) & 0xFF));
        }
        return retVal;
    }

    private float bswap_f(float val) {
        int size = Float.BYTES; // Size of a float in bytes
        int intVal = Float.floatToRawIntBits(val); // Convert float to raw integer bits
        int swapped = 0;
        for (int i = 0; i < size; i++) {
            swapped |= ((intVal >> (i * 8)) & 0xFF) << ((size - 1 - i) * 8);
        }
        return Float.intBitsToFloat(swapped); // Convert back to float
    }

    private double bswap_d(double val) {
        int size = Double.BYTES; // Size of a double in bytes
        long longVal = Double.doubleToRawLongBits(val); // Convert double to raw long bits
        long swapped = 0;
        for (int i = 0; i < size; i++) {
            swapped |= ((longVal >> (i * 8)) & 0xFFL) << ((size - 1 - i) * 8);
        }
        return Double.longBitsToDouble(swapped); // Convert back to double
    }


    private byte readKnownCycle(RandomAccessFile raf) throws IOException {
        // The first 2 bytes are 'sa' and are skipped.
        raf.skipBytes(2);

        // Read the two-byte integer (little-endian).
        short twoByteInt = Short.reverseBytes(raf.readShort());

        // Skip the next 12 bytes since we already know the byte order.
        raf.skipBytes(13);

        // If the two-byte integer is 4660, it indicates the byte order is the same as the host.
        if (twoByteInt == 4660) {
            return 0;
        }

        // Otherwise, we need to flip shorts, floats, and doubles when reading.
        return 1;
    }

    private void getByReadPerByte(int nti, int[] vi, int nv, FileInfo fileInfo, int returnNans, double[][][] result, int[] ndata, int skipInitialLine) {
        int chunkSize;
        int[] offsets;
        int[] byteSizes;

        int r;
        int fpEnd, fpCurrent;
        int i, j;

        double[] readResult;
        double[] memoryResult;
        int[] readVi;

        int minOffsetValue;
        int writeData = (skipInitialLine == 0) ? 0 : 1;

        if (returnNans == 1)
            minOffsetValue = -2;
        else
            minOffsetValue = -1;

        byteSizes = new int[nv];
        offsets = new int[nv];
        readResult = new double[nv];
        memoryResult = new double[nv];
        readVi = new int[nv];

        for (i = 0; i < nv - 1; i++) {
            ndata[i] = 0;
        }
        for (i = 0; i < nv; i++) {
            j = vi[i];
            byteSizes[i] = fileInfo.byteSizes[j];
            offsets[i] = 0;
        }

        fileInfo.fd.seek(fileInfo.fd.length());
        fpEnd = fileInfo.fd.getFilePointer();

        fileInfo.fd.seek(fileInfo.binOffset);

        byte flip = this.readKnownCycle(fileInfo.fd);

        while (true) {
            r = readStateBytes(vi, nv, fileInfo, offsets, chunkSize);
            fpCurrent = fileInfo.fd.getFilePointer();

            if (r >= 1) {
                for (i = 0; i < nv; i++) {
                    if (offsets[i] >= 0) {
                        fileInfo.fd.seek(fpCurrent + offsets[i]);
                        readResult[i] = readSensorValue(fileInfo.fd, byteSizes[i], flip);
                        memoryResult[i] = readResult[i];
                    } else if (offsets[i] == -1) {
                        readResult[i] = memoryResult[i];
                    } else if (offsets[i] == -2) {
                        readResult[i] = FILLVALUE;
                    }
                }

                if (writeData == 1) {
                    for (i = 0; i < nv; i++) {
                        if (offsets[i] >= minOffsetValue && i != nti) {
                            j = i - (i > nti ? 1 : 0);
                            addToArray(readResult[nti], readResult[i], result[j], ndata[j]);
                            ndata[j]++;
                        }
                    }
                } else {
                    writeData = 1;
                }
            }

            fpCurrent += chunkSize + 1;

            if (fpCurrent >= fpEnd) {
                break;
            }

            fseek(fileInfo.fd, fpCurrent, 0);
        }

        free(byteSizes);
        free(offsets);
        free(readResult);
        free(memoryResult);
        free(readVi);
    }

    public int readStateBytes(int[] vi, int nvt, FileInfo fileInfo, int[] offsets, int[] chunksize) throws IOException {
        final int bitsPerByte = 8;
        final int bitsPerField = 1;
        final int mask = 1;
        final int updatedField = 1;
        final int sameField = 0;
        final int notSetField = -1;
        final int notFoundField = -2;

        int bitshift = bitsPerByte - bitsPerField;
        int fieldsPerByte = bitsPerByte / bitsPerField;

        chunksize[0] = 0;
        int variableIndex = 0;
        int variableCounter = 0;

        for (int sb = 0; sb < nvt; sb++) {
            offsets[sb] = notFoundField; // Defaults to not found
        }

        for (int sb = 0; sb < fileInfo.nStateBytes; sb++) {
            int c = fileInfo.fd.read();

            for (int fld = 0; fld < fieldsPerByte; fld++) {
                int field = (c >> bitshift) & mask;
                c <<= bitsPerField;

                if (field == updatedField) {
                    int idx = contains(variableIndex, vi, nvt);

                    if (idx != -1) {
                        // The updated value is one of the wanted variables, so record its position
                        offsets[idx] = chunksize[0];
                        variableCounter += 1;
                    }

                    chunksize[0] += fileInfo.byteSizes[variableIndex];
                } else if (field == sameField) {
                    int idx = contains(variableIndex, vi, nvt);

                    if (idx != -1) {
                        // This variable is asked for but has an old value, so it's not being read.
                        // Offset marked -1.
                        offsets[idx] = -1;
                        variableCounter += 1;
                    }
                } else if (field == notSetField) {
                    int idx = contains(variableIndex, vi, nvt);

                    if (idx != -1) {
                        // This variable is asked for but has no value set, so it's not being read.
                        // Offset marked -2.
                        offsets[idx] = -2;
                        variableCounter += 1;
                    }
                }

                variableIndex += 1;
            }
        }

        // If a variable index appears twice in vi, ensure that the offset is copied over.
        if (nvt > 1) {
            for (int i = 1; i < nvt; ++i) {
                if (vi[i] == vi[i - 1]) {
                    offsets[i] = offsets[i - 1];
                }
            }
        }

        // Return the number of variables found.
        return variableCounter;
    }

    public int contains(int q, int[] list, int n) {
        int r = -1;
        for (int i = 0; i < n; i++) {
            if (q == list[i]) {
                r = i;
                break;
            }
        }
        return r;
    }

    public double readSensorValue(DataInputStream dis, int bs, boolean flip) throws IOException {
        double value = 0;
        switch (bs) {
            case 1:
                byte sc = dis.readByte();
                value = (double) sc;
                break;
            case 2:
                short ss = dis.readShort();
                if (flip) {
                    ss = bswap_s(ss);
                }
                value = (double) ss;
                break;
            case 4:
                float sf = dis.readFloat();
                if (flip) {
                    sf = bswap_f(sf);
                }
                value = (double) sf;
                break;
            case 8:
                double sd = dis.readDouble();
                if (flip) {
                    sd = bswap_d(sd);
                }
                value = sd;
                break;
            default:
                System.out.println("Should not be here!!!!");
                System.out.println("byte size: " + bs);
                System.exit(1);
        }
        return value;
    }

    public void addToArray(double t, double x, double[][] r, int size) {
        int nblocks = size / BLOCKSIZE;
        int remainder = size % BLOCKSIZE;

        if (remainder == 0) {
            // We've consumed the last data element, add more.
            int newSize = (nblocks + 1) * BLOCKSIZE;
            for (int i = 0; i < 2; i++) {
                r[i] = Arrays.copyOf(r[i], newSize);
                if (r[i] == null) {
                    System.out.println("Memory fault!");
                    System.exit(1);
                }
            }
        }
        r[0][size] = t;
        r[1][size] = x;
    }
}
