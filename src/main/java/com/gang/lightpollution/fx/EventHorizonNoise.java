package com.gang.lightpollution.fx;

import java.util.Random;

/** Deterministic periodic lattice for the trace shader's two-channel 3-D noise lookup. */
public final class EventHorizonNoise {
    public static final int SIZE = 256;
    private EventHorizonNoise() {}

    public static byte[] rgba() {
        byte[] lattice = new byte[SIZE * SIZE];
        new Random(0x15_75_52L).nextBytes(lattice);
        byte[] pixels = new byte[SIZE * SIZE * 4];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int index = (y * SIZE + x) * 4;
            pixels[index] = lattice[((y + 17) & 255) * SIZE + ((x + 37) & 255)];
            pixels[index + 1] = lattice[y * SIZE + x];
            pixels[index + 2] = 0;
            pixels[index + 3] = (byte) 255;
        }
        return pixels;
    }
}
