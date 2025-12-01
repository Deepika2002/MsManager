package com.example.msslideapp.model;

import java.util.List;

public class UploadResponse {
    private String id;
    private List<ChangeItem> changes;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<ChangeItem> getChanges() { return changes; }
    public void setChanges(List<ChangeItem> changes) { this.changes = changes; }
}
