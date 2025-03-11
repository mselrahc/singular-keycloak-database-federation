package org.opensingular.dbuserprovider.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.keycloak.models.UserModel;
import org.opensingular.dbuserprovider.persistence.RDBMS;
import org.opensingular.dbuserprovider.util.SearchUtil;

public class QueryConfigurations {

    private final String baseQuery;
    private final String count;
    private final String findById;
    private final String findByUsername;
    private final String findByEmail;
    private final Map<String, String> columnsMapping;
    private final String findPasswordHash;
    private final String hashFunction;
    private final RDBMS RDBMS;
    private final boolean allowKeycloakDelete;
    private final boolean allowDatabaseToOverwriteKeycloak;

    public QueryConfigurations(
        String baseQuery,
        String count,
        String findById,
        String findByUsername,
        String findByEmail,
        List<String> columnsMapping,
        String findPasswordHash,
        String hashFunction,
        RDBMS RDBMS,
        boolean allowKeycloakDelete,
        boolean allowDatabaseToOverwriteKeycloak
    ) {
        this.baseQuery = baseQuery;
        this.count = count;
        this.findById = findById;
        this.findByUsername = findByUsername;
        this.findByEmail = findByEmail;
        this.columnsMapping = columnsMapping.stream()
            .map(s -> s.split("="))
            .filter(arr -> arr.length == 2)
            .collect(Collectors.toMap(arr -> arr[0].trim(), arr -> arr[1].trim()));
        this.findPasswordHash = findPasswordHash;
        this.hashFunction = hashFunction;
        this.RDBMS = RDBMS;
        this.allowKeycloakDelete = allowKeycloakDelete;
        this.allowDatabaseToOverwriteKeycloak = allowDatabaseToOverwriteKeycloak;
    }

    public RDBMS getRDBMS() {
        return RDBMS;
    }

    public String getBaseQuery() {
        return getBaseQuery(null);
    }

    public String getBaseQuery(List<String> columns) {
        return replaceColumns(baseQuery, columns);
    }

    public String getCount() {
        if (count == null || count.trim().isEmpty()) {
            return getBaseQuery(List.of("count(*)"));
        }

        return count;
    }

    public String getFindById() {
        if (findById == null || findById.trim().isEmpty()) {
            return SearchUtil.create(getBaseQuery(), columnsMapping, Map.of("id", "", UserModel.EXACT, "true")).getQuery();
        }

        return replaceColumns(findById, null);
    }

    public String getFindByUsername() {
        if (findByUsername == null || findByUsername.trim().isEmpty()) {
            return SearchUtil.create(getBaseQuery(), columnsMapping, Map.of(UserModel.USERNAME, "", UserModel.EXACT, "true")).getQuery();
        }

        return replaceColumns(findByUsername, null);
    }

    public String getFindByEmail() {
        if (findByEmail == null || findByEmail.trim().isEmpty()) {
            return SearchUtil.create(getBaseQuery(), columnsMapping, Map.of(UserModel.EMAIL, "", UserModel.EXACT, "true")).getQuery();
        }

        return replaceColumns(findByEmail, null);
    }

    public String replaceColumns(String query, List<String> columns) {
        String escapedQuery = query.replace("{{columns}}", "__KY_DB_COLUMNS__");
        String columnStr = columns == null || columns.isEmpty()
            ? columnsMapping.keySet().stream()
                .map(c -> String.format("%s as %s", columnsMapping.get(c), c))
                .collect(Collectors.joining(", "))
            : columns.stream().collect(Collectors.joining(", "));
        String modifiedQuery = escapedQuery.replace("{columns}", columnStr);
        return modifiedQuery.replace("__KY_DB_COLUMNS__", "{columns}");
    }

    public Map<String, String> getColumnsMapping() {
        return columnsMapping;
    }

    public String getFindPasswordHash() {
        return findPasswordHash;
    }

    public String getHashFunction() {
        return hashFunction;
    }

    public boolean isArgon2() {
        return hashFunction.contains("Argon2");
    }

    public boolean isBlowfish() {
        return hashFunction.toLowerCase().contains("blowfish");
    }

    public boolean getAllowKeycloakDelete() {
        return allowKeycloakDelete;
    }

    public boolean getAllowDatabaseToOverwriteKeycloak() {
        return allowDatabaseToOverwriteKeycloak;
    }
}
