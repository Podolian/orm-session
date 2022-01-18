package com.home;

import com.home.annotation.Column;
import com.home.annotation.Table;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class Session {
    private final DataSource dataSource;
    private final Map<EntityKey<?>, Object> lookupCache = new HashMap<>();
    private final Map<EntityKey<?>, Object> initialStateCache = new HashMap<>();

    public <T> T find(Class<T> type, Object id) {
        EntityKey<T> entityKey = new EntityKey<>(type, id);
        Object entity = lookupCache.computeIfAbsent(entityKey, this::searchInDb);
        return type.cast(entity);
    }

    public void close() {
        lookupCache.entrySet().stream()
                .filter(this::entryHasChanged)
                .forEach(this::performUpdate);
    }

    @SneakyThrows
    private void performUpdate(Map.Entry<EntityKey<?>, Object> entry) {
        try (Connection connection = dataSource.getConnection()) {
            String updateSql = String.format("update %s set %s where id = ?",
                    entry.getValue().getClass().getDeclaredAnnotation(Table.class).name(),
                    getUpdateFields(entry.getValue())
            );
            System.out.println("SQL: " + updateSql);
            try (PreparedStatement preparedStatement = connection.prepareStatement(updateSql)) {
                preparedStatement.setObject(1, entry.getKey().id());
                preparedStatement.executeUpdate();
            }
        }
    }

    private String getUpdateFields(Object entity) {
        return Arrays.stream(entity.getClass().getDeclaredFields())
                .peek(f -> f.setAccessible(true))
                .map(extractQueryPartFromField(entity))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.joining(", "));
    }

    private Function<Field, Optional<String>> extractQueryPartFromField(Object entity) {
        return field -> {
            try {
                String queryPart = field.getName() + "= '" + field.get(entity) + "'";
                return Optional.of(queryPart);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        };
    }

    private boolean entryHasChanged(Map.Entry<EntityKey<?>, Object> entry) {
        Object[] initialFields = extractFields(this.initialStateCache.get(entry.getKey()));
        Object[] fieldsToTestForChanges = extractFields(entry.getValue());
        for (int i = 0; i < initialFields.length; i++) {
            if (!fieldsToTestForChanges[i].equals(initialFields[i])) {
                return true;
            }
        }
        return false;
    }

    private Object[] extractFields(Object entity) {
        return Arrays.stream(entity.getClass().getDeclaredFields())
                .sorted(Comparator.comparing(Field::getName))
                .peek(f -> f.setAccessible(true))
                .map(extractFieldValue(entity))
                .toArray();
    }

    private Function<Field, Optional<?>> extractFieldValue(Object entity) {
        return f -> {
            try {
                return Optional.of(f.get(entity));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        };
    }

    @SneakyThrows
    private <T> T searchInDb(EntityKey<T> entityKey) {
        try (Connection connection = dataSource.getConnection()) {
            Class<T> entityType = entityKey.type();
            String preparedSql = prepareSelectSQL(entityType);
            try (PreparedStatement preparedStatement = connection.prepareStatement(preparedSql)) {
                preparedStatement.setObject(1, entityKey.id());
                System.out.println("SQL: " + preparedStatement);
                ResultSet resultSet = preparedStatement.executeQuery();
                return createEntityFrom(entityKey, resultSet);
            }
        }
    }

    @SneakyThrows
    private <T> T createEntityFrom(EntityKey<T> entityKey, ResultSet resultSet) {
        Class<T> type = entityKey.type();
        resultSet.next();
        Field[] declaredFields = Arrays.stream(type.getDeclaredFields())
                .sorted(Comparator.comparing(Field::getName))
                .toArray(Field[]::new);
        T entity = fillFields(resultSet, declaredFields, type);
        T snapshot = fillFields(resultSet, declaredFields, type);
        initialStateCache.put(entityKey, snapshot);
        return entity;
    }

    private <T> T fillFields(ResultSet resultSet, Field[] declaredFields, Class<T> type) throws Exception {
        T entity = type.getConstructor().newInstance();
        for (Field field : declaredFields) {
            String columnName = field.getDeclaredAnnotation(Column.class).name();
            field.setAccessible(true);
            Object fieldValue = resultSet.getObject(columnName);
            field.set(entity, fieldValue);
        }
        return entity;
    }

    private String prepareSelectSQL(Class<?> type) {
        Table tableAnnotation = type.getDeclaredAnnotation(Table.class);
        String SQL = "select * from %s where id = ?";
        return String.format(SQL, tableAnnotation.name());
    }
}
