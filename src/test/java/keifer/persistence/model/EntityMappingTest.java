package keifer.persistence.model;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.mapping.Value;
import org.junit.Test;

import javax.persistence.Entity;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Builds Hibernate's mapping for every entity, the way the application does at startup.
 *
 * Table level annotations - unique keys and indexes - name their columns logically, which is the
 * property name until a column is named outright, and not the snake case the naming strategy
 * eventually writes. Get that wrong and nothing complains until the server boots against a real
 * database and refuses to start, which is a slow way to find a typo.
 */
public class EntityMappingTest {

    private static final List<Class<?>> ENTITIES = Arrays.asList(
            CardEntity.class,
            DeckEntity.class,
            DeckSnapshotEntity.class,
            PriceSnapshotEntity.class,
            SealedCollectionEntity.class,
            SealedCollectionSnapshotEntity.class,
            SealedEntity.class,
            UserEntity.class,
            VersionEntity.class);

    /** The dialect and naming strategies the deployed application runs with. */
    private StandardServiceRegistry registry() {

        return new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQL5Dialect")
                // The mapping is checked on its own, with no database to connect to
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                // The strategies Spring Boot installs, since they decide the column names
                .applySetting("hibernate.physical_naming_strategy",
                        "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy")
                .build();
    }

    @Test
    public void everyEntityMaps() {

        StandardServiceRegistry registry = registry();

        try {

            MetadataSources sources = new MetadataSources(registry);
            ENTITIES.forEach(sources::addAnnotatedClass);

            // Throws where a constraint names a column the table does not have
            Metadata metadata = sources.buildMetadata();

            for (Class<?> entity : ENTITIES) {
                PersistentClass mapped = metadata.getEntityBinding(entity.getName());
                assertNotNull(entity.getSimpleName() + " did not map", mapped);
                assertNotNull(entity.getSimpleName() + " has no table", mapped.getTable());
            }

        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    /*
     * MyISAM - which MySQL5Dialect, as configured here, builds - allows a key of 1000 bytes, and
     * a text column left to default to varchar(255) is 1020 of them once the charset is counted.
     * The index is then refused at startup and quietly logged as a warning, leaving the table
     * unindexed in production. Anything indexed has to say how long it really is.
     */
    private static final int MAX_INDEXED_TEXT_LENGTH = 64;

    @Test
    public void indexedTextColumnsDeclareALength() {

        StandardServiceRegistry registry = registry();

        try {

            MetadataSources sources = new MetadataSources(registry);
            ENTITIES.forEach(sources::addAnnotatedClass);
            Metadata metadata = sources.buildMetadata();

            for (Class<?> entity : ENTITIES) {

                Table table = metadata.getEntityBinding(entity.getName()).getTable();

                for (Iterator<UniqueKey> keys = table.getUniqueKeyIterator(); keys.hasNext(); ) {
                    checkKeyColumns(table, keys.next().getColumns());
                }

                for (Iterator<Index> indexes = table.getIndexIterator(); indexes.hasNext(); ) {
                    checkKeyColumns(table, indexes.next().getColumnIterator());
                }
            }

        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void checkKeyColumns(Table table, Iterable<Column> columns) {
        columns.forEach(column -> checkKeyColumn(table, column));
    }

    private void checkKeyColumns(Table table, Iterator<Column> columns) {
        while (columns.hasNext()) {
            checkKeyColumn(table, columns.next());
        }
    }

    private void checkKeyColumn(Table table, Column column) {

        // A constraint holds its own copies, which carry a name and nothing else. The table's
        // own column is the one that knows what it is made of.
        Column bound = table.getColumn(column);
        Value value = bound == null ? null : bound.getValue();

        if (value == null) {
            return;
        }

        Class<?> held = value.getType().getReturnedClass();

        // Dates and numbers are fixed width; only text is at the mercy of the default
        if (held != String.class && !held.isEnum()) {
            return;
        }

        assertTrue(table.getName() + "." + bound.getName() + " is indexed at length "
                        + bound.getLength() + ", past what a MyISAM key allows",
                bound.getLength() <= MAX_INDEXED_TEXT_LENGTH);
    }

    @Test
    public void everyEntityIsListed() {

        // A new entity that nobody added here would go unchecked, which is the whole point
        for (Class<?> entity : ENTITIES) {
            assertNotNull(entity.getSimpleName() + " is not an @Entity",
                    entity.getAnnotation(Entity.class));
        }
    }

}
