package com.symfony.demo.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the {@code roles} property as a JSON text column, mirroring the Doctrine
 * {@code Types::JSON} mapping on the PHP {@code User} entity (which stores roles as a JSON
 * array in the {@code symfony_demo_user.roles} column).
 */
@Converter
public class RolesConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        List<String> value = attribute == null ? new ArrayList<>() : attribute;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to serialize roles to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to deserialize roles from JSON: " + dbData, e);
        }
    }
}
