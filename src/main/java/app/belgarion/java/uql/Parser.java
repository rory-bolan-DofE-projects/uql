package app.belgarion.java.uql;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import app.belgarion.java.db_files.Database;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonSerializer;
import org.jetbrains.annotations.NotNull;

public class Parser {
    public static String dbFile = "";

    public static void main(String[] args) throws IOException, Database.MalformedRequestException, IncorrectQuerySyntaxException, MalformedTableException {
        String dbName = "./test_database.udb";


            Database.New(dbName);

            String[] testScript = new String[]{
                    "load " + dbName,
                    "create-table users id:number username:text email:text",
                    "insert into users 1 belgarion user1@example.com",
                    "insert into users 2 polgara user2@example.com",
                    "insert into users 3 silk user3@example.com",
                    "select all rows from users;",
                    "select 2 rows from users;"
            };

            System.out.println("\n--- Starting Query Execution ---\n");
            Response[] responses = execute(testScript);

            for (int i = 0; i < testScript.length; i++) {
                System.out.println("Command [" + (i + 1) + "]: " + testScript[i]);
                System.out.println("Response:");
                System.out.println(responses[i]);
                System.out.println("-----------------------------------");
            }

    }

    public static Response[] execute(String[] file_lines) throws IncorrectQuerySyntaxException, IOException, MalformedTableException, Database.MalformedRequestException {
        Response[] resp = new Response[file_lines.length];
        if (!file_lines[0].trim().startsWith("load")) {
            throw new IncorrectQuerySyntaxException("You must load a table first");
        }
        for (int i = 0; i < file_lines.length; i++) {
            resp[i] = runOneLine(file_lines[i]);
        }
        return resp;
    }

    private static Response runOneLine(String file_line) throws IncorrectQuerySyntaxException, IOException, MalformedTableException, Database.MalformedRequestException {
        if (file_line.startsWith("load")) {
            loadTable(file_line);
            return new Response(new ArrayList<>(), Optional.empty(), true);
        } else if (file_line.startsWith("select ")) {
            return select(file_line.substring("select ".length()).trim());
        } else if (file_line.startsWith("create-table ")) {
            return createTable(file_line.substring("create-table ".length()).trim());
        } else if (file_line.startsWith("insert into ")) {
            return addToTable(file_line.substring("insert into ".length()).trim());
        }
        return new Response(new ArrayList<>(), Optional.empty(), false);
    }

    public static class IncorrectQuerySyntaxException extends Exception {
        private IncorrectQuerySyntaxException(String message) {
            super(message);
        }
    }

    private static Response select(String query) throws IncorrectQuerySyntaxException, IOException, MalformedTableException {
        char[] chars = query.toCharArray();
        int index = 0;

        while (index < chars.length && Character.isWhitespace(chars[index])) index++;

        if (Character.isDigit(chars[index])) {
            StringBuilder countStr = new StringBuilder();
            while (index < chars.length && Character.isDigit(chars[index])) {
                countStr.append(chars[index]);
                index++;
            }
            int maxrows = Integer.parseInt(countStr.toString());

            while (index < chars.length && Character.isWhitespace(chars[index])) index++;

            /*
            select 3 rows from table_name;
                     ^
            */
            if (index >= chars.length || chars[index] != 'r') {
                throw new IncorrectQuerySyntaxException("The structure of the query should be 'select x rows from table_name'");
            }
            index += 10; // skip "rows from "

            StringBuilder table_name = new StringBuilder();
            while (index < chars.length && !Character.isWhitespace(chars[index]) && chars[index] != ';') {
                table_name.append(chars[index]);
                index++;
            }

            String cleanTableName = table_name.toString().trim();
            String[][] csv = Database.readTable(dbFile, cleanTableName);
            if (csv.length == 0) {
                return new Response(new ArrayList<>(), Optional.empty(), false);
            }

            ArrayList<Row> rows = getRows(csv);
            ArrayList<String> columns = new ArrayList<>(List.of(csv[0]));


            rows = filter(query, columns, rows);


            if (rows.size() > maxrows) {
                rows = new ArrayList<>(rows.subList(0, maxrows));
            }

            return new Response(columns, Optional.of(rows.toArray(Row[]::new)), true);

        } else {
            // select all rows from table_name
            index += 14; // skip "all rows from "

            StringBuilder table_name = new StringBuilder();
            for (int i = index; i < chars.length; i++) {
                table_name.append(chars[i]);
            }

            // regex i totally didnt steal that gets just the table name without anything before or after idk
            String table_name_cleaned = table_name.toString().trim().split("\\s+")[0].replace(";", "");

            String[][] csv = Database.readTable(dbFile, table_name_cleaned);
            if (csv.length == 0) {
                return new Response(new ArrayList<>(), Optional.empty(), false);
            }

            ArrayList<Row> rows = getRows(csv);
            ArrayList<String> columns = new ArrayList<>(List.of(csv[0]));


            rows = filter(query, columns, rows);

            return new Response(columns, Optional.of(rows.toArray(Row[]::new)), true);
        }
    }

