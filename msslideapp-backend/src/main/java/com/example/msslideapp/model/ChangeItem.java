package com.example.msslideapp.model;
import java.util.Map; 


public class ChangeItem {
    private String sheet;
    private int row;
    private int col;
    private String oldValue;
    private String newValue;
    private String color;
    private boolean strike;
    private String changeType;
    private Map<String, Object> meta;

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }
    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }
    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isStrike() { return strike; }
    public void setStrike(boolean strike) { this.strike = strike; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
}
