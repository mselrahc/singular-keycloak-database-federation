# singular-keycloak-database-federation



### Compatible with Keycloak 17+ quarkus based.

### ** Keycloak 19+ ** KNOWN ISSUE:

#### New Theme breaks custom providers, to overcome this problem, follow these steps:


 - Click "Realm Settings" on the left menu
 - Then click the tab "Themes"
 - And, for the selection input labeled "Admin console theme", select "keycloak"
 - Logoff and login again
 - Now, if you try to configure this provider again, keycloak should render all configuration fields and everything else should work fine.
 
 See issue #19 for further information.


Keycloak User Storage SPI for Relational Databases (Keycloak User Federation). 
Supports MySQL 8+ and SQL Server 2012+. 
PostgreSQL 12+, Oracle 19+, and DB2 drivers are included but not tested, use at your own risk.

## Changes
Changes from the forked repo:
- Change provider configuration
- Add supports for attribute-based search
- Fix user search in authorization evaluation test
- Added support for password update


## Configuration

Keycloak User Federation Screenshot

![Sample Screenshot](assets/config-page.png)


## Limitations

- Do not allow user information update
- Do not supports user roles or groups

## Custom attributes

Just add a mapper to client mappers with the same name as the returned column alias in your queries.Use mapper type "User Attribute". See the example below:
    
![Sample Screenshot 2](assets/mapper-page.png)


## Build

- Run `mvn clean package`  

## Development

- Copy `.env.example` to `.env` and adjust the values as needed
- Run `docker compose up`

## Deployment

- Copy every  `.jar` from dist/ folder  to  /providers folder under your keycloak installation root. 
    - i.e, on a default keycloak setup, copy all  `.jar` files to <keycloak_root_dir>/providers
- Run:
    `$ ./bin/kc.sh start-dev`
    OR if you are using a production configuration:
    ```bash
    $ ./bin/kc.sh build
    $ ./bin/kc.sh start
    ```

## For futher information see:
- https://github.com/keycloak/keycloak/issues/9833
- https://www.keycloak.org/docs/latest/server_development/#packaging-and-deployment
