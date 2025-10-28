package app.belgarion.java;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;

public class Database {
    private static final Gson gson = new Gson();
    private static String fileName;
    public static void New(String name) throws IOException {
        name = name.trim();
        fileName = name;
        if (!name.endsWith(".udb")) {
            System.err.printf("Filename %s isn't the UDB file type", name);
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
    public static void newTable(String name, Column... columns) throws IOException {
        File tempFile = File.createTempFile("temp_udb", ".zip");

        try (
                ZipFile zipFile = new ZipFile(fileName);
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))
        ) {
            for (ZipEntry entry : Collections.list(zipFile.entries())) {
                if (!entry.getName().equals("schema.json")) {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        is.transferTo(zos);
                    }
                    zos.closeEntry();
                }
            }

            ZipEntry schemaEntry = zipFile.getEntry("schema.json");
            JsonObject root = gson.fromJson(
                    new String(zipFile.getInputStream(schemaEntry).readAllBytes()),
                    JsonObject.class
            );

            JsonArray tables = root.getAsJsonArray("tables");
            tables.add(name);

            zos.putNextEntry(new ZipEntry("schema.json"));
            zos.write(gson.toJson(root).getBytes());
            zos.closeEntry();

            String baseName = name.endsWith(".csv") ? name.substring(0, name.length() - 4) : name;

            zos.putNextEntry(new ZipEntry(baseName + ".header.json"));
            JsonObject header = new JsonObject();
            header.addProperty("name", baseName);

            JsonObject cols = new JsonObject();
            for (Column column : columns) {
                cols.addProperty(column.name(), column.type().toString());
            }
            header.add("columns", cols);
            zos.write(gson.toJson(header).getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(baseName + ".csv"));
            String csvHeader = Arrays.stream(columns)
                    .map(Column::name)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("") + "\n";
            zos.write(csvHeader.getBytes());
            zos.closeEntry();
        }

        Files.move(tempFile.toPath(), Path.of(fileName), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Added table '" + name + "' to " + fileName);
    }
    static void CLI(String fileName)  throws IOException {
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
            if (line.startsWith("load")) {
                if (!Character.isWhitespace(chars[4])) throw new IncorrectSyntaxException("structure of load command must be load <database file>");
                String file = line.substring(5, line.length() - 1);
                try (
                        ZipFile zipFile = new ZipFile(file);
                        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))
                        ) {
                    zipFile.stream().forEach(entry -> {
                        System.out.println("Entry: " + entry.getName());
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                            String lineFromReader;
                            while ((lineFromReader = reader.readLine()) != null) {
                                System.out.println(lineFromReader);
                            }
                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                        }
                    });
                }
            }
        }
    }

}
