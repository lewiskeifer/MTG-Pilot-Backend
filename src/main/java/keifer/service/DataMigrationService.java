package keifer.service;

/*
DataMigrationService migrates data from https://github.com/lewiskeifer/MTG-Inventory-Manager
This service uses values hard-coded to my machine and is of little value elsewhere.
 */

public interface DataMigrationService {

    void migrateTextData();

    void migrateJsonData();

    void migrateSqlData();

}
