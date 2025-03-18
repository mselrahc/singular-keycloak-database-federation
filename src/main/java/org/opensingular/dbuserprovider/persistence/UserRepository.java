package org.opensingular.dbuserprovider.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.sql.DataSource;

import org.jboss.logging.Logger;
import org.opensingular.dbuserprovider.DBUserStorageException;
import org.opensingular.dbuserprovider.DBUserStorageProvider;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.util.HashUtil;
import org.opensingular.dbuserprovider.util.PagingUtil;
import org.opensingular.dbuserprovider.util.PagingUtil.Pageable;
import org.opensingular.dbuserprovider.util.SearchUtil;
import org.opensingular.dbuserprovider.util.SearchUtil.SearchQuery;


public class UserRepository {
    private static final Logger log = Logger.getLogger(DBUserStorageProvider.class);
    
    private final DataSourceProvider  dataSourceProvider;
    private final QueryConfigurations queryConfigurations;
    
    public UserRepository(DataSourceProvider dataSourceProvider, QueryConfigurations queryConfigurations) {
        this.dataSourceProvider  = dataSourceProvider;
        this.queryConfigurations = queryConfigurations;
    }
    
    
    private <T> T doQuery(String query, Pageable pageable, Function<ResultSet, T> resultTransformer, Object... params) {
        Optional<DataSource> dataSourceOpt = dataSourceProvider.getDataSource();
        if (dataSourceOpt.isPresent()) {
            DataSource dataSource = dataSourceOpt.get();
            try (Connection c = dataSource.getConnection()) {
                if (pageable != null) {
                    query = PagingUtil.formatScriptWithPageable(query, pageable, queryConfigurations.getRDBMS());
                }
                log.infov("Query: {0} params: {1} ", query, Arrays.toString(params));
                try (PreparedStatement statement = c.prepareStatement(query)) {
                    if (params != null) {
                        for (int i = 1; i <= params.length; i++) {
                            statement.setObject(i, params[i - 1]);
                        }
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        return resultTransformer.apply(rs);
                    }
                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
            return null;
        }
        return null;
    }
    
    @SuppressWarnings("UseSpecificCatch")
    private List<Map<String, String>> readMap(ResultSet rs) {
        try {
            List<Map<String, String>> data         = new ArrayList<>();
            Set<String>               columnsFound = new HashSet<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                String columnLabel = rs.getMetaData().getColumnLabel(i);
                columnsFound.add(columnLabel);
            }
            while (rs.next()) {
                Map<String, String> result = new HashMap<>();
                for (String col : columnsFound) {
                    result.put(col, rs.getString(col));
                }
                data.add(result);
            }
            log.infov("Result count: {0}", data.size());
            return data;
        } catch (Exception e) {
            throw new DBUserStorageException(e.getMessage(), e);
        }
    }
    
    
    @SuppressWarnings("UseSpecificCatch")
    private Integer readInt(ResultSet rs) {
        try {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            throw new DBUserStorageException(e.getMessage(), e);
        }
    }
    
    @SuppressWarnings({ "UseSpecificCatch", "unused" })
    private Boolean readBoolean(ResultSet rs) {
        try {
            return rs.next() ? rs.getBoolean(1) : null;
        } catch (Exception e) {
            throw new DBUserStorageException(e.getMessage(), e);
        }
    }
    
    @SuppressWarnings("UseSpecificCatch")
    private String readString(ResultSet rs) {
        try {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            throw new DBUserStorageException(e.getMessage(), e);
        }
    }
    
    public List<Map<String, String>> getAllUsers() {
        return doQuery(queryConfigurations.getBaseQuery(), null, this::readMap);
    }
    
    public int getUsersCount(Map<String, String> search) {
        if (search == null || search.isEmpty()) {
            return Optional.ofNullable(doQuery(queryConfigurations.getCount(), null, this::readInt)).orElse(0);
        } else {
            SearchQuery searchQuery = SearchUtil.create(queryConfigurations.getBaseQuery(), queryConfigurations.getColumnsMapping(), search);
            String query = String.format("select count(*) from (%s) count", searchQuery.getQuery());
            return Optional.ofNullable(doQuery(query, null, this::readInt, searchQuery.getParams())).orElse(0);
        }
    }
    
    public Map<String, String> findUserById(String id) {
        return Optional.ofNullable(doQuery(queryConfigurations.getFindById(), null, this::readMap, id))
                       .orElse(Collections.emptyList())
                       .stream().findFirst().orElse(null);
    }
    
    public Optional<Map<String, String>> findUserByUsername(String username) {
        return Optional.ofNullable(doQuery(queryConfigurations.getFindByUsername(), null, this::readMap, username))
                       .orElse(Collections.emptyList())
                       .stream().findFirst();
    }
    
    public Optional<Map<String, String>> findUserByEmail(String email) {
        return Optional.ofNullable(doQuery(queryConfigurations.getFindByEmail(), null, this::readMap, email))
            .orElse(Collections.emptyList())
            .stream().findFirst();
    }

    public List<Map<String, String>> findUsers(Map<String, String> search, PagingUtil.Pageable pageable) {
        if (search == null || search.isEmpty()) {
            return doQuery(queryConfigurations.getBaseQuery(), pageable, this::readMap);
        }
        SearchQuery searchQuery = SearchUtil.create(queryConfigurations.getBaseQuery(), queryConfigurations.getColumnsMapping(), search);
        return doQuery(searchQuery.getQuery(), pageable, this::readMap, searchQuery.getParams());
    }
    
    public boolean validateCredentials(String username, String password) {
        String hash = Optional.ofNullable(doQuery(queryConfigurations.getFindPasswordHash(), null, this::readString, username)).orElse("");
        return HashUtil.verify(hash, password, queryConfigurations.getHashFunction());
    }
    
    public boolean updateCredentials(String username, String password) {
        String query = queryConfigurations.getUpdatePassword();
        if (query == null || query.isBlank()) {
            throw new UnsupportedOperationException("Password update not supported");
        }

        Optional<DataSource> dataSourceOpt = dataSourceProvider.getDataSource();
        if (!dataSourceOpt.isPresent()) {
            throw new RuntimeException("Data source not found");
        }

        DataSource dataSource = dataSourceOpt.get();
        try (Connection c = dataSource.getConnection()) {
            log.infov("Query: {0}", query);
            try (PreparedStatement statement = c.prepareStatement(query)) {
                statement.setObject(1, HashUtil.hash(password, queryConfigurations.getHashFunction()));
                statement.setObject(2, username);
                boolean updated = statement.executeUpdate() > 0;
                if (!updated) {
                    throw new RuntimeException("Password update failed");
                }
                
                return true;
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        
        return false;
    }
    
    public boolean removeUser() {
        return queryConfigurations.getAllowKeycloakDelete();
    }
}
