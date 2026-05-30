package com.codegen.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class TableDefinition {
    private String entityName;
    private String tableName;
    private String packageName = "com.example";
    private String idType = "Long";
    private boolean usePagination = false;
    private List<ColumnDef> columns = new ArrayList<>();
    private List<ForeignKeyDef> foreignKeys = new ArrayList<>();
}
