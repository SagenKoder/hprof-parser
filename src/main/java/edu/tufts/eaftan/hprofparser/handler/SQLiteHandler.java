package edu.tufts.eaftan.hprofparser.handler;

import edu.tufts.eaftan.hprofparser.parser.datastructures.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class SQLiteHandler extends NullRecordHandler {

    private final Map<String, Long> methodNsMap = new HashMap<>();
    private final Map<String, Long> methodCountMap = new HashMap<>();

    private void time(String function, Runnable runnable) {
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            long end = System.nanoTime();
            long duration = end - start;
            long ns = methodNsMap.getOrDefault(function, 0L) + duration;
            long count = methodCountMap.getOrDefault(function, 0L) + 1;
            methodNsMap.put(function, ns);
            methodCountMap.put(function, count);
            if (count % 1000 == 0) {
                System.out.println("Function: " + function + " - Total duration: " + ns + " ns Total calls: " + count + " Average duration: " + ns / count + " ns");
            }
        }
    }

    private static final String DB_URL = "jdbc:sqlite:heapdump.db"; // Database file path
    private Connection connection;
    private final HashMap<Long, String> stringMap = new HashMap<>();
    private final HashMap<Long, ClassInfo> classMap = new HashMap<>();

    private final long startTimeNanos;

    public SQLiteHandler() {
        try {
            initializeDatabase();
            createTables();
            setupDatabaseProperties();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
        startTimeNanos = System.nanoTime();
    }

    private void initializeDatabase() throws SQLException {
        // Ensure the directory for the database exists
        try {
            Path dbPath = Paths.get(DB_URL.replace("jdbc:sqlite:", ""));
            Path dbDirectory = dbPath.getParent();
            if (dbDirectory != null && !Files.exists(dbDirectory)) {
                Files.createDirectories(dbDirectory);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directories for database", e);
        }

        // Establish connection to the SQLite database file
        connection = DriverManager.getConnection(DB_URL);
    }

    private void createTables() throws SQLException {
        String createStringsTable = """
            CREATE TABLE IF NOT EXISTS Strings (
                id INTEGER PRIMARY KEY,
                data TEXT
            );
            """;

        String createClassesTable = """
            CREATE TABLE IF NOT EXISTS Classes (
                classObjId INTEGER PRIMARY KEY,
                className TEXT,
                superClassObjId INTEGER,
                instanceSize INTEGER,
                FOREIGN KEY(superClassObjId) REFERENCES Classes(classObjId)
            );
            """;

        String createObjectsTable = """
            CREATE TABLE IF NOT EXISTS Objects (
                objId INTEGER PRIMARY KEY,
                classObjId INTEGER,
                stackTraceSerialNum INTEGER,
                FOREIGN KEY(classObjId) REFERENCES Classes(classObjId)
            );
            """;

        String createFieldsTable = """
            CREATE TABLE IF NOT EXISTS Fields (
                objId INTEGER,
                fieldName TEXT,
                fieldType TEXT,
                fieldValue TEXT,
                FOREIGN KEY(objId) REFERENCES Objects(objId)
            );
            """;

        String createStaticFieldsTable = """
            CREATE TABLE IF NOT EXISTS StaticFields (
                classObjId INTEGER,
                fieldName TEXT,
                fieldType TEXT,
                fieldValue TEXT,
                FOREIGN KEY(classObjId) REFERENCES Classes(classObjId)
            );
            """;

        String createArraysTable = """
            CREATE TABLE IF NOT EXISTS Arrays (
                arrayId INTEGER PRIMARY KEY,
                elemClassObjId INTEGER,
                length INTEGER,
                elemType TEXT,
                FOREIGN KEY(elemClassObjId) REFERENCES Classes(classObjId)
            );
            """;

        String createObjectArrayElementsTable = """
            CREATE TABLE IF NOT EXISTS ObjectArrayElements (
                arrayId INTEGER,
                elementIndex INTEGER,
                elementObjId INTEGER,
                FOREIGN KEY(arrayId) REFERENCES Arrays(arrayId),
                FOREIGN KEY(elementObjId) REFERENCES Objects(objId)
            );
            """;

        String createPrimitiveArrayElementsTable = """
            CREATE TABLE IF NOT EXISTS PrimitiveArrayElements (
                arrayId INTEGER,
                elementIndex INTEGER,
                elementValue TEXT,
                FOREIGN KEY(arrayId) REFERENCES Arrays(arrayId)
            );
            """;

        String createConstantsTable = """
            CREATE TABLE IF NOT EXISTS Constants (
                classObjId INTEGER,
                constantPoolIndex INTEGER,
                constantValue TEXT,
                FOREIGN KEY(classObjId) REFERENCES Classes(classObjId)
            );
            """;

        String createHeapRootsTable = """
            CREATE TABLE IF NOT EXISTS HeapRoots (
                objId INTEGER PRIMARY KEY,
                rootType TEXT
            );
            """;


        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createStringsTable);
            stmt.execute(createClassesTable);
            stmt.execute(createObjectsTable);
            stmt.execute(createFieldsTable);
            stmt.execute(createStaticFieldsTable);
            stmt.execute(createArraysTable);
            stmt.execute(createObjectArrayElementsTable);
            stmt.execute(createPrimitiveArrayElementsTable);
            stmt.execute(createConstantsTable);
            stmt.execute(createHeapRootsTable);
        }
    }

    private void setupDatabaseProperties() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA synchronous = OFF");
            stmt.execute("PRAGMA journal_mode = MEMORY");
            stmt.execute("PRAGMA cache_size = 10000");
        }
    }

    /* Handlers for top-level records */
    @Override
    public void stringInUTF8(long id, String data) {
        time("stringInUTF8", () -> {
            stringMap.put(id, data);
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO Strings (id, data) VALUES (?, ?)")) {
                pstmt.setLong(1, id);
                pstmt.setString(2, data);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert string", e);
            }
        });
    }

    @Override
    public void loadClass(int classSerialNum, long classObjId,
                          int stackTraceSerialNum, long classNameStringId) {
        time("loadClass", () -> {
            String className = stringMap.get(classNameStringId);
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO Classes (classObjId, className) VALUES (?, ?)")) {
                pstmt.setLong(1, classObjId);
                pstmt.setString(2, className);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert class", e);
            }
        });
    }

    @Override
    public void classDump(long classObjId, int stackTraceSerialNum,
                          long superClassObjId, long classLoaderObjId, long signersObjId,
                          long protectionDomainObjId, long reserved1, long reserved2,
                          int instanceSize, Constant[] constants, Static[] statics,
                          InstanceField[] instanceFields) {
        time("classDump", () -> {
            classMap.put(classObjId, new ClassInfo(classObjId, superClassObjId, instanceSize, instanceFields));
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "UPDATE Classes SET superClassObjId = ?, instanceSize = ? WHERE classObjId = ?")) {
                pstmt.setLong(1, superClassObjId);
                pstmt.setInt(2, instanceSize);
                pstmt.setLong(3, classObjId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update class", e);
            }

            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO Constants (classObjId, constantPoolIndex, constantValue) VALUES (?, ?, ?)")) {
                for (Constant constant : constants) {
                    pstmt.setLong(1, classObjId);
                    pstmt.setShort(2, constant.constantPoolIndex);
                    pstmt.setString(3, constant.value.toString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert constants", e);
            }

            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO StaticFields (classObjId, fieldName, fieldType, fieldValue) VALUES (?, ?, ?, ?)")) {
                for (Static field : statics) {
                    pstmt.setLong(1, classObjId);
                    pstmt.setString(2, stringMap.get(field.staticFieldNameStringId));
                    pstmt.setString(3, field.value.type.name());
                    pstmt.setString(4, field.value.toString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert static fields", e);
            }
        });
    }

    @Override
    public void instanceDump(long objId, int stackTraceSerialNum, long classObjId, Value<?>[] instanceFieldValues) {
        time("instanceDump", () -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO Objects (objId, classObjId, stackTraceSerialNum) VALUES (?, ?, ?)")) {
                pstmt.setLong(1, objId);
                pstmt.setLong(2, classObjId);
                pstmt.setInt(3, stackTraceSerialNum);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert object", e);
            }

            // Get the instance fields for the class
            InstanceField[] instanceFields = classMap.get(classObjId).instanceFields;

            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO Fields (objId, fieldName, fieldType, fieldValue) VALUES (?, ?, ?, ?)")) {
                for (int i = 0; i < instanceFields.length; i++) {
                    InstanceField field = instanceFields[i];
                    String fieldName = stringMap.get(field.fieldNameStringId);
                    Value<?> value = instanceFieldValues[i];
                    pstmt.setLong(1, objId);
                    pstmt.setString(2, fieldName);
                    pstmt.setString(3, field.type.name());
                    pstmt.setString(4, value.toString());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert fields", e);
            }
        });
    }


    @Override
    public void objArrayDump(long objId, int stackTraceSerialNum,
                             long elemClassObjId, long[] elems) {
        time("objArrayDump", () -> {
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO Arrays (arrayId, elemClassObjId, length, elemType) VALUES (?, ?, ?, ?)")) {
                pstmt.setLong(1, objId);
                pstmt.setLong(2, elemClassObjId);
                pstmt.setInt(3, elems.length);
                pstmt.setString(4, Type.OBJ.name());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert array", e);
            }

            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO ObjectArrayElements (arrayId, elementIndex, elementObjId) VALUES (?, ?, ?)")) {
                for (int i = 0; i < elems.length; i++) {
                    pstmt.setLong(1, objId);
                    pstmt.setInt(2, i);
                    pstmt.setLong(3, elems[i]);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert object array elements", e);
            }
        });
    }

    @Override
    public void primArrayDump(long objId, int stackTraceSerialNum,
                              byte elemType, Value<?>[] elems) {
        time("primArrayDump", () -> {
            Type type = Type.hprofTypeToEnum(elemType);
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO Arrays (arrayId, elemClassObjId, length, elemType) VALUES (?, ?, ?, ?)")) {
                pstmt.setLong(1, objId);
                pstmt.setNull(2, Types.BIGINT); // Primitive arrays don't have an element class
                pstmt.setInt(3, elems.length);
                pstmt.setString(4, type.name());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert array", e);
            }

// Lets ignore primitive arrays for now...
//            try (PreparedStatement pstmt = connection.prepareStatement(
//                    "INSERT INTO PrimitiveArrayElements (arrayId, elementIndex, elementValue) VALUES (?, ?, ?)")) {
//                for (int i = 0; i < elems.length; i++) {
//                    pstmt.setLong(1, objId);
//                    pstmt.setInt(2, i);
//                    pstmt.setString(3, elems[i].toString());
//                    pstmt.addBatch();
//                }
//                pstmt.executeBatch();
//            } catch (SQLException e) {
//                throw new RuntimeException("Failed to insert primitive array elements", e);
//            }
        });
    }

    /* Utility methods */

    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close database connection", e);
        }
        long ns = System.nanoTime() - startTimeNanos;
        System.out.println("Total duration: " + ns + " ns");
    }
}
