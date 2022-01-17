package com.home;

import com.home.annotation.Column;
import com.home.annotation.Table;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class Session {
    private final DataSource dataSource;
    private Map<EntityKey<?>, Object> lookupCache = new HashMap<>();
    private Map<EntityKey<?>, Object[]> initialStateCache = new HashMap<>();

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
                .collect(Collectors.joining(", "));
    }

    private Function<Field, String> extractQueryPartFromField(Object entity) {
        return field -> {
            try {
                return field.getName() + "= '" + field.get(entity) + "'";
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return null;
            }
        };
    }

    private boolean entryHasChanged(Map.Entry<EntityKey<?>, Object> entry) {
        Object[] initialFields = this.initialStateCache.get(entry.getKey());
        Object[] fieldUnderTest = Arrays.stream(entry.getValue().getClass().getDeclaredFields())
                .sorted(Comparator.comparing(Field::getName))
                .peek(f -> f.setAccessible(true))
                .map(extractFieldValue(entry))
                .toArray();
        for (int i = 0; i < initialFields.length; i++) {
            if (!initialFields[i].equals(fieldUnderTest[i])) {
                return true;
            }
        }
        return false;
    }

    private Function<Field, Object> extractFieldValue(Map.Entry<EntityKey<?>, Object> entry) {
        return f -> {
            try {
                return f.get(entry.getValue());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return null;
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
                T entity = createEntityFrom(entityKey, resultSet);
                return entity;
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
        Object[] snapshotCopy = new Object[declaredFields.length];
        T entity = type.getConstructor().newInstance();
        for (int i = 0; i < declaredFields.length; i++) {
            Field field = declaredFields[i];
            String columnName = field.getDeclaredAnnotation(Column.class).name();
            field.setAccessible(true);
            Object fieldValue = resultSet.getObject(columnName);
            field.set(entity, fieldValue);
            snapshotCopy[i] = fieldValue;
        }
        initialStateCache.put(entityKey, snapshotCopy);
        return entity;
    }

    private String prepareSelectSQL(Class<?> type) {
        Table tableAnnotation = type.getDeclaredAnnotation(Table.class);
        String SQL = "select * from %s where id = ?";
        return String.format(SQL, tableAnnotation.name());
    }
}
