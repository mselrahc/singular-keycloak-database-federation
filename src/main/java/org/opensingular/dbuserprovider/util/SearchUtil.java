package org.opensingular.dbuserprovider.util;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.keycloak.models.UserModel;

public class SearchUtil {
    private static final Set<String> USER_SEARCH_ATTRIBUTES = Set.of(
        UserModel.USERNAME,
        UserModel.FIRST_NAME,
        UserModel.LAST_NAME,
        UserModel.EMAIL
    );

    public static SearchQuery create(String baseQuery, Map<String, String> columnsMap, Map<String, String> searchCriteria) {
        final String PLACEHOLDER = "__KY_DB_FILTERS__";

        String escapedQuery = baseQuery.replace("{{filters}}", PLACEHOLDER);
        String allResultQuery = escapedQuery.replace("{filters}", "1=1").replace(PLACEHOLDER, "{filters}");
        String noResultQuery = escapedQuery.replace("{filters}", "1=0").replace(PLACEHOLDER, "{filters}");

        if (
            searchCriteria == null || searchCriteria.isEmpty() 
            || columnsMap == null || columnsMap.isEmpty()
            || !escapedQuery.contains("{filters}")
        ) {
            return new SearchQuery(allResultQuery, null);
        }

        Map<String, String> searchConfig = filterSearch(searchCriteria, true);
        Map<String, String> searchMap = filterSearch(searchCriteria, false);
        
        SortedMap<String, String> parameters = new TreeMap<>();
        boolean isFreeTextSearch = searchConfig.containsKey(UserModel.SEARCH);
        boolean isAttributeSearch = !searchMap.isEmpty();

        if (!isFreeTextSearch && !isAttributeSearch) {
            return new SearchQuery(allResultQuery, null);
        }
        
        boolean isExact = Boolean.parseBoolean(searchConfig.getOrDefault(UserModel.EXACT, "false"));
        if (isAttributeSearch) {
            parameters.putAll(getAttributeParameters(columnsMap, searchMap));
        } else {
            String keyword = searchConfig.getOrDefault(UserModel.SEARCH, "");
            if (keyword.trim().isEmpty() || keyword.trim().equals("*")) {
                return new SearchQuery(allResultQuery, null);
            }
            parameters.putAll(getSearchParameters(columnsMap, keyword));
        }

        if (parameters.isEmpty()) {
            return new SearchQuery(noResultQuery, null);
        }

        String conditionString = buildCondition(parameters, isExact, isAttributeSearch);
        Object[] paramValues = getParameterValues(parameters, isExact);
        String replacedQuery = baseQuery.replace("{filters}", conditionString).replace(PLACEHOLDER, "{filters}");

        return new SearchQuery(replacedQuery, paramValues);
    }

    private static Map<String, String> filterSearch(Map<String, String> searchCriteria, boolean isConfig) {
        return searchCriteria.entrySet().stream()
        .filter(entry -> entry.getKey().startsWith("keycloak.") == isConfig)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, String> getSearchParameters(Map<String, String> columnsMap, String keyword) {
        return columnsMap.entrySet().stream()
            .filter(entry -> USER_SEARCH_ATTRIBUTES.contains(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getValue, entry -> keyword));
    }

    private static Map<String, String> getAttributeParameters(Map<String, String> columnsMap, Map<String, String> searchMap) {
        return searchMap.entrySet().stream()
            .filter(entry -> columnsMap.containsKey(entry.getKey()))
            .collect(Collectors.toMap(entry -> columnsMap.get(entry.getKey()), Map.Entry::getValue));

    }

    private static String buildCondition(Map<String, String> parameters, boolean isExact, boolean isAttributeSearch) {
        String comparator = isExact ? "=" : "LIKE";
        String combinator = isAttributeSearch ? "AND" : "OR";
        String escape = isExact ? "" : "ESCAPE '!'";
        return parameters.entrySet().stream()
            .map(entry -> String.format(" UPPER(%s) %s ? %s ", entry.getKey(), comparator, escape))
            .collect(Collectors.joining(combinator));
    }

    private static Object[] getParameterValues(Map<String, String> parameters, boolean isExact) {
        return parameters.values().stream()
            .map(value -> (isExact || value == null) 
                ? value 
                : "%" + value.toUpperCase().replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                    .replace("[", "![") + "%"
            )
            .toArray();
    }
    
    public static class SearchQuery {
        private final String query;
        private final Object[] params;

        private SearchQuery(String query, Object[] params) {
            this.query = query;
            this.params = params;
        }

        public String getQuery() {
            return query;
        }
    
        public Object[] getParams() {
            return params;
        }
    }
}