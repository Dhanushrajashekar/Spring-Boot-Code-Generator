package com.codegen.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ForeignKeyDef {
    private String fieldName;
    private String targetEntity;
    private String relationshipType = "ManyToOne";
    private String fetchType = "LAZY";
    private boolean nullable = true;
    private String cascadeType = "NONE";
    private String mappedBy;
    private List<String> jsonIgnoreProperties = new ArrayList<>();
}
