package app.belgarion.java.uql;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import app.belgarion.java.db_files.Database;
public class Parser {
    public static void main(String[] args) throws IncorrectQuerySyntaxException, MalformedTableException, IOException {
       Response[] resp = execute(new String[]{"select 2 from"});
    }
    public static Response[] execute(String[] file_lines) throws IncorrectQuerySyntaxException, IOException, MalformedTableException {
        Response[] resp = new Response[file_lines.length];
        for (int i = 0; i < file_lines.length; i++) {
            resp[i] = runOneLine(file_lines[i]);
        }
        return resp;
    }
    private static Response runOneLine(String file_line) throws IncorrectQuerySyntaxException, IOException, MalformedTableException {
        if (file_line.startsWith("select ")) {
           return select(file_line.substring("select ".length()).trim());
        }
        return new Response(Optional.empty());
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
            // select all rows from table_name; in file_name;
            //        ^

            index+=14;

            StringBuilder table_name = new StringBuilder();
            while (chars[index] != ';') {
                table_name.append(chars[index]);
                index++;
            }
            /*
            String[][] csv = Database.readTable(table_name.toString());
            ArrayList<Row> rows = getRows(csv);



            return new Response(Optional.of(rows.toArray(Row[]::new)));

             */
        }
        return new Response(Optional.empty());
    }

    private static ArrayList<Row> getRows(String[][] csv) throws MalformedTableException {
        ArrayList<Row> rows = new ArrayList<>();


        for (int i = 1; i < csv.length; ++i) {
            if (csv[i].length != csv[0].length) {
                throw new MalformedTableException("All rows must have the same amount of columns");
            }
            HashMap<String, String> rowTemp = new HashMap<>();
            for (int j = 0; j < csv[i].length; ++j) {
                rowTemp.put(csv[0][j], csv[i][j]);
            }
            rows.add(new Row(rowTemp));
        }
        return rows;
    }

    public record Row(HashMap<String, String> columns) {}
    public record Response(Optional<Row[]> rows) {}
    public static class MalformedTableException extends Exception {
        private MalformedTableException(String message) {
            super(message);
        }
    }
    
}
