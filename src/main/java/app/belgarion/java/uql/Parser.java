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
        Words words = new Words(tokenize(file_line));
        if (!words.hasNext()) {
            return new Response(new ArrayList<>(), Optional.empty(), false);
        }
        String keyword = words.peek().toLowerCase();
        return switch (keyword) {
            case "load" -> {
                loadTable(words);
                yield new Response(new ArrayList<>(), Optional.empty(), true);
            }
            case "select" -> select(words);
            case "create-table" -> createTable(words);
            case "insert" -> addToTable(words);
            case "update" -> update(words);
            case "delete" -> deleteFrom(words);
            default -> new Response(new ArrayList<>(), Optional.empty(), false);
        };
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

    private static Response select(Words words) throws IncorrectQuerySyntaxException, IOException, MalformedTableException {
        words.expect("select");
        int maxrows = Integer.MAX_VALUE;
        if (words.peek().equalsIgnoreCase("all")) {
            words.next();
        } else {
            maxrows = Integer.parseInt(words.next());
        }
        words.expect("rows");
        words.expect("from");
        String tableName = words.next();
        String[] wherebits = null;
        if (words.hasNext() && words.peek().equalsIgnoreCase("where")) {
            words.next();
            wherebits = parseCondition(words);
        }
        String[][] csv = Database.readTable(dbFile, tableName);
        if (csv.length == 0) {
            return new Response(new ArrayList<>(), Optional.empty(), false);
        }
        ArrayList<Row> rows = getRows(csv);
        ArrayList<String> columns = new ArrayList<>(List.of(csv[0]));
        rows = filter(wherebits, columns, rows);
        if (rows.size() > maxrows) {
            rows = new ArrayList<>(rows.subList(0, maxrows));
        }
        return new Response(columns, Optional.of(rows.toArray(Row[]::new)), true);
    }
    // private method that parses where clauses (stuff like "select all rows from table where id>1")
    private static ArrayList<Row> filter(String[] wherebits, ArrayList<String> columns, ArrayList<Row> rows) {
        if (wherebits == null) return rows;
        int columnindex = columns.indexOf(wherebits[0]);
        if (columnindex == -1) return rows;
        ArrayList<Row> filtered = new ArrayList<>();
        for (Row row : rows) {
            if (row.columns().size() <= columnindex) continue;
            String currentvalue = row.columns().get(columnindex);
            if (doacondition(currentvalue, wherebits[1], wherebits[2])) {
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
    private static void loadTable(Words words) throws IOException, IncorrectQuerySyntaxException {
        words.expect("load");
        String dbname = words.next();
        if (!Files.exists(Path.of(dbname))) {
            Database.New(dbname);
        }
        dbFile = dbname;
    }
    private static Response createTable(Words words) throws IOException, Database.MalformedRequestException, IncorrectQuerySyntaxException {
        words.expect("create-table");
        String tableName = words.next();
        ArrayList<String> parameters = new ArrayList<>();
        parameters.add(dbFile);
        parameters.add(tableName);
        while (words.hasNext() && !words.peek().equals(";")) {
            String colName = words.next();
            words.expect(":");
            String colType = words.next();
            parameters.add(colName + ":" + colType);
        }
        Database.createTable(parameters);
        return new Response(new ArrayList<>(), Optional.empty(), true);
    }

    private static Response addToTable(Words words) throws IOException, Database.MalformedRequestException, IncorrectQuerySyntaxException {
        words.expect("insert");
        words.expect("into");
        String tableName = words.next();
        ArrayList<String> values = new ArrayList<>();
        while (words.hasNext() && !words.peek().equals(";")) {
            values.add(words.next());
        }
        ArrayList<List<String>> rows = new ArrayList<>();
        rows.add(values);
        Database.insertIntoTable(dbFile, tableName, rows);
        return new Response(new ArrayList<>(), Optional.empty(), true);
    }

    private static String[] parseCondition(Words words) {
        String column = words.next();
        String operator = words.next();
        String value = words.next();
        return new String[]{column, operator, value};
    }

    private static Response update(Words words) throws IncorrectQuerySyntaxException, IOException, Database.MalformedRequestException {
        words.expect("update");
        String tableName = words.next();
        words.expect("set");
        Map<String, String> assignmentsByName = new HashMap<>();
        while (true) {
            String col = words.next();
            words.expect("=");
            String value = words.next();
            assignmentsByName.put(col, value);
            if (!words.peek().equals(",")) break;
            words.next(); // eat the comma
        }
        String[] wherebits = null;
        if (words.hasNext() && words.peek().equalsIgnoreCase("where")) {
            words.next();
            wherebits = parseCondition(words);
        }
        String[][] csv = Database.readTable(dbFile, tableName);
        if (csv.length == 0) {
            return new Response(new ArrayList<>(), Optional.empty(), false);
        }
        ArrayList<String> columns = new ArrayList<>(List.of(csv[0]));
        Map<Integer, String> assignments = new HashMap<>();
        for (Map.Entry<String, String> entry : assignmentsByName.entrySet()) {
            int colindex = columns.indexOf(entry.getKey());
            if (colindex != -1) assignments.put(colindex, entry.getValue());
        }
        Map<Integer, List<String>> newrows = new HashMap<>();
        for (int i = 1; i < csv.length; i++) {
            String[] row = csv[i];
            if (wherebits != null) {
                int colindex = columns.indexOf(wherebits[0]);
                boolean matches = colindex != -1 && colindex < row.length && doacondition(row[colindex], wherebits[1], wherebits[2]);
                if (!matches) continue;
            }
            String[] newrow = row.clone();
            for (Map.Entry<Integer, String> entry : assignments.entrySet()) {
                if (entry.getKey() < newrow.length) newrow[entry.getKey()] = entry.getValue();
            }
            newrows.put(i - 1, Arrays.asList(newrow));
        }
        Database.updateRows(dbFile, tableName, newrows);
        return new Response(new ArrayList<>(), Optional.empty(), true);
    }
    private static Response deleteFrom(Words words) throws IncorrectQuerySyntaxException, IOException, Database.MalformedRequestException {
        words.expect("delete");
        words.expect("from");
        String tableName = words.next();
        String[] wherebits = null;
        if (words.hasNext() && words.peek().equalsIgnoreCase("where")) {
            words.next();
            wherebits = parseCondition(words);
        }
        String[][] csv = Database.readTable(dbFile, tableName);
        if (csv.length == 0) {
            return new Response(new ArrayList<>(), Optional.empty(), false);
        }
        ArrayList<String> columns = new ArrayList<>(List.of(csv[0]));
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
        Database.deleteRows(dbFile, tableName, rowstodelete);
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