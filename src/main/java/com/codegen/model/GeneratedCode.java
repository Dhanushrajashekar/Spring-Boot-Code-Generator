package com.codegen.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class GeneratedCode {
    private String entityCode;
    private String repositoryCode;
    private String serviceCode;
    private String controllerCode;
    private String dtoCode;
    private String exceptionHandlerCode;
    private Map<String, String> enumCodes = new HashMap<>();
    private String sqlPreview;
}
