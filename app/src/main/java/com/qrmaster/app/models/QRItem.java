package com.qrmaster.app.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "qr_items")
public class QRItem {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String content;
    private String type;
    private long timestamp;
    private boolean isSaved;
    private boolean isGenerated;
    private String colorForeground;
    private String colorBackground;

    public QRItem(String content, String type, long timestamp) {
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
        this.isSaved = false;
        this.isGenerated = false;
        this.colorForeground = "#000000";
        this.colorBackground = "#FFFFFF";
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public boolean isSaved() { return isSaved; }
    public void setSaved(boolean saved) { isSaved = saved; }
    
    public boolean isGenerated() { return isGenerated; }
    public void setGenerated(boolean generated) { isGenerated = generated; }
    
    public String getColorForeground() { return colorForeground; }
    public void setColorForeground(String color) { this.colorForeground = color; }
    
    public String getColorBackground() { return colorBackground; }
    public void setColorBackground(String color) { this.colorBackground = color; }
}