    // private method that parses where clauses (stuff like "select all rows from table where id>1")
    private static ArrayList<Row> filter(String query, ArrayList<String> columns, ArrayList<Row> rows) {
        String lowerQuery = query.toLowerCase();
        if (!lowerQuery.contains(" where ")) {
            return rows;
        }

        int wherebitlocation = lowerQuery.indexOf(" where ") + 7;
        String chunk = query.substring(wherebitlocation).replace(";", "").trim();

        String operator;
        if (chunk.contains(">=")) operator = ">=";
        else if (chunk.contains("<=")) operator = "<=";
        else if (chunk.contains(">")) operator = ">";
        else if (chunk.contains("<")) operator = "<";
        else if (chunk.contains("=")) operator = "=";
        else return rows;

        String[] parts = chunk.split(operator, 2);
        if (parts.length != 2) {
            return rows;
        }

        String columnwanted = parts[0].trim();
        String valuewanted = parts[1].trim();

        int columnindex = columns.indexOf(columnwanted);
        if (columnindex == -1) {
            return rows;
        }

        ArrayList<Row> filtered = new ArrayList<>();
        for (Row row : rows) {
            if (row.columns().size() <= columnindex) continue;

            String currentvalue = row.columns().get(columnindex);
            if (doacondition(currentvalue, operator, valuewanted)) {
                filtered.add(row);
            }
        }

        return filtered;
    }

    private static boolean doacondition(String value, String op, String intendedvalue) {
        // try numbers first
        try {
            double cellNum = Double.parseDouble(value);
            double targetNum = Double.parseDouble(intendedvalue);

            return switch (op) {
                case "="  -> cellNum == targetNum;
                case ">"  -> cellNum > targetNum;
                case "<"  -> cellNum < targetNum;
                case ">=" -> cellNum >= targetNum;
                case "<=" -> cellNum <= targetNum;
                default   -> false;
            };
        } catch (NumberFormatException e) {
            // if it aint a number (for example: "select 3 rows from table where job=\"software_engineer\"")
            int cmp = value.compareTo(intendedvalue);
            return switch (op) {
                case "="  -> value.equals(intendedvalue);
                case ">"  -> cmp > 0;
                case "<"  -> cmp < 0;
                case ">=" -> cmp >= 0;
                case "<=" -> cmp <= 0;
                default   -> false;
            };
        }
    }
    private static void loadTable(String query) throws IOException {
        String dbname = query.trim().substring(4).trim();
        if (!Files.exists(Path.of(dbname))) {
            Database.New(dbname);
        }
        dbFile = dbname;
    }

    private static Response createTable(String query) throws IOException, Database.MalformedRequestException {
        ArrayList<String> parameters = new ArrayList<>(List.of(query.split(" ")));
        parameters.addFirst(dbFile);
        Database.createTable(parameters);
        return new Response(new ArrayList<>(), Optional.empty(), true);
    }

    private static Response addToTable(String query) throws Database.MalformedRequestException, IOException {
        char[] chars = query.toCharArray();

        int index = 0;
        while (index < chars.length && Character.isWhitespace(chars[index])) index++;

        // We are now pointing at the first letter of the table name
        StringBuilder tablenameBuilder = new StringBuilder();
        while (index < chars.length && !Character.isWhitespace(chars[index])) {
            tablenameBuilder.append(chars[index]);
            index++;
        }
        String tablename = tablenameBuilder.toString();

        // Skip the spaces between the table name and the values
        while (index < chars.length && Character.isWhitespace(chars[index])) index++;

        StringBuilder valuestringbuilder = new StringBuilder();
        // We are now pointing to the first letter of the items to be added to the table
        while (index < chars.length) {
            valuestringbuilder.append(chars[index]);
            index++;
        }

        ArrayList<String> innerList = new ArrayList<>(Arrays.asList(valuestringbuilder.toString().split(" ")));
        ArrayList<List<String>> values = new ArrayList<>();
        values.add(innerList);

        Database.insertIntoTable(dbFile, tablename, values);
        return new Response(new ArrayList<>(), Optional.empty(), true);
    }

    private static ArrayList<Row> getRows(String[][] csv) throws MalformedTableException {
        ArrayList<Row> rows = new ArrayList<>();

        for (int i = 1; i < csv.length; ++i) {
            if (csv[i].length != csv[0].length) {
                throw new MalformedTableException("All rows must have the same amount of columns");
            }
            ArrayList<String> rowTemp = new ArrayList<>(Arrays.asList(csv[i]));
            rows.add(new Row(rowTemp));
        }
        return rows;
    }

    public record Row(ArrayList<String> columns) {}

    public record Response(ArrayList<String> columns, Optional<Row[]> rows, boolean success) {
        public @NotNull String toString() {
            StringBuilder finalResponse = new StringBuilder();
            finalResponse.append(this.success() ? "request succeeded" : "request failed");
            finalResponse.append('\n');
            if (this.rows().isEmpty()) return finalResponse.toString();

            finalResponse.append('|');
            for (String columnName : columns) {
                finalResponse.append(columnName);
                finalResponse.append('|');
            }
            finalResponse.append('\n');

            for (Row row : rows.get()) {
                finalResponse.append('|');
                for (String item : row.columns()) {
                    finalResponse.append(item);
                    finalResponse.append('|');
                }
                finalResponse.append('\n');
            }
            return finalResponse.toString();
        }
        public static String responsesToJson(Response[] responses) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Optional.class, (JsonSerializer<Optional<?>>) (src, typeOfSrc, context) ->
                            src.isPresent() ? context.serialize(src.get()) : JsonNull.INSTANCE
                    )
                    .setPrettyPrinting()
                    .create();

            return gson.toJson(responses);
        }
    }

    public static class MalformedTableException extends Exception {
        private MalformedTableException(String message) {
            super(message);
        }
    }
}