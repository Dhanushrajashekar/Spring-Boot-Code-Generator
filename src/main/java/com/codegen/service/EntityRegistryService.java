package com.codegen.service;

import com.codegen.model.TableDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EntityRegistryService {

    private final List<TableDefinition> registry = new ArrayList<>();

    public void register(TableDefinition def) {
        registry.removeIf(e -> e.getEntityName().equals(def.getEntityName()));
        registry.add(def);
    }

    public List<String> getEntityNames() {
        return registry.stream().map(TableDefinition::getEntityName).collect(Collectors.toList());
    }

    public List<TableDefinition> getAll() {
        return new ArrayList<>(registry);
    }
}
