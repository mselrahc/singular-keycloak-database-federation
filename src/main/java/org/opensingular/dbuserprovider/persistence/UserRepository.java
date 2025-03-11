package org.opensingular.dbuserprovider.persistence;

import java.security.MessageDigest;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.sql.DataSource;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.jboss.logging.Logger;
import org.opensingular.dbuserprovider.DBUserStorageException;
import org.opensingular.dbuserprovider.DBUserStorageProvider;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.util.PBKDF2SHA256HashingUtil;
import org.opensingular.dbuserprovider.util.PagingUtil;
import org.opensingular.dbuserprovider.util.PagingUtil.Pageable;
import org.opensingular.dbuserprovider.util.SearchUtil;
import org.opensingular.dbuserprovider.util.SearchUtil.SearchQuery;

import com.google.common.collect.ImmutableMap;

import at.favre.lib.crypto.bcrypt.BCrypt;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;


public class UserRepository {
    private static final Logger log = Logger.getLogger(DBUserStorageProvider.class);
    private static final Map<String, Argon2Types> ARGON2TYPES = ImmutableMap.of(
        "Argon2d", Argon2Types.ARGON2d,
        "Argon2i", Argon2Types.ARGON2i,
        "Argon2id", Argon2Types.ARGON2id
    );
    private static final Map<Argon2Types, Argon2> ARGON2 = ImmutableMap.of(
        Argon2Types.ARGON2d, Argon2Factory.create(Argon2Types.ARGON2d),
        Argon2Types.ARGON2i, Argon2Factory.create(Argon2Types.ARGON2i),
        Argon2Types.ARGON2id, Argon2Factory.create(Argon2Types.ARGON2id)
    );
    
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
        if (queryConfigurations.isBlowfish()) {
            return !hash.isEmpty() && BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
        } else if (queryConfigurations.isArgon2()) {
            return !hash.isEmpty() && ARGON2.get(ARGON2TYPES.get(queryConfigurations.getHashFunction())).verify(hash, password.toCharArray());
        } else {
            String hashFunction = queryConfigurations.getHashFunction();

            if(hashFunction.equals("PBKDF2-SHA256")){
                String[] components = hash.split("\\$");
                return new PBKDF2SHA256HashingUtil(password, components[2], Integer.parseInt(components[1])).validatePassword(components[3]);
            }

            MessageDigest digest   = DigestUtils.getDigest(hashFunction);
            byte[]        pwdBytes = StringUtils.getBytesUtf8(password);
            return Objects.equals(Hex.encodeHexString(digest.digest(pwdBytes)), hash);
        }
    }
    
    public boolean updateCredentials(String username, String password) {
        throw new NotImplementedException("Password update not supported");
    }
    
    public boolean removeUser() {
        return queryConfigurations.getAllowKeycloakDelete();
    }
}
