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
    static void getAll(String fileName) throws IOException {
        if (!fileName.endsWith(".udb")) {
            if (fileName.contains(".")) {
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
                fileName = fileName + ".udb";
            }

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
                System.out.printf(
                        "---------------\nname: %s\nsize: %s\ncontents: \n%s\n",
                        entry.getName(),
                        entry.getSize(),
                        contents
                );
            }
        }
        Console console =  System.console();
    }
    static void CLI()  throws IOException {
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

}
