# Database Migration Strategy

**Project**: Financial Management System for Psychologists
**Database**: Room ORM with SQLCipher
**Last Updated**: 2026-02-25

## Overview

This document describes the strategy for handling database schema changes and migrations as the application evolves.

## Current Schema Version

- **Database Name**: `financial_management.db`
- **Current Version**: 1
- **Encryption**: SQLCipher with AES-256-GCM
- **Initial Entities**: Patient, Appointment, Payment

## Migration Strategy

### 1. When to Create a Migration

A migration is needed when:
- Adding a new table
- Removing a table
- Adding a new column to existing table
- Removing a column from existing table
- Changing column type
- Changing column constraints (NOT NULL, unique, etc.)
- Adding or removing indexes

**Incrementing the version is NOT needed for**:
- Adding a new DAO method (query, insert, update, delete)
- Changing code logic in converters
- Updating index names (if behavior unchanged)

### 2. Migration Implementation Steps

#### Step 1: Update Entity Class

Modify the entity with new fields or structure:

```kotlin
@Entity(tableName = "patient")
data class PatientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    // ... existing fields ...

    // NEW FIELD in v2
    val notes: String? = null  // Added in migration 1→2
)
```

#### Step 2: Increment Database Version

Update the `@Database` annotation in `AppDatabase.kt`:

```kotlin
@Database(
    entities = [PatientEntity::class, AppointmentEntity::class, PaymentEntity::class],
    version = 2,  // Changed from 1 to 2
    exportSchema = false
)
```

#### Step 3: Create Migration Class

Add migration class in `DatabaseMigrations.kt`:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new column to existing table
        database.execSQL("""
            ALTER TABLE patient
            ADD COLUMN notes TEXT
        """.trimIndent())

        Log.d("Migration", "Migration 1→2: Added notes column to patient table")
    }
}
```

#### Step 4: Register Migration

Add migration to database builder in `AppDatabase.kt`:

```kotlin
Room.databaseBuilder(
    context.applicationContext,
    AppDatabase::class.java,
    Constants.DATABASE_NAME
)
    .openHelperFactory(SupportFactory(encryptionKey))
    .addMigrations(MIGRATION_1_2)  // Register migration
    .addCallback(databaseCallback)
    .build()
```

#### Step 5: Export Schema (Optional but Recommended)

Set `exportSchema = true` in `@Database` to track schema changes:

```kotlin
@Database(
    entities = [...],
    version = 2,
    exportSchema = true  // Enable schema export
)
```

Schema files will be generated in `app/schemas/` directory for version control.

### 3. Common Migration Patterns

#### Adding a Column

```kotlin
database.execSQL("""
    ALTER TABLE patient
    ADD COLUMN date_of_birth TEXT
""")
```

**Note**: SQLite only supports adding columns at the end. The column must have a default value or be nullable if adding to existing rows.

#### Adding a Column with Default Value

```kotlin
database.execSQL("""
    ALTER TABLE payment
    ADD COLUMN currency TEXT NOT NULL DEFAULT 'BRL'
""")
```

#### Creating a New Table

```kotlin
database.execSQL("""
    CREATE TABLE IF NOT EXISTS invoice (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        patient_id INTEGER NOT NULL,
        amount DECIMAL(10,2) NOT NULL,
        date TEXT NOT NULL,
        FOREIGN KEY(patient_id) REFERENCES patient(id)
    )
""")

// Also create indexes if needed
database.execSQL("""
    CREATE INDEX IF NOT EXISTS idx_invoice_patient_date
    ON invoice(patient_id, date DESC)
""")
```

#### Renaming a Table

SQLite doesn't have native RENAME TABLE with constraints, so use:

```kotlin
database.execSQL("ALTER TABLE old_table RENAME TO new_table")
```

#### Modifying Column Constraints

SQLite doesn't support modifying columns directly, so:

```kotlin
// Create new table with desired schema
database.execSQL("""
    CREATE TABLE patient_new (
        id INTEGER PRIMARY KEY,
        name TEXT NOT NULL UNIQUE,  // Made UNIQUE
        phone TEXT UNIQUE,
        email TEXT UNIQUE,
        status TEXT NOT NULL DEFAULT 'ACTIVE',
        initial_consult_date TEXT NOT NULL,
        registration_date TEXT NOT NULL,
        last_appointment_date TEXT
    )
""")

