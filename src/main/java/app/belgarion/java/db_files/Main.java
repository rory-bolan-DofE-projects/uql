package app.belgarion.java.db_files;



import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static app.belgarion.java.db_files.Database.*;



public class Main {
    public static void run(String[] args) throws IOException, MalformedRequestException {
        if (Arrays.equals(args, new String[]{})) {
            CLI();
            System.exit(1);
        }
        String command = args[0];
        String file = args[1];
        if (!command.equals("new") && !command.equals("load") && !command.equals("create-table")) {
            System.err.println("Unknown command: " + command);
            System.exit(1);
        }
        if (command.equals("new")) {
            if (file == null) {
                System.err.println("Missing command line argument: database file");
                System.exit(1);
            }
            if (!file.endsWith(".udb")) {
                System.err.printf("File '%s' does not end with .udb\n", file);
                System.exit(1);
            }
            New(file);
        } else if (command.equals("load")) {
            if (file == null) {
                System.err.println("Missing command line argument: database file");
                System.exit(1);
            }
            if (!file.endsWith(".udb")) {
                System.err.printf("File '%s' does not end with .udb%n", file);
                System.exit(1);
            }
            Database.getAll(file);

        } else {
            // uql create-table filename table_name columns
            List<String> arguments = new ArrayList<>(List.of(args));
            arguments.removeFirst();
            Database.createTable(arguments);
        }

    }




    public static void main(String[] args) throws IOException, MalformedRequestException {

        run(args);
    }
}