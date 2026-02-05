package com.abster.camerastream;

import android.util.Size;

public enum StreamQuality {
    LOW(new Size(640, 480), 50),
    MEDIUM(new Size(1280, 720), 70),
    HIGH(new Size(1920, 1080), 90);

    private final Size resolution;
    private final int jpegQuality;

    StreamQuality(Size resolution, int jpegQuality) {
        this.resolution = resolution;
        this.jpegQuality = jpegQuality;
    }

    public Size getResolution() {
        return resolution;
    }

    public int getJpegQuality() {
        return jpegQuality;
    }

    public String getLabel() {
        return resolution.getWidth() + "x" + resolution.getHeight();
    }
}