// Copy data from old table
database.execSQL("""
    INSERT INTO patient_new
    SELECT * FROM patient
""")

// Drop old table
database.execSQL("DROP TABLE patient")

// Rename new table
database.execSQL("ALTER TABLE patient_new RENAME TO patient")

// Recreate indexes
database.execSQL("CREATE UNIQUE INDEX idx_patient_phone ON patient(phone)")
database.execSQL("CREATE UNIQUE INDEX idx_patient_email ON patient(email)")
```

### 4. Testing Migrations

#### Test on Fresh Install

```bash
# Fresh app installation should create schema correctly
./gradlew installDebug
# App should work without errors
```

#### Test on Upgrade

```bash
# Install previous version: disable new migration
# Then install new version with migration
# Verify data persists correctly
```

#### Automated Migration Tests

Create tests that:
1. Create old schema version
2. Insert sample data
3. Run migration
4. Verify schema and data integrity

```kotlin
@get:Rule
val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java.canonicalName,
    FrameworkSQLiteOpenHelperFactory()
)

@Test
fun testMigration_1_2() {
    // Create v1 database
    val db1 = helper.createDatabase(Constants.DATABASE_NAME, 1)

    // Insert test data
    db1.execSQL("INSERT INTO patient (id, name) VALUES (1, 'John')")

    // Close and run migration
    db1.close()
    val db2 = helper.runMigrationsAndValidate(
        Constants.DATABASE_NAME, 2, true,
        MIGRATION_1_2
    )

    // Verify schema
    val cursor = db2.query("PRAGMA table_info(patient)")
    // Assert new column exists
}
```

### 5. Data Loss Prevention

**Always plan for data loss scenarios**:

1. **Backup before migration** (user responsibility in current release)
   - Use CSV export feature to backup data before app update

2. **Validate data integrity** post-migration
   - Check row counts match
   - Verify referential integrity
   - Validate calculated fields

3. **Destructive migration fallback**
   - If migration fails and data is already in old format
   - Implement fallback recovery or inform user to restore backup

```kotlin
override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
    super.onDestructiveMigration(db)
    Log.w(TAG, "Destructive migration performed - data loss occurred")
    // TODO: Notify user, suggest data restore
}
```

### 6. Migration Checklist

When implementing a migration:

- [ ] Entity class updated with new fields/structure
- [ ] Database version incremented
- [ ] Migration class created in `DatabaseMigrations.kt`
- [ ] Migration registered in `AppDatabase.buildDatabase()`
- [ ] SQL migration tested manually
- [ ] Data integrity verified post-migration
- [ ] Indexes recreated if needed
- [ ] Tests written for migration
- [ ] Schema exported (optional, for version control)
- [ ] Changelog updated documenting breaking changes
- [ ] Backward compatibility verified or data loss documented

### 7. Version History

| Version | Date | Changes | Breaking Changes |
|---------|------|---------|------------------|
| 1 | 2026-02-25 | Initial schema: Patient, Appointment, Payment | N/A |
| 2 | TBD | TBD | TBD |

### 8. Best Practices

1. **Test migrations thoroughly** before production release
2. **Document all schema changes** in this file and CHANGELOG
3. **Keep migrations small and focused** (one change per migration)
4. **Always provide fallback** for migration failures
5. **Consider user experience** when making breaking changes
6. **Export schema** for version control and documentation
7. **Use transactions** for complex migrations to ensure atomicity

### 9. Troubleshooting

#### Migration Fails on Install

```kotlin
// Check if migration is registered
Room.databaseBuilder(...)
    .addMigrations(MIGRATION_1_2)  // Must be added
    .build()

// Or allow destructive migration (data loss)
.fallbackToDestructiveMigration()
```

#### Column Already Exists Error

```kotlin
// Use IF NOT EXISTS
database.execSQL("ALTER TABLE patient ADD COLUMN IF NOT EXISTS notes TEXT")
```

#### Foreign Key Constraint Error

```kotlin
// Check foreign key enforcement
database.execSQL("PRAGMA foreign_keys;")  // Should return 1

// If constraint violation, fix data before migration
```

### 10. Future Considerations

- **Schema versioning**: Keep track of all versions for documentation
- **Backward compatibility**: Decide how many versions back to support
- **Automated migration testing**: Implement CI/CD tests for migrations
- **Production monitoring**: Track migration success/failure rates
- **User communication**: Notify users of data structure changes
