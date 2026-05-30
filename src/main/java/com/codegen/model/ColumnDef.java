package com.codegen.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ColumnDef {
    private String name;
    private String type;
    private boolean nullable = true;
    private boolean unique = false;
    private Integer length;
    private boolean text = false;
    private boolean creationTimestamp = false;
    private boolean updateTimestamp = false;
    private String enumClassName;
    private List<String> enumValues = new ArrayList<>();
    private List<String> validations = new ArrayList<>();
}
