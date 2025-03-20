package org.opensingular.dbuserprovider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.persistence.DataSourceProvider;
import org.opensingular.dbuserprovider.persistence.RDBMS;

import com.google.auto.service.AutoService;

@AutoService(UserStorageProviderFactory.class)
public class DBUserStorageProviderFactory implements UserStorageProviderFactory<DBUserStorageProvider> {
    private static final Logger log = Logger.getLogger(DBUserStorageProvider.class);    
    
    private final Map<String, ProviderConfig> providerConfigPerInstance = new HashMap<>();
    
    @Override
    public void init(Config.Scope config) {
    }
    
    @Override
    public void close() {
        for (Map.Entry<String, ProviderConfig> pc : providerConfigPerInstance.entrySet()) {
            pc.getValue().dataSourceProvider.close();
        }
    }
    
    @Override
    public DBUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        ProviderConfig providerConfig = providerConfigPerInstance.computeIfAbsent(model.getId(), s -> configure(model));
        return new DBUserStorageProvider(session, model, providerConfig.dataSourceProvider, providerConfig.queryConfigurations);
    }
    
    private synchronized ProviderConfig configure(ComponentModel model) {
        log.infov("Creating configuration for model: id={0} name={1}", model.getId(), model.getName());
        ProviderConfig providerConfig = new ProviderConfig();
        String         user           = model.get(StorageProviderConfig.USER.name());
        String         password       = model.get(StorageProviderConfig.PASSWORD.name());
        String         url            = model.get(StorageProviderConfig.URL.name());
        RDBMS          rdbms          = RDBMS.getByDescription(model.get(StorageProviderConfig.RDBMS.name()));
        providerConfig.dataSourceProvider.configure(url, rdbms, user, password, model.getName());
        providerConfig.queryConfigurations = new QueryConfigurations(
            model.get(StorageProviderConfig.BASE_QUERY.name()),
            model.get(StorageProviderConfig.COUNT.name()),
            model.get(StorageProviderConfig.FIND_BY_ID.name()),
            model.get(StorageProviderConfig.FIND_BY_USERNAME.name()),
            model.get(StorageProviderConfig.FIND_BY_EMAIL.name()),
            model.getConfig().get(StorageProviderConfig.COLUMNS_MAPPING.name()),
            model.get(StorageProviderConfig.FIND_PASSWORD_HASH.name()),
            model.get(StorageProviderConfig.HASH_FUNCTION.name()),
            model.get(StorageProviderConfig.UPDATE_PASSWORD.name()),
            rdbms,
            model.get(StorageProviderConfig.ALLOW_KEYCLOAK_DELETE.name(), false),
            model.get(StorageProviderConfig.ALLOW_DATABASE_TO_OVERWRITE_KEYCLOAK.name(), false)
        );
        return providerConfig;
    }
    
    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) throws ComponentValidationException {
        try {
            ProviderConfig old = providerConfigPerInstance.put(model.getId(), configure(model));
            if (old != null) {
                old.dataSourceProvider.close();
            }
        } catch (Exception e) {
            throw new ComponentValidationException(e.getMessage(), e);
        }
    }
    
    @Override
    public String getId() {
        return "RDBMS";
    }
    
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                                           //DATABASE
                                           .property()
                                           .name(StorageProviderConfig.URL.name())
                                           .label("JDBC URL")
                                           .helpText("JDBC connection string. Example: `jdbc:jtds:sqlserver://server-name/database_name;instance=instance_name`")
                                           .type(ProviderConfigProperty.STRING_TYPE)
                                           .defaultValue("")
                                           .required(true)
                                           .add()
                                           .property()
                                           .name(StorageProviderConfig.USER.name())
                                           .label("JDBC connection user")
                                           .helpText("JDBC connection user")
                                           .type(ProviderConfigProperty.STRING_TYPE)
                                           .defaultValue("")
                                           .required(true)
                                           .add()
                                           .property()
                                           .name(StorageProviderConfig.PASSWORD.name())
                                           .label("JDBC connection password")
                                           .helpText("JDBC connection password")
                                           .type(ProviderConfigProperty.PASSWORD)
                                           .defaultValue("")
                                           .required(true)
                                           .add()
                                           .property()
                                           .name(StorageProviderConfig.RDBMS.name())
                                           .label("RDBMS")
                                           .helpText("Relational Database Management System")
                                           .type(ProviderConfigProperty.LIST_TYPE)
                                           .options(RDBMS.getAllDescriptions())
                                           .defaultValue(RDBMS.SQL_SERVER.getDesc())
                                           .required(true)
                                           .add()
                                           .property()
                                           .name(StorageProviderConfig.ALLOW_KEYCLOAK_DELETE.name())
                                           .label("Allow Keycloak's User Delete")
                                           .helpText("By default, clicking Delete on a user in Keycloak is not allowed.  Activate this option to allow to Delete Keycloak's version of the user (does not touch the user record in the linked RDBMS), e.g. to clear synching issues and allow the user to be synced from scratch from the RDBMS on next use, in Production or for testing.")
                                           .type(ProviderConfigProperty.BOOLEAN_TYPE)
                                           .defaultValue("false")
                                           .add()
                                           .property()
                                           .name(StorageProviderConfig.ALLOW_DATABASE_TO_OVERWRITE_KEYCLOAK.name())
                                           .label("Allow DB Attributes to Overwrite Keycloak")
                                           // Technical details for the following comment: we aggregate both the existing Keycloak version and the DB version of an attribute in a Set, but since e.g. email is not a list of values on the Keycloak User, the new email is never set on it.
                                           .helpText("By default, once a user is loaded in Keycloak, its attributes (e.g. 'email') stay as they are in Keycloak even if an attribute of the same name now returns a different value through the query.  Activate this option to have all attributes set in the SQL query to always overwrite the existing user attributes in Keycloak (e.g. if Keycloak user has email 'test@test.com' but the query fetches a field named 'email' that has a value 'example@exemple.com', the Keycloak user will now have email attribute = 'example@exemple.com'). This behavior works with NO_CAHCE configuration. In case you set this flag under a cached configuration, the user attributes will be reload if: 1) the cached value is older than 500ms and 2) username or e-mail does not match cached values.")
                                           .type(ProviderConfigProperty.BOOLEAN_TYPE)
                                           .defaultValue("false")
                                           .add()
        
                                           //QUERIES
        
                                           .property()
                                           .name(StorageProviderConfig.BASE_QUERY.name())
                                           .label("Base user SQL")
                                           .helpText("Query returning all users. Use {columns} as placeholder for the selected columns. Use {filters} as placeholder for the where conditions.")
                                           .type(ProviderConfigProperty.TEXT_TYPE)
                                           .defaultValue("select {columns} from users where {filters}")
                                           .required(true)
                                           .add()

                                           .property()
                                           .name(StorageProviderConfig.COUNT.name())
                                           .label("Count user SQL")
                                           .helpText("Query returning the total count of users. This can be left empty to use the same query as the base query ({columns} will be replaced with `count(*)`).")
                                           .type(ProviderConfigProperty.TEXT_TYPE)
                                           .defaultValue("")
                                           .add()
        
                                           .property()
                                           .name(StorageProviderConfig.FIND_BY_ID.name())
                                           .label("Find user by ID SQL")
                                           .helpText("Query returning user by id. This can be left empty to use the same query as the base query ({filters} will be replaced with `your_id_column = ?`).")
                                           .type(ProviderConfigProperty.TEXT_TYPE)
                                           .defaultValue("")
                                           .add()
        
                                           .property()
                                           .name(StorageProviderConfig.FIND_BY_USERNAME.name())
                                           .label("Find user by username SQL")
                                           .helpText("Query returning user by username. This can be left empty to use the same query as the base query ({filters} will be replaced with `your_username_column = ?`).")
                                           .type(ProviderConfigProperty.TEXT_TYPE)
                                           .defaultValue("")
                                           .add()
                                           
                                           .property()
                                           .name(StorageProviderConfig.FIND_BY_EMAIL.name())
                                           .label("Find user by email SQL")
                                           .helpText("Query returning user by email. This can be left empty to use the same query as the base query ({filters} will be replaced with `your_email_column = ?`).")
                                           .type(ProviderConfigProperty.TEXT_TYPE)
                                           .defaultValue("")
                                           .add()
                                                   
                                           .property()
                                           .name(StorageProviderConfig.COLUMNS_MAPPING.name())
                                           .label("Attribute mapping")
                                           .helpText("""
                                                Map Keycloak attributes to corresponding database columns. Use '=' as the separator.
                                                The 'id' and 'username' attributes are required, but you can safely remove other attributes. 
                                                Custom attributes can also be added as needed.
                                                All attributes defined here will replace the {columns} placeholder.
                                                During attribute-based search, if the searched attribute is not defined in this list, no results will be returned.
                                                Use 'true' and 'false' as strings for boolean values.
                                                Use a float value representing a Unix timestamp (milliseconds since epoch) for datetime values.
                                           """)
                                           .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE)
                                           .defaultValue(List.of(
                                               "id=my_id_column",
                                               "username=my_username_column",
                                               "firstName=my_first_name_column",
                                               "lastName=my_last_name_column",
                                               "email=my_email_column",
                                               "locale=my_locale_column",
                                               "EMAIL_VERIFIED=my_email_verified_column",
                                               "ENABLED=my_enabled_column",
                                               "CREATED_TIMESTAMP=my_created_timestamp_column"
                                           ))
                                           .add()

        
                                           .property()
                                           .name(StorageProviderConfig.FIND_PASSWORD_HASH.name())
                                           .label("Find password hash (blowfish or hash digest hex) SQL query")
                                           .helpText("Query returning password hash by username. This can be left empty to use the same query as the base query ({filters} will be replaced with `your_username_column = ?`).")
                                           .type(ProviderConfigProperty.TEXT_TYPE)
                                           .defaultValue("select hash_pwd from users where \"username\" = ?")
                                           .add()
                                           .property()
                                           .name(StorageProviderConfig.HASH_FUNCTION.name())
                                           .label("Password hash function")
                                           .helpText("Hash type used to match password (md* and sha* uses hex hash digest)")
                                           .type(ProviderConfigProperty.LIST_TYPE)
                                           .options("Blowfish (bcrypt)", "MD2", "MD5", "SHA-1", "SHA-256", "SHA3-224", "SHA3-256", "SHA3-384", "SHA3-512", "SHA-384", "SHA-512/224", "SHA-512/256", "SHA-512", "PBKDF2-SHA256", "Argon2d", "Argon2i", "Argon2id")
                                           .defaultValue("SHA-1")
                                           .add()

                                           .property()
                                           .name(StorageProviderConfig.UPDATE_PASSWORD.name())
                                           .label("Update password SQL")
                                           .helpText("Query to update password hash. This can be left empty to disable password update. Use ? as placeholders, placeholders will be substituted with the new password and username. The algorithm used to generate the password hash will be taken from the hash function setting.")
                                           .type(ProviderConfigProperty.TEXT_TYPE)
                                           .defaultValue("")
                                           .add()

                                           .build();
    }
    
    private static class ProviderConfig {
        private DataSourceProvider  dataSourceProvider = new DataSourceProvider();
        private QueryConfigurations queryConfigurations;
    }
    
    
}
