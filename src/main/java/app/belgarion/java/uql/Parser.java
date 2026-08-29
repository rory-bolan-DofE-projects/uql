package app.belgarion.java.uql;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        } else if (file_line.startsWith("update ")) {
            return update(file_line.substring("update ".length()).trim());
        } else if (file_line.startsWith("delete from ")) {
            return deleteFrom(file_line.substring("delete from ".length()).trim());
        }
        return new Response(new ArrayList<>(), Optional.empty(), false);
    }
    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '"') {
                int end = line.indexOf('"', i + 1);
                if (end == -1) end = line.length();
                tokens.add(line.substring(i + 1, end));
                i = end + 1;
                continue;
            }
            if (c == '>' || c == '<' || c == '=') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '=') {
                    tokens.add(line.substring(i, i + 2));
                    i += 2;
                } else {
                    tokens.add(String.valueOf(c));
                    i++;
                }
                continue;
            }
            if (c == ',' || c == ':' || c == ';') {
                tokens.add(String.valueOf(c));
                i++;
                continue;
            }
            int start = i;
            while (i < line.length() && !Character.isWhitespace(line.charAt(i)) && ",:;\"><=".indexOf(line.charAt(i)) == -1) {
                i++;
            }
            tokens.add(line.substring(start, i));
        }
        return tokens;
    }
    private static class Words {
        private final List<String> tokens;
        private int pos = 0;
        private Words(List<String> tokens) {
            this.tokens = tokens;
        }
        private boolean hasNext() {
            return pos < tokens.size();
        }
        private String peek() {
            return hasNext() ? tokens.get(pos) : "";
        }
        private String next() {
            return hasNext() ? tokens.get(pos++) : "";
        }
        private void expect(String word) throws IncorrectQuerySyntaxException {
            String got = next();
            if (!got.equalsIgnoreCase(word)) {
                throw new IncorrectQuerySyntaxException("Expected '" + word + "' but found '" + got + "'");
            }
        }
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

    private static String[] parseCondition(String chunk) {
        String operator;
        if (chunk.contains(">=")) operator = ">=";
        else if (chunk.contains("<=")) operator = "<=";
        else if (chunk.contains(">")) operator = ">";
        else if (chunk.contains("<")) operator = "<";
        else if (chunk.contains("=")) operator = "=";
        else return null;

        String[] parts = chunk.split(operator, 2);
        if (parts.length != 2) return null;

        return new String[]{parts[0].trim(), operator, parts[1].trim()};
    }

    private static Response update(String query) throws IncorrectQuerySyntaxException, IOException, Database.MalformedRequestException {
        char[] chars = query.toCharArray();
        int index = 0;
        while (index < chars.length && Character.isWhitespace(chars[index])) index++;

        StringBuilder tablenameBuilder = new StringBuilder();
        while (index < chars.length && !Character.isWhitespace(chars[index])) {
            tablenameBuilder.append(chars[index]);
            index++;
        }
        String tablename = tablenameBuilder.toString();

        String rest = query.substring(index).trim();
        if (!rest.toLowerCase().startsWith("set ")) {
            throw new IncorrectQuerySyntaxException("The structure of the query should be 'update table_name set col=value where ...'");
        }
        rest = rest.substring(4).trim();

        String setPart = rest;
        String wherePart = "";
        int whereIdx = rest.toLowerCase().indexOf(" where ");
        if (whereIdx != -1) {
            setPart = rest.substring(0, whereIdx).trim();
            wherePart = rest.substring(whereIdx + 7).trim().replace(";", "");
        } else {
            setPart = setPart.replace(";", "").trim();
        }

        String[][] csv = Database.readTable(dbFile, tablename);
        if (csv.length == 0) {
            return new Response(new ArrayList<>(), Optional.empty(), false);
        }
        ArrayList<String> columns = new ArrayList<>(List.of(csv[0]));

        Map<Integer, String> assignments = new HashMap<>();
        for (String assignment : setPart.split(",")) {
            String[] bits = assignment.trim().split("=", 2);
            if (bits.length != 2) continue;
            int colindex = columns.indexOf(bits[0].trim());
            if (colindex == -1) continue;
            assignments.put(colindex, bits[1].trim());
        }

        String[] wherebits = wherePart.isEmpty() ? null : parseCondition(wherePart);

        Map<Integer, List<String>> newrows = new HashMap<>();
        for (int i = 1; i < csv.length; i++) {
            String[] row = csv[i];
            boolean matches = true;
            if (wherebits != null) {
                int colindex = columns.indexOf(wherebits[0]);
                matches = colindex != -1 && colindex < row.length && doacondition(row[colindex], wherebits[1], wherebits[2]);
            }
            if (!matches) continue;

            String[] newrow = row.clone();
            for (Map.Entry<Integer, String> entry : assignments.entrySet()) {
                if (entry.getKey() < newrow.length) newrow[entry.getKey()] = entry.getValue();
            }
            newrows.put(i - 1, Arrays.asList(newrow));
        }

        Database.updateRows(dbFile, tablename, newrows);
        return new Response(new ArrayList<>(), Optional.empty(), true);
    }

    private static Response deleteFrom(String query) throws IOException, Database.MalformedRequestException {
        char[] chars = query.toCharArray();
        int index = 0;
        while (index < chars.length && Character.isWhitespace(chars[index])) index++;

        StringBuilder tablenameBuilder = new StringBuilder();
        while (index < chars.length && !Character.isWhitespace(chars[index])) {
            tablenameBuilder.append(chars[index]);
            index++;
        }
        String tablename = tablenameBuilder.toString();

        String rest = query.substring(index).trim();
        String wherePart = rest.toLowerCase().startsWith("where ") ? rest.substring(6).trim().replace(";", "") : "";

        String[][] csv = Database.readTable(dbFile, tablename);
        if (csv.length == 0) {
            return new Response(new ArrayList<>(), Optional.empty(), false);
        }
        ArrayList<String> columns = new ArrayList<>(List.of(csv[0]));

        String[] wherebits = wherePart.isEmpty() ? null : parseCondition(wherePart);

        Set<Integer> rowstodelete = new HashSet<>();
        for (int i = 1; i < csv.length; i++) {
            String[] row = csv[i];
            boolean matches = true;
            if (wherebits != null) {
                int colindex = columns.indexOf(wherebits[0]);
                matches = colindex != -1 && colindex < row.length && doacondition(row[colindex], wherebits[1], wherebits[2]);
            }
            if (matches) rowstodelete.add(i - 1);
        }

        Database.deleteRows(dbFile, tablename, rowstodelete);
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