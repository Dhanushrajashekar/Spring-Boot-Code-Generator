package com.codegen.controller;

import com.codegen.model.GeneratedCode;
import com.codegen.model.TableDefinition;
import com.codegen.service.CodeGeneratorService;
import com.codegen.service.EntityRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GeneratorApiController {

    private final CodeGeneratorService codeGeneratorService;
    private final EntityRegistryService entityRegistryService;

    @GetMapping("/entities")
    public List<String> getEntities() {
        return entityRegistryService.getEntityNames();
    }

    @GetMapping("/entities/all")
    public List<TableDefinition> getAllEntities() {
        return entityRegistryService.getAll();
    }

    @PostMapping("/generate")
    public GeneratedCode generate(
            @RequestBody TableDefinition def,
            @RequestParam(defaultValue = "false") boolean register) {

        GeneratedCode code = codeGeneratorService.generate(def);
        if (register) {
            entityRegistryService.register(def);
        }
        return code;
    }

    @PostMapping("/generate/zip")
    public ResponseEntity<byte[]> generateZip(
            @RequestBody TableDefinition def,
            @RequestParam(defaultValue = "false") boolean register) throws IOException {

        GeneratedCode code = codeGeneratorService.generate(def);
        if (register) {
            entityRegistryService.register(def);
        }

        String basePath = def.getPackageName().replace(".", "/");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addEntry(zos, basePath + "/entity/" + def.getEntityName() + ".java", code.getEntityCode());
            addEntry(zos, basePath + "/repository/" + def.getEntityName() + "Repository.java", code.getRepositoryCode());
            addEntry(zos, basePath + "/service/" + def.getEntityName() + "Service.java", code.getServiceCode());
            addEntry(zos, basePath + "/controller/" + def.getEntityName() + "Controller.java", code.getControllerCode());
            if (code.getDtoCode() != null && !code.getDtoCode().isBlank()) {
                addEntry(zos, basePath + "/dto/" + def.getEntityName() + "DTO.java", code.getDtoCode());
            }
            if (code.getExceptionHandlerCode() != null && !code.getExceptionHandlerCode().isBlank()) {
                addEntry(zos, basePath + "/exception/GlobalExceptionHandler.java", code.getExceptionHandlerCode());
            }
            if (code.getEnumCodes() != null) {
                for (Map.Entry<String, String> entry : code.getEnumCodes().entrySet()) {
                    addEntry(zos, basePath + "/model/" + entry.getKey() + ".java", entry.getValue());
                }
            }
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + def.getEntityName() + "-generated.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(baos.toByteArray());
    }

    private void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
