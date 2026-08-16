package app.belgarion.java.uql;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import app.belgarion.java.db_files.Database;
import org.jetbrains.annotations.NotNull;

public class Parser {
    public static void main(String[] args) throws IncorrectQuerySyntaxException, MalformedTableException, IOException, Database.MalformedRequestException {
        Response[] resp = execute(new String[]{"create-table ./test2.udb test2 key:autoincrement_id name:text age:number"});
    }
    public static Response[] execute(String[] file_lines) throws IncorrectQuerySyntaxException, IOException, MalformedTableException, Database.MalformedRequestException {
        Response[] resp = new Response[file_lines.length];
        for (int i = 0; i < file_lines.length; i++) {
            resp[i] = runOneLine(file_lines[i]);
        }
        return resp;
    }
    private static Response runOneLine(String file_line) throws IncorrectQuerySyntaxException, IOException, MalformedTableException, Database.MalformedRequestException {
        if (file_line.startsWith("select ")) {
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
        int index = 0, exponent = 1;

        while (Character.isWhitespace(chars[index])) index++;
        if (Character.isDigit(chars[index])) {
            int fin = 0;
            while (Character.isDigit(chars[index])) {
                int digit = Integer.parseInt(String.valueOf(chars[index]));
                int exponentiated = digit * exponent;
                fin += exponentiated;
                index++;
                exponent *= 10;
            }
            do index++; while (Character.isWhitespace(chars[index]));
            // calculated rows for selection
        /*
        select 3 rows from table
                 ^
         */
            if (chars[index] != 'r') {
                throw new IncorrectQuerySyntaxException("The structure of the query should be 'select x rows from table_name'");
            }
            index += 10;
            StringBuilder table_name = new StringBuilder();
            while (!Character.isWhitespace(chars[index])) {
                table_name.append(chars[index]);
                index++;
            }
            /*
            select 3 rows from table in filename
                                    ^
             */
            index+=4;
            /*
            select 3 rows from table in filename;
                                        ^
             */
            StringBuilder fileName = new StringBuilder();
            while (chars[index] != ';') {
                fileName.append(chars[index]);
                index++;
            }

            String[][] csv = Database.readTable(fileName.toString(), table_name.toString());

            // yes this is overcomplicated, but I really couldn't care less
        } else {
            // select all rows from table in file_name;
            //        ^

            index+=14;

            StringBuilder table_name = new StringBuilder();
            while (!Character.isWhitespace(chars[index])) {
                table_name.append(chars[index]);
                index++;
            }
            // select all rows from table in filename;
            //                           ^
            // needs to be:
            // select all rows from table in filename;
            //                               ^
            index+=4;
            StringBuilder filename = new StringBuilder();
            while (Character.isWhitespace(chars[index])) index++;
            while (chars[index] != ';') {
                filename.append(chars[index]);
                index++;
            }
            String[][] csv = Database.readTable(filename.toString(), table_name.toString());
            ArrayList<Row> rows = getRows(csv);
            ArrayList<String> columns = new ArrayList<>(List.of(csv[0]));
            return new Response(columns, Optional.of(rows.toArray(Row[]::new)), true);

        }
        return new Response(new ArrayList<>(), Optional.empty(), false);
    }

    private static Response createTable(String query) throws IOException, Database.MalformedRequestException {
        // query should be "filename table_name columns" and columns is name:type
        ArrayList<String> parameters = new ArrayList<>(List.of(query.split(" ")));
        Database.createTable(parameters);
        return new Response(new ArrayList<>(), Optional.empty(), true);
    }

    private static Response addToTable(String query) throws Database.MalformedRequestException, IOException {
        char[] chars = query.toCharArray();
        // full command should be insert into database.udb/table value1 value2 value3 value4...
        int index = 0;
        while (Character.isWhitespace(chars[index])) index++;
        // now this should point to the first letter of the database name
        StringBuilder dbnamebuilder = new StringBuilder();
        while (chars[index] != '/') {
            dbnamebuilder.append(chars[index]);
            index++;
        }
        String dbname = dbnamebuilder.toString();
        index++;
        // we are now pointing at the first letter of the table name
        StringBuilder tablenameBuilder = new StringBuilder();
        while (!Character.isWhitespace(chars[index])) {
            tablenameBuilder.append(chars[index]);
            index++;
        }
        String tablename = tablenameBuilder.toString();
        index++;
        StringBuilder valuestringbuilder = new StringBuilder();
        // we are now pointing to the first letter of the items to be added to the table
        while (index < chars.length) {
            valuestringbuilder.append(chars[index]);
            index++;
        }
        ArrayList<String> innerList = new ArrayList<>(Arrays.asList(valuestringbuilder.toString().split(" ")));
        ArrayList<List<String>> values = new ArrayList<>();
        values.add(innerList);
        Database.insertIntoTable(dbname, tablename, values);
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
    }

    public static class MalformedTableException extends Exception {
        private MalformedTableException(String message) {
            super(message);
        }
    }
}