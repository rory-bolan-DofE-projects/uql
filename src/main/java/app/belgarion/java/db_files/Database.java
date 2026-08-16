package app.belgarion.java.db_files;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;

public class Database {
    public static final class CSV {
        public static final char SEP = 31;
    }
    private static final Gson gson = new Gson();
    public static class MalformedRequestException extends Exception {
        public MalformedRequestException(String message) {
            super(message);
        }
    }
    public static void New(String name) throws IOException {
        name = name.trim();
        if (!name.endsWith(".udb")) {
            System.err.printf("Filename %s isn't the UDB file type\n", name);
            System.exit(1);
        }

        try (FileOutputStream fos = new FileOutputStream(name);
             ZipOutputStream zos = new ZipOutputStream(fos)) {


            zos.putNextEntry(new ZipEntry("schema.json"));
            JsonObject schema_json = new JsonObject();
            schema_json.addProperty("name", name);
            schema_json.add("tables", new JsonArray());
            schema_json.addProperty("version", 1);
            String schema = schema_json.toString();
            System.out.println(schema);
            zos.write(schema.getBytes());
            zos.closeEntry();


        }
    }
    public static boolean containsTable(String db_name, String table_name) throws IOException {
        try (ZipFile zipFile = new ZipFile(db_name)) {
            for (ZipEntry entry :  Collections.list(zipFile.entries())) {
                String name = entry.getName();
                if (name.substring(0, name.lastIndexOf(".")).equals(table_name)) {
                    return true;
                }
            }
            return false;
        }
    }
    public static void newTable(String dbName, String name, Column... columns) throws IOException, MalformedRequestException {
        if (containsTable(dbName, name)) {
            throw new MalformedRequestException("Table " + name + " already exists in " + dbName);
        }
        String baseName = name.endsWith(".csv")
                ? name.substring(0, name.length() - 4)
                : name;

        File tempFile = File.createTempFile("temp_udb", ".zip");

        try (
                ZipFile zipFile = new ZipFile(dbName);
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))
        ) {
            // -----------------------------
            // 1. Copy all entries *except* schema.json
            // -----------------------------
            for (ZipEntry oldEntry : Collections.list(zipFile.entries())) {
                if (oldEntry.getName().equals("schema.json")) continue;

                ZipEntry newEntry = new ZipEntry(oldEntry);
                zos.putNextEntry(newEntry);

                try (InputStream is = zipFile.getInputStream(oldEntry)) {
                    is.transferTo(zos);
                }

                zos.closeEntry();
            }

            // -----------------------------
            // 2. Load and update schema.json
            // -----------------------------
            JsonObject root;
            {
                ZipEntry schemaEntry = zipFile.getEntry("schema.json");
                if (schemaEntry == null) {
                    throw new IOException("schema.json is missing in the UDB file.");
                }

                try (InputStream is = zipFile.getInputStream(schemaEntry)) {
                    root = gson.fromJson(new String(is.readAllBytes()), JsonObject.class);
                }
            }

            JsonArray tables = root.getAsJsonArray("tables");

            // Prevent duplicates
            boolean exists = tables.asList().stream()
                    .anyMatch(e -> e.getAsString().equals(baseName));
            if (!exists) {
                tables.add(baseName);
            }

            // Write updated schema.json
            zos.putNextEntry(new ZipEntry("schema.json"));
            zos.write(gson.toJson(root).getBytes());
            zos.closeEntry();

            // -----------------------------
            // 3. Write header file (baseName.header.json)
            // -----------------------------
            zos.putNextEntry(new ZipEntry(baseName + ".header.json"));

            JsonObject header = new JsonObject();
            header.addProperty("name", baseName);

            JsonObject colsJson = new JsonObject();
            for (Column col : columns) {
                colsJson.addProperty(col.name(), col.type().toString());
            }
            header.add("columns", colsJson);

            zos.write(gson.toJson(header).getBytes());
            zos.closeEntry();


            zos.putNextEntry(new ZipEntry(baseName + ".data"));

            String sep = String.valueOf(CSV.SEP);
            String headerLine = String.join(sep,
                    Arrays.stream(columns).map(Column::name).toList()
            ) + "\n";

            zos.write(headerLine.getBytes());
            zos.closeEntry();
        }

        // Atomically replace old file
        Files.move(tempFile.toPath(), Path.of(dbName), StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Added table '" + baseName + "' to " + dbName);
    }
    public static ColumnType getColumnType(String type) {
        return switch (type.toLowerCase()) {
            case "text" -> ColumnType.TEXT;
            case "autoincrement_id" -> ColumnType.AUTOINCREMENT_ID;
            case "number" -> ColumnType.NUMBER;
            case "boolean" -> ColumnType.BOOLEAN;
            default -> null;
        };
    }
    public static void createTable(List<String> arguments) throws IOException, MalformedRequestException {
        String filename = arguments.removeFirst();
        String table_name = arguments.removeFirst();
        ArrayList<Column> cols = new ArrayList<>();
        for (String str : arguments) {
            String[] splat = str.split(":");
            cols.add(new Column(getColumnType(splat[1]), splat[0]));
        }
        Database.newTable(filename, table_name, cols.toArray(Column[]::new));
    }
    public static void getAll(String fileName) throws IOException {
        if (!fileName.endsWith(".udb")) {
            if (fileName.contains(".")) {
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
            }
            fileName = fileName + ".udb";

        }
        try (ZipFile zipFile = new ZipFile(fileName)) {
            for (ZipEntry entry : Collections.list(zipFile.entries())) {
                StringBuilder contents = new StringBuilder();
                String line;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)))) {
                    while ((line = br.readLine()) != null) {
                        contents.append(line).append("\n");
                    }
                }
                contents = new StringBuilder(contents.toString().replace(CSV.SEP, '|'));
                System.out.printf(
                        "---------------\nname: %s\nsize: %s\ncontents: \n%s\n",
                        entry.getName(),
                        entry.getSize(),
                        contents
                );
            }
        }
    }
    public static void CLI()  throws IOException {
        for (;;) {
            System.out.print(">>> ");
            Scanner scanner = new Scanner(System.in);

            String line = scanner.nextLine();
            char[] chars = line.toCharArray();
            line = line
                    .toLowerCase()
                    .replaceAll(">", "")
                    .replaceAll("<", "")
                    .trim();
            if (line.startsWith("new")) {
                if (!Character.isWhitespace(chars[3])) throw new IncorrectSyntaxException("structure of load command must be new <database name>");

                String file = line.substring(4);
                if (!file.endsWith(".udb")) {
                    if (file.contains(".")) {
                        file = file.substring(0, file.lastIndexOf("."));
                        file = file + ".udb";
                    }
                }
                New(file);
            } else if (line.startsWith("dump")) {
                if (!Character.isWhitespace(chars[4])) throw new IncorrectSyntaxException("structure of load command must be load <database file>");
                String file = line.substring(5, line.length() - 1);
                getAll(file);
            }
        }
    }
    public static String[][] readTable(String dbFile, String tableName) throws IOException {

        if (!dbFile.endsWith(".udb")) {
            int dot = dbFile.lastIndexOf('.');
            if (dot != -1) {
                dbFile = dbFile.substring(0, dot);
            }
            dbFile = dbFile + ".udb";
        }
        if (!tableName.endsWith(".data")) {
            if (tableName.contains(".")) {
                tableName = tableName.substring(0, tableName.lastIndexOf("."));
            }
            tableName = tableName + ".data";
        }
        ArrayList<String[]> lines = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(dbFile)) {

            ZipEntry entry = zipFile.getEntry(tableName);
            if (entry == null) return(new String[0][0]);

            String line;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)))) {
                while ((line = br.readLine()) != null) {
                    lines.add(line.split(String.valueOf(CSV.SEP), -1));
                }
            }


        }
        return lines.toArray(String[][]::new);

    }
    public static void insertIntoTable(String dbName, String tableName, List<List<String>> rows) throws IOException, MalformedRequestException {
        if (!dbName.endsWith(".udb")) {
            int dot_index = dbName.lastIndexOf('.');
            if (dot_index != -1) {
                dbName = dbName.substring(0, dot_index);
            }
            dbName = dbName + ".udb";
        }

        if (!containsTable(dbName, tableName)) {
            throw new MalformedRequestException("Table " + tableName + " does not exist in " + dbName);
        }

        String dataEntryName = tableName + ".data";
        File tempFile = File.createTempFile("temp_udb_insert", ".zip");

        try (
                ZipFile zipFile = new ZipFile(dbName);
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))
        ) {
            for (ZipEntry oldEntry : Collections.list(zipFile.entries())) {
                ZipEntry newEntry = new ZipEntry(oldEntry.getName());
                zos.putNextEntry(newEntry);

                try (InputStream is = zipFile.getInputStream(oldEntry)) {
                    is.transferTo(zos);
                }

                if (oldEntry.getName().equals(dataEntryName)) {
                    StringBuilder newContent = new StringBuilder();
                    String sep = String.valueOf(CSV.SEP);

                    for (List<String> row : rows) {
                        newContent.append(String.join(sep, row)).append("\n");
                    }

                    zos.write(newContent.toString().getBytes());
                }

                zos.closeEntry();
            }
        }

        Files.move(tempFile.toPath(), Path.of(dbName), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Inserted " + rows.size() + " rows into '" + tableName + "' in " + dbName);
    }
}
