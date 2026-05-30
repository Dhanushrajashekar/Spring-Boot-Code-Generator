package com.codegen.service;

import com.codegen.model.ColumnDef;
import com.codegen.model.ForeignKeyDef;
import com.codegen.model.GeneratedCode;
import com.codegen.model.TableDefinition;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CodeGeneratorService {

    public GeneratedCode generate(TableDefinition def) {
        GeneratedCode code = new GeneratedCode();
        code.setEntityCode(generateEntity(def));
        code.setRepositoryCode(generateRepository(def));
        code.setServiceCode(generateService(def));
        code.setControllerCode(generateController(def));
        code.setDtoCode(generateDto(def));
        code.setExceptionHandlerCode(generateExceptionHandler(def));
        code.setEnumCodes(generateEnums(def));
        code.setSqlPreview(generateSqlPreview(def));
        return code;
    }

    // ──────────────────────────────────────────────────────────────
    // ENTITY
    // ──────────────────────────────────────────────────────────────

    private String generateEntity(TableDefinition def) {
        Set<String> imports = new TreeSet<>();
        StringBuilder fields = new StringBuilder();

        imports.add("jakarta.persistence.Column");
        imports.add("jakarta.persistence.Entity");
        imports.add("jakarta.persistence.GeneratedValue");
        imports.add("jakarta.persistence.GenerationType");
        imports.add("jakarta.persistence.Id");
        imports.add("jakarta.persistence.Table");
        imports.add("lombok.AllArgsConstructor");
        imports.add("lombok.Data");
        imports.add("lombok.NoArgsConstructor");

        if ("UUID".equals(def.getIdType())) {
            imports.add("java.util.UUID");
            fields.append("    @Id\n");
            fields.append("    @GeneratedValue(strategy = GenerationType.UUID)\n");
            fields.append("    private UUID id;\n\n");
        } else {
            fields.append("    @Id\n");
            fields.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
            fields.append("    private Long id;\n\n");
        }

        for (ColumnDef col : def.getColumns()) {
            if (col.getName() == null || col.getName().isBlank()) continue;
            addTypeImport(col.getType(), imports);

            if (col.isCreationTimestamp()) {
                imports.add("org.hibernate.annotations.CreationTimestamp");
                fields.append("    @CreationTimestamp\n");
            }
            if (col.isUpdateTimestamp()) {
                imports.add("org.hibernate.annotations.UpdateTimestamp");
                fields.append("    @UpdateTimestamp\n");
            }
            if ("Enum".equals(col.getType())) {
                imports.add("jakarta.persistence.Enumerated");
                imports.add("jakarta.persistence.EnumType");
                fields.append("    @Enumerated(EnumType.STRING)\n");
            }

            // Validation annotations
            if (col.getValidations() != null) {
                for (String v : col.getValidations()) {
                    switch (v) {
                        case "NotBlank" -> {
                            imports.add("jakarta.validation.constraints.NotBlank");
                            fields.append("    @NotBlank\n");
                        }
                        case "NotNull" -> {
                            imports.add("jakarta.validation.constraints.NotNull");
                            fields.append("    @NotNull\n");
                        }
                        case "Email" -> {
                            imports.add("jakarta.validation.constraints.Email");
                            fields.append("    @Email\n");
                        }
                        case "Size" -> {
                            imports.add("jakarta.validation.constraints.Size");
                            fields.append("    @Size(min = 1, max = 255)\n");
                        }
                        case "Min" -> {
                            imports.add("jakarta.validation.constraints.Min");
                            fields.append("    @Min(0)\n");
                        }
                        case "Max" -> {
                            imports.add("jakarta.validation.constraints.Max");
                            fields.append("    @Max(9999)\n");
                        }
                    }
                }
            }

            List<String> colAttrs = new ArrayList<>();
            colAttrs.add("name = \"" + toSnakeCase(col.getName()) + "\"");
            if (!col.isNullable()) colAttrs.add("nullable = false");
            if (col.isUnique())    colAttrs.add("unique = true");
            if (col.isText())      colAttrs.add("columnDefinition = \"TEXT\"");
            if ("String".equals(col.getType()) && col.getLength() != null && col.getLength() > 0) {
                colAttrs.add("length = " + col.getLength());
            }

            fields.append("    @Column(").append(String.join(", ", colAttrs)).append(")\n");

            String javaType = "Enum".equals(col.getType())
                ? (col.getEnumClassName() != null && !col.getEnumClassName().isBlank() ? col.getEnumClassName() : "YourEnumType")
                : col.getType();
            fields.append("    private ").append(javaType).append(" ").append(col.getName()).append(";\n\n");
        }

        for (ForeignKeyDef fk : def.getForeignKeys()) {
            if (fk.getFieldName() == null || fk.getFieldName().isBlank()) continue;
            generateFkField(fk, def.getEntityName(), imports, fields);
        }

        StringBuilder code = new StringBuilder();
        code.append("package ").append(def.getPackageName()).append(".entity;\n\n");
        imports.forEach(imp -> code.append("import ").append(imp).append(";\n"));
        code.append("\n");
        code.append("@Entity\n");
        code.append("@Table(name = \"").append(def.getTableName()).append("\")\n");
        code.append("@Data\n");
        code.append("@NoArgsConstructor\n");
        code.append("@AllArgsConstructor\n");
        code.append("public class ").append(def.getEntityName()).append(" {\n\n");
        code.append(fields);
        code.append("}\n");
        return code.toString();
    }

    private void generateFkField(ForeignKeyDef fk, String ownerEntity, Set<String> imports, StringBuilder fields) {
        switch (fk.getRelationshipType()) {

            case "ManyToOne" -> {
                imports.add("jakarta.persistence.FetchType");
                imports.add("jakarta.persistence.JoinColumn");
                imports.add("jakarta.persistence.ManyToOne");
                fields.append("    @ManyToOne(fetch = FetchType.").append(fk.getFetchType()).append(")\n");
                fields.append("    @JoinColumn(name = \"").append(toSnakeCase(fk.getFieldName())).append("_id\"");
                if (!fk.isNullable()) fields.append(", nullable = false");
                fields.append(")\n");
                appendJsonIgnore(fk, imports, fields);
                fields.append("    private ").append(fk.getTargetEntity()).append(" ").append(fk.getFieldName()).append(";\n\n");
            }

            case "OneToMany" -> {
                imports.add("jakarta.persistence.FetchType");
                imports.add("jakarta.persistence.OneToMany");
                imports.add("java.util.List");
                String mappedBy = (fk.getMappedBy() != null && !fk.getMappedBy().isBlank())
                    ? fk.getMappedBy() : toLowerFirst(ownerEntity);
                fields.append("    @OneToMany(mappedBy = \"").append(mappedBy).append("\"");
                if (fk.getCascadeType() != null && !"NONE".equals(fk.getCascadeType())) {
                    imports.add("jakarta.persistence.CascadeType");
                    fields.append(", cascade = CascadeType.").append(fk.getCascadeType());
                }
                fields.append(", fetch = FetchType.").append(fk.getFetchType()).append(")\n");
                appendJsonIgnore(fk, imports, fields);
                fields.append("    private List<").append(fk.getTargetEntity()).append("> ").append(fk.getFieldName()).append(";\n\n");
            }

            case "OneToOne" -> {
                imports.add("jakarta.persistence.FetchType");
                imports.add("jakarta.persistence.OneToOne");
                boolean isInverseSide = fk.getMappedBy() != null && !fk.getMappedBy().isBlank();
                fields.append("    @OneToOne(fetch = FetchType.").append(fk.getFetchType());
                if (isInverseSide) fields.append(", mappedBy = \"").append(fk.getMappedBy()).append("\"");
                if (fk.getCascadeType() != null && !"NONE".equals(fk.getCascadeType())) {
                    imports.add("jakarta.persistence.CascadeType");
                    fields.append(", cascade = CascadeType.").append(fk.getCascadeType());
                }
                fields.append(")\n");
                if (!isInverseSide) {
                    imports.add("jakarta.persistence.JoinColumn");
                    fields.append("    @JoinColumn(name = \"").append(toSnakeCase(fk.getFieldName())).append("_id\"");
                    if (!fk.isNullable()) fields.append(", nullable = false");
                    fields.append(")\n");
                }
                appendJsonIgnore(fk, imports, fields);
                fields.append("    private ").append(fk.getTargetEntity()).append(" ").append(fk.getFieldName()).append(";\n\n");
            }

            case "ManyToMany" -> {
                imports.add("jakarta.persistence.FetchType");
                imports.add("jakarta.persistence.JoinColumn");
                imports.add("jakarta.persistence.JoinTable");
                imports.add("jakarta.persistence.ManyToMany");
                imports.add("java.util.List");
                fields.append("    @ManyToMany(fetch = FetchType.").append(fk.getFetchType()).append(")\n");
                fields.append("    @JoinTable(\n");
                fields.append("        name = \"").append(toSnakeCase(ownerEntity)).append("_").append(toSnakeCase(fk.getTargetEntity())).append("\",\n");
                fields.append("        joinColumns = @JoinColumn(name = \"").append(toSnakeCase(ownerEntity)).append("_id\"),\n");
                fields.append("        inverseJoinColumns = @JoinColumn(name = \"").append(toSnakeCase(fk.getTargetEntity())).append("_id\")\n");
                fields.append("    )\n");
                appendJsonIgnore(fk, imports, fields);
                fields.append("    private List<").append(fk.getTargetEntity()).append("> ").append(fk.getFieldName()).append(";\n\n");
            }
        }
    }

    private void appendJsonIgnore(ForeignKeyDef fk, Set<String> imports, StringBuilder fields) {
        List<String> props = fk.getJsonIgnoreProperties();
        if (props != null && !props.isEmpty()) {
            imports.add("com.fasterxml.jackson.annotation.JsonIgnoreProperties");
            fields.append("    @JsonIgnoreProperties({");
            fields.append(props.stream().map(p -> "\"" + p.trim() + "\"").collect(Collectors.joining(", ")));
            fields.append("})\n");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // REPOSITORY
    // ──────────────────────────────────────────────────────────────

    private String generateRepository(TableDefinition def) {
        String entity = def.getEntityName();
        String pkg = def.getPackageName();
        String idType = def.getIdType();

        StringBuilder code = new StringBuilder();
        code.append("package ").append(pkg).append(".repository;\n\n");
        code.append("import ").append(pkg).append(".entity.").append(entity).append(";\n");
        code.append("import org.springframework.data.jpa.repository.JpaRepository;\n");
        if (def.isUsePagination()) {
            code.append("import org.springframework.data.domain.Page;\n");
            code.append("import org.springframework.data.domain.Pageable;\n");
        }
        code.append("import org.springframework.stereotype.Repository;\n");
        if ("UUID".equals(idType)) code.append("import java.util.UUID;\n");
        code.append("\n@Repository\n");
        code.append("public interface ").append(entity).append("Repository");
        code.append(" extends JpaRepository<").append(entity).append(", ").append(idType).append("> {\n");
        if (def.isUsePagination()) {
            code.append("\n    // JpaRepository inherits findAll(Pageable) from PagingAndSortingRepository\n");
            code.append("    // Add custom paginated queries here if needed\n");
        }
        code.append("}\n");
        return code.toString();
    }

    // ──────────────────────────────────────────────────────────────
    // SERVICE
    // ──────────────────────────────────────────────────────────────

    private String generateService(TableDefinition def) {
        String entity = def.getEntityName();
        String eVar = toLowerFirst(entity);
        String idType = def.getIdType();
        String pkg = def.getPackageName();

        StringBuilder code = new StringBuilder();
        code.append("package ").append(pkg).append(".service;\n\n");
        code.append("import ").append(pkg).append(".entity.").append(entity).append(";\n");
        code.append("import ").append(pkg).append(".repository.").append(entity).append("Repository;\n");
        code.append("import lombok.RequiredArgsConstructor;\n");
        code.append("import org.springframework.stereotype.Service;\n");
        code.append("import org.springframework.transaction.annotation.Transactional;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Optional;\n");
        if (def.isUsePagination()) {
            code.append("import org.springframework.data.domain.Page;\n");
            code.append("import org.springframework.data.domain.Pageable;\n");
        }
        if ("UUID".equals(idType)) code.append("import java.util.UUID;\n");
        code.append("\n@Service\n@RequiredArgsConstructor\n");
        code.append("public class ").append(entity).append("Service {\n\n");
        code.append("    private final ").append(entity).append("Repository ").append(eVar).append("Repository;\n\n");

        code.append("    @Transactional(readOnly = true)\n");
        code.append("    public List<").append(entity).append("> findAll() {\n");
        code.append("        return ").append(eVar).append("Repository.findAll();\n");
        code.append("    }\n\n");

        if (def.isUsePagination()) {
            code.append("    @Transactional(readOnly = true)\n");
            code.append("    public Page<").append(entity).append("> findAll(Pageable pageable) {\n");
            code.append("        return ").append(eVar).append("Repository.findAll(pageable);\n");
            code.append("    }\n\n");
        }

        code.append("    @Transactional(readOnly = true)\n");
        code.append("    public Optional<").append(entity).append("> findById(").append(idType).append(" id) {\n");
        code.append("        return ").append(eVar).append("Repository.findById(id);\n");
        code.append("    }\n\n");

        code.append("    @Transactional\n");
        code.append("    public ").append(entity).append(" save(").append(entity).append(" ").append(eVar).append(") {\n");
        code.append("        return ").append(eVar).append("Repository.save(").append(eVar).append(");\n");
        code.append("    }\n\n");

        code.append("    @Transactional\n");
        code.append("    public void deleteById(").append(idType).append(" id) {\n");
        code.append("        ").append(eVar).append("Repository.deleteById(id);\n");
        code.append("    }\n");
        code.append("}\n");
        return code.toString();
    }

    // ──────────────────────────────────────────────────────────────
    // CONTROLLER
    // ──────────────────────────────────────────────────────────────

    private String generateController(TableDefinition def) {
        String entity = def.getEntityName();
        String eVar = toLowerFirst(entity);
        String idType = def.getIdType();
        String pkg = def.getPackageName();
        String urlPath = "/" + toPlural(toSnakeCase(entity)).replace("_", "-");

        StringBuilder code = new StringBuilder();
        code.append("package ").append(pkg).append(".controller;\n\n");
        code.append("import ").append(pkg).append(".entity.").append(entity).append(";\n");
        code.append("import ").append(pkg).append(".service.").append(entity).append("Service;\n");
        code.append("import jakarta.validation.Valid;\n");
        code.append("import lombok.RequiredArgsConstructor;\n");
        code.append("import org.springframework.http.ResponseEntity;\n");
        code.append("import org.springframework.web.bind.annotation.*;\n");
        code.append("import java.util.List;\n");
        if (def.isUsePagination()) {
            code.append("import org.springframework.data.domain.Page;\n");
            code.append("import org.springframework.data.domain.Pageable;\n");
            code.append("import org.springframework.data.web.PageableDefault;\n");
        }
        if ("UUID".equals(idType)) code.append("import java.util.UUID;\n");
        code.append("\n// Note: @Valid requires spring-boot-starter-validation in pom.xml\n");
        code.append("@RestController\n");
        code.append("@RequestMapping(\"").append(urlPath).append("\")\n");
        code.append("@RequiredArgsConstructor\n");
        code.append("public class ").append(entity).append("Controller {\n\n");
        code.append("    private final ").append(entity).append("Service ").append(eVar).append("Service;\n\n");

        code.append("    @GetMapping\n");
        code.append("    public ResponseEntity<List<").append(entity).append(">> getAll() {\n");
        code.append("        return ResponseEntity.ok(").append(eVar).append("Service.findAll());\n");
        code.append("    }\n\n");

        if (def.isUsePagination()) {
            code.append("    @GetMapping(\"/page\")\n");
            code.append("    public ResponseEntity<Page<").append(entity).append(">> getPage(\n");
            code.append("            @PageableDefault(size = 20) Pageable pageable) {\n");
            code.append("        return ResponseEntity.ok(").append(eVar).append("Service.findAll(pageable));\n");
            code.append("    }\n\n");
        }

        code.append("    @GetMapping(\"/{id}\")\n");
        code.append("    public ResponseEntity<").append(entity).append("> getById(@PathVariable ").append(idType).append(" id) {\n");
        code.append("        return ").append(eVar).append("Service.findById(id)\n");
        code.append("                .map(ResponseEntity::ok)\n");
        code.append("                .orElse(ResponseEntity.notFound().build());\n");
        code.append("    }\n\n");

        code.append("    @PostMapping\n");
        code.append("    public ResponseEntity<").append(entity).append("> create(@Valid @RequestBody ").append(entity).append(" ").append(eVar).append(") {\n");
        code.append("        return ResponseEntity.ok(").append(eVar).append("Service.save(").append(eVar).append("));\n");
        code.append("    }\n\n");

        code.append("    @PutMapping(\"/{id}\")\n");
        code.append("    public ResponseEntity<").append(entity).append("> update(@PathVariable ").append(idType).append(" id, @Valid @RequestBody ").append(entity).append(" ").append(eVar).append(") {\n");
        code.append("        ").append(eVar).append(".setId(id);\n");
        code.append("        return ResponseEntity.ok(").append(eVar).append("Service.save(").append(eVar).append("));\n");
        code.append("    }\n\n");

        code.append("    @DeleteMapping(\"/{id}\")\n");
        code.append("    public ResponseEntity<Void> delete(@PathVariable ").append(idType).append(" id) {\n");
        code.append("        ").append(eVar).append("Service.deleteById(id);\n");
        code.append("        return ResponseEntity.noContent().build();\n");
        code.append("    }\n");
        code.append("}\n");
        return code.toString();
    }

    // ──────────────────────────────────────────────────────────────
    // DTO
    // ──────────────────────────────────────────────────────────────

    private String generateDto(TableDefinition def) {
        String entity = def.getEntityName();
        String pkg = def.getPackageName();
        Set<String> imports = new TreeSet<>();
        StringBuilder fields = new StringBuilder();

        imports.add("lombok.AllArgsConstructor");
        imports.add("lombok.Data");
        imports.add("lombok.NoArgsConstructor");

        String idType = def.getIdType();
        if ("UUID".equals(idType)) imports.add("java.util.UUID");
        fields.append("    private ").append(idType).append(" id;\n");

        for (ColumnDef col : def.getColumns()) {
            if (col.getName() == null || col.getName().isBlank()) continue;
            addTypeImport(col.getType(), imports);
            String javaType = "Enum".equals(col.getType())
                ? (col.getEnumClassName() != null && !col.getEnumClassName().isBlank() ? col.getEnumClassName() : "String")
                : col.getType();
            fields.append("    private ").append(javaType).append(" ").append(col.getName()).append(";\n");
        }

        for (ForeignKeyDef fk : def.getForeignKeys()) {
            if (fk.getFieldName() == null || fk.getFieldName().isBlank()) continue;
            if ("ManyToOne".equals(fk.getRelationshipType()) || "OneToOne".equals(fk.getRelationshipType())) {
                fields.append("    private Long ").append(fk.getFieldName()).append("Id;\n");
            }
        }

        StringBuilder code = new StringBuilder();
        code.append("package ").append(pkg).append(".dto;\n\n");
        imports.forEach(imp -> code.append("import ").append(imp).append(";\n"));
        code.append("\n@Data\n@NoArgsConstructor\n@AllArgsConstructor\n");
        code.append("public class ").append(entity).append("DTO {\n\n");
        code.append(fields);
        code.append("}\n");
        return code.toString();
    }

    // ──────────────────────────────────────────────────────────────
    // EXCEPTION HANDLER
    // ──────────────────────────────────────────────────────────────

    private String generateExceptionHandler(TableDefinition def) {
        String pkg = def.getPackageName();
        StringBuilder code = new StringBuilder();
        code.append("package ").append(pkg).append(".exception;\n\n");
        code.append("import jakarta.persistence.EntityNotFoundException;\n");
        code.append("import org.springframework.http.HttpStatus;\n");
        code.append("import org.springframework.http.ResponseEntity;\n");
        code.append("import org.springframework.validation.FieldError;\n");
        code.append("import org.springframework.web.bind.MethodArgumentNotValidException;\n");
        code.append("import org.springframework.web.bind.annotation.ExceptionHandler;\n");
        code.append("import org.springframework.web.bind.annotation.RestControllerAdvice;\n");
        code.append("import java.util.HashMap;\n");
        code.append("import java.util.Map;\n");
        code.append("\n@RestControllerAdvice\n");
        code.append("public class GlobalExceptionHandler {\n\n");
        code.append("    @ExceptionHandler(EntityNotFoundException.class)\n");
        code.append("    public ResponseEntity<Map<String, String>> handleEntityNotFound(EntityNotFoundException ex) {\n");
        code.append("        return ResponseEntity.status(HttpStatus.NOT_FOUND)\n");
        code.append("                .body(Map.of(\"error\", ex.getMessage()));\n");
        code.append("    }\n\n");
        code.append("    @ExceptionHandler(MethodArgumentNotValidException.class)\n");
        code.append("    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {\n");
        code.append("        Map<String, String> errors = new HashMap<>();\n");
        code.append("        ex.getBindingResult().getAllErrors().forEach(error -> {\n");
        code.append("            String fieldName = ((FieldError) error).getField();\n");
        code.append("            String message = error.getDefaultMessage();\n");
        code.append("            errors.put(fieldName, message);\n");
        code.append("        });\n");
        code.append("        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);\n");
        code.append("    }\n\n");
        code.append("    @ExceptionHandler(Exception.class)\n");
        code.append("    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {\n");
        code.append("        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)\n");
        code.append("                .body(Map.of(\"error\", \"An unexpected error occurred.\",\n");
        code.append("                             \"details\", ex.getMessage()));\n");
        code.append("    }\n");
        code.append("}\n");
        return code.toString();
    }

    // ──────────────────────────────────────────────────────────────
    // ENUMS
    // ──────────────────────────────────────────────────────────────

    private Map<String, String> generateEnums(TableDefinition def) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ColumnDef col : def.getColumns()) {
            if (!"Enum".equals(col.getType())) continue;
            String className = col.getEnumClassName();
            if (className == null || className.isBlank()) continue;
            if (result.containsKey(className)) continue;

            StringBuilder code = new StringBuilder();
            code.append("package ").append(def.getPackageName()).append(".model;\n\n");
            code.append("public enum ").append(className).append(" {\n\n");
            if (col.getEnumValues() != null && !col.getEnumValues().isEmpty()) {
                List<String> vals = col.getEnumValues().stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
                if (!vals.isEmpty()) {
                    code.append("    ").append(String.join(",\n    ", vals)).append("\n");
                }
            } else {
                code.append("    // Define enum constants here\n");
            }
            code.append("}\n");
            result.put(className, code.toString());
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // SQL PREVIEW
    // ──────────────────────────────────────────────────────────────

    private String generateSqlPreview(TableDefinition def) {
        String table   = def.getTableName();
        String idType  = def.getIdType();
        String entity  = def.getEntityName();

        // Build the SELECT column list
        List<String> selectCols = new ArrayList<>();
        selectCols.add("id");
        for (ColumnDef col : def.getColumns()) {
            if (col.getName() == null || col.getName().isBlank()) continue;
            selectCols.add(toSnakeCase(col.getName()));
        }
        for (ForeignKeyDef fk : def.getForeignKeys()) {
            if (fk.getFieldName() == null || fk.getFieldName().isBlank()) continue;
            if ("ManyToOne".equals(fk.getRelationshipType()) || "OneToOne".equals(fk.getRelationshipType())) {
                selectCols.add(toSnakeCase(fk.getFieldName()) + "_id");
            }
        }
        String colList = String.join(", ", selectCols);

        // Non-id columns for INSERT / UPDATE
        List<String> writeCols = selectCols.subList(1, selectCols.size());
        String insertCols   = String.join(", ", writeCols);
        String insertParams = writeCols.stream().map(c -> "?").collect(Collectors.joining(", "));
        String setClauses   = writeCols.stream().map(c -> c + " = ?").collect(Collectors.joining(",\n       "));

        StringBuilder s = new StringBuilder();
        s.append("-- ─────────────────────────────────────────────────────────\n");
        s.append("-- SQL Preview: ").append(entity).append("\n");
        s.append("-- Queries issued by Spring Data JPA at runtime\n");
        s.append("-- ─────────────────────────────────────────────────────────\n\n");

        // findAll()
        s.append("-- findAll()\n");
        s.append("-- Loads every row. Avoid on large tables — use pagination instead.\n");
        s.append("SELECT ").append(colList).append("\n");
        s.append("FROM ").append(table).append(";\n\n");

        // findById()
        s.append("-- findById(").append(idType).append(" id)\n");
        s.append("-- Returns Optional<").append(entity).append(">. Empty if the id does not exist.\n");
        s.append("SELECT ").append(colList).append("\n");
        s.append("FROM ").append(table).append("\n");
        s.append("WHERE id = ?;\n\n");

        // save() — INSERT
        s.append("-- save(").append(entity).append(" entity)  →  INSERT  [when entity.getId() is null]\n");
        s.append("-- Spring checks the id field. Null id = new record.\n");
        s.append("INSERT INTO ").append(table).append(" (").append(insertCols).append(")\n");
        s.append("VALUES (").append(insertParams).append(");\n\n");

        // save() — UPDATE
        s.append("-- save(").append(entity).append(" entity)  →  UPDATE  [when entity.getId() is set]\n");
        s.append("-- Replaces ALL column values. Partial updates require a custom @Query.\n");
        s.append("UPDATE ").append(table).append("\n");
        s.append("SET    ").append(setClauses).append("\n");
        s.append("WHERE  id = ?;\n\n");

        // deleteById()
        s.append("-- deleteById(").append(idType).append(" id)\n");
        s.append("-- Spring first issues a SELECT to load the entity, then DELETE.\n");
        s.append("SELECT ").append(colList).append("\n");
        s.append("FROM ").append(table).append("\n");
        s.append("WHERE id = ?;\n\n");
        s.append("DELETE FROM ").append(table).append("\n");
        s.append("WHERE id = ?;\n\n");

        // Pagination
        if (def.isUsePagination()) {
            s.append("-- findAll(Pageable pageable)  →  example: page=0, size=20, sort=id ASC\n");
            s.append("-- Spring issues two queries: one for the data, one for the total count.\n");
            s.append("SELECT ").append(colList).append("\n");
            s.append("FROM ").append(table).append("\n");
            s.append("ORDER BY id ASC\n");
            s.append("LIMIT 20 OFFSET 0;\n\n");
            s.append("SELECT COUNT(*)\n");
            s.append("FROM ").append(table).append(";\n\n");
        }

        // FK-derived queries
        for (ForeignKeyDef fk : def.getForeignKeys()) {
            if (fk.getFieldName() == null || fk.getFieldName().isBlank()) continue;

            if ("ManyToOne".equals(fk.getRelationshipType())) {
                String fkCol    = toSnakeCase(fk.getFieldName()) + "_id";
                String method   = "findBy" + toUpperFirst(fk.getFieldName());
                String paramType = fk.getTargetEntity() != null ? fk.getTargetEntity() : "Entity";
                s.append("-- ").append("List<").append(entity).append("> ").append(method)
                 .append("(").append(paramType).append(" ").append(fk.getFieldName()).append(")\n");
                s.append("-- Derived query — Spring parses the method name automatically.\n");
                s.append("-- Spring extracts ").append(paramType).append(".getId() and binds it as the parameter.\n");
                s.append("SELECT ").append(colList).append("\n");
                s.append("FROM ").append(table).append("\n");
                s.append("WHERE ").append(fkCol).append(" = ?;\n\n");

                // OrderBy variant
                String methodOrdered = method + "OrderByCreatedAtAsc";
                s.append("-- List<").append(entity).append("> ").append(methodOrdered)
                 .append("(").append(paramType).append(" ").append(fk.getFieldName()).append(")\n");
                s.append("-- Spring appends ORDER BY from the method name suffix.\n");
                s.append("SELECT ").append(colList).append("\n");
                s.append("FROM ").append(table).append("\n");
                s.append("WHERE ").append(fkCol).append(" = ?\n");
                s.append("ORDER BY created_at ASC;\n\n");
            }

            if ("OneToMany".equals(fk.getRelationshipType())) {
                String ownerFkCol = toSnakeCase(entity) + "_id";
                String method     = "findBy" + toUpperFirst(fk.getFieldName().replaceAll("s$", ""));
                s.append("-- ").append("List<").append(fk.getTargetEntity()).append("> accessed via ").append(fk.getFieldName()).append("\n");
                s.append("-- Spring loads the collection when you access ").append(entity).append(".get")
                 .append(toUpperFirst(fk.getFieldName())).append("()\n");
                s.append("SELECT *\n");
                s.append("FROM ").append(toSnakeCase(fk.getTargetEntity())).append("\n");
                s.append("WHERE ").append(ownerFkCol).append(" = ?;\n\n");
            }

            if ("ManyToMany".equals(fk.getRelationshipType())) {
                String joinTable = toSnakeCase(entity) + "_" + toSnakeCase(fk.getTargetEntity());
                s.append("-- ").append("List<").append(fk.getTargetEntity()).append("> accessed via ").append(fk.getFieldName()).append("\n");
                s.append("-- Spring joins through the join table.\n");
                s.append("SELECT t.*\n");
                s.append("FROM ").append(toSnakeCase(fk.getTargetEntity())).append(" t\n");
                s.append("INNER JOIN ").append(joinTable).append(" j\n");
                s.append("    ON j.").append(toSnakeCase(fk.getTargetEntity())).append("_id = t.id\n");
                s.append("WHERE j.").append(toSnakeCase(entity)).append("_id = ?;\n\n");
            }
        }

        return s.toString();
    }

    private String toUpperFirst(String word) {
        if (word == null || word.isBlank()) return word;
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    // ──────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────

    private void addTypeImport(String type, Set<String> imports) {
        switch (type) {
            case "LocalDateTime" -> imports.add("java.time.LocalDateTime");
            case "LocalDate"     -> imports.add("java.time.LocalDate");
            case "LocalTime"     -> imports.add("java.time.LocalTime");
            case "BigDecimal"    -> imports.add("java.math.BigDecimal");
            case "UUID"          -> imports.add("java.util.UUID");
        }
    }

    private String toSnakeCase(String name) {
        if (name == null || name.isBlank()) return name;
        return name.replaceAll("([A-Z])", "_$1").toLowerCase().replaceAll("^_", "");
    }

    private String toPlural(String word) {
        if (word == null || word.isBlank()) return word;
        if (word.endsWith("y"))  return word.substring(0, word.length() - 1) + "ies";
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("z")
            || word.endsWith("sh") || word.endsWith("ch")) return word + "es";
        return word + "s";
    }

    private String toLowerFirst(String word) {
        if (word == null || word.isBlank()) return word;
        return Character.toLowerCase(word.charAt(0)) + word.substring(1);
    }
}
