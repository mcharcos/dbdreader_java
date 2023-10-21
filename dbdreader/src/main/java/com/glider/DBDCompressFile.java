package com.glider;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.*;

public class DBDCompressFile {

    public static final int CHUNKSIZE = 4096; // Adjust the chunk size as needed

    public DBDCompressFile() {
        // nothing for now
    }

    public boolean isFileCompressed(String filename) {
        String ext = getFilenameExtension(filename);

        return ext != null && (ext.length() > 1) && ext.charAt(1) == 'c' ? true : false;
    }

    public RandomAccessFile openCompressedFile(String filename) {
        String base;
        String extensionDecompressed;
        String extension;

        base = this.getFilenameBase(filename);

        extension = getFilenameExtension(filename);

        extensionDecompressed = extension.substring(0, 1) + 'b';

        base = base + "." + extensionDecompressed;

        RandomAccessFile fpmem = null;

        try {
            fpmem = new RandomAccessFile(base, "r");
        } catch (FileNotFoundException e) {
            // Opening of decompressed file failed, assume it is not there, and so we write it now.
            try {
                FileOutputStream fos = new FileOutputStream(base);
                long uncompressedFileSize = writeCompressedFileToMemory(filename, fos);
                fos.close();

                if (uncompressedFileSize == 0) {
                    fpmem = null;
                } else {
                    // Writing was successful, now reopen the file for reading.
                    fpmem = new RandomAccessFile(base, "r");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return fpmem;
    }

    private String getFilenameBase(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex != -1) {
            return filename.substring(0, dotIndex);
        }
        return filename;
    }


    private String getFilenameExtension(String filename) {
        File file = new File(filename);
        String fileName = file.getName();
        int dotIndex = fileName.indexOf('.');

        if (dotIndex == -1) {
            return ""; // No extension found
        } else {
            return fileName.substring(dotIndex + 1);
        }
    }

    private static ByteArrayOutputStream fopenCompressedFile(String filename) {
        long uncompressedFileSize;

        ByteArrayOutputStream fpmem = new ByteArrayOutputStream();
        uncompressedFileSize = writeCompressedFileToMemory(filename, fpmem);

        if (uncompressedFileSize == 0) {
            fpmem = null;
        }

        return fpmem;
    }


    private static long writeCompressedFileToMemory(String filename, ByteArrayOutputStream outputStream) {
        try {
            FileInputStream fis = new FileInputStream(filename);
            byte[] buffer = new byte[4096]; // Adjust buffer size as needed
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            fis.close();
            return outputStream.size();
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static long writeCompressedFileToMemory(String filename, OutputStream fpmem) {
        FileInputStream fp = null;
        long decompressedBlockSize;
        long compressedFileSize;
        long fileSize = 0;

        try {
            fp = new FileInputStream(filename);
            compressedFileSize = fp.getChannel().size();

            byte[] data = new byte[CHUNKSIZE];
            int bytesRead;

            while ((decompressedBlockSize = decompressBlock(data, fp)) > 0) {
                fileSize += decompressedBlockSize;
                fpmem.write(data, 0, (int) decompressedBlockSize);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fp != null) {
                try {
                    fp.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return fileSize;
    }

    private static int getBlockSize(DataInputStream dataInput) {
        try {
            byte[] buffer = new byte[2];
            dataInput.readFully(buffer);
            int size = ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);
            return size;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static long getFileSize(RandomAccessFile randomAccessFile) {
        try {
            long currentPos = randomAccessFile.getFilePointer();
            randomAccessFile.seek(0);
            long fileSize = randomAccessFile.length();
            randomAccessFile.seek(currentPos);
            return fileSize;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }


    private static int decompressBlock(byte[] data, InputStream fp) {
        int block_size;
        int decompressed_size;
        byte[] buffer;

        block_size = getBlockSize(fp);
        buffer = new byte[block_size];

        try {
            for (int i = 0; i < block_size; i++) {
                int bytesRead = fp.read(buffer, i, 1);
                if (bytesRead == 0) {
                    // Stream ended unexpectedly
                    System.err.println("Unexpected reading error.");
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Decompress the block using LZ4
        LZ4Factory factory = LZ4Factory.fastestInstance();
        LZ4FastDecompressor decompressor = factory.fastDecompressor();
        decompressed_size = decompressor.decompress(buffer, 0, data, 0, block_size);

        return decompressed_size;
    }


}
