package app.belgarion.java.db_files;



import app.belgarion.java.uql.Parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//
//import static app.belgarion.java.db_files.Database.*;



public class Main {
    public static void run(String dbName, String[] args) throws IOException, Database.MalformedRequestException, Parser.IncorrectQuerySyntaxException, Parser.MalformedTableException {



                    System.out.println("--- 1. Creating Database ---");
                    Database.New(dbName);




                    System.out.println("\n2. Executing UQL Queries");
                    Parser.Response[] responses = Parser.execute(args);


                    System.out.println("\n3. Query Results");
                    for (int i = 0; i < responses.length; i++) {
                        System.out.println("Query " + (i + 1) + ":");
                        System.out.println(responses[i].toString());
                    }

                    System.out.println("4. Internal Database Dump");
                    Database.getAll(dbName);




//        if (Arrays.equals(args, new String[]{})) {
//            CLI();
//            System.exit(1);
//        }
//        String command = args[0];
//        String file = args[1];
//        if (!command.equals("new") && !command.equals("load") && !command.equals("create-table")) {
//            System.err.println("Unknown command: " + command);
//            System.exit(1);
//        }
//        if (command.equals("new")) {
//            if (file == null) {
//                System.err.println("Missing command line argument: database file");
//                System.exit(1);
//            }
//            if (!file.endsWith(".udb")) {
//                System.err.printf("File '%s' does not end with .udb\n", file);
//                System.exit(1);
//            }
//            New(file);
//        } else if (command.equals("load")) {
//            if (file == null) {
//                System.err.println("Missing command line argument: database file");
//                System.exit(1);
//            }
//            if (!file.endsWith(".udb")) {
//                System.err.printf("File '%s' does not end with .udb%n", file);
//                System.exit(1);
//            }
//            Database.getAll(file);
//
//        } else {
//            // uql create-table filename table_name columns
//            List<String> arguments = new ArrayList<>(List.of(args));
//            arguments.removeFirst();
//            Database.createTable(arguments);
//        }

    }




    public static void main(String[] args) throws Database.MalformedRequestException, Parser.IncorrectQuerySyntaxException, Parser.MalformedTableException, IOException {
        if (args.length == 0) return;
        if (!Files.exists(Path.of(args[0]))) return;
        ArrayList<String> list = new ArrayList<>();
        BufferedReader reader;

        reader = new BufferedReader(new FileReader(args[0]));
        String line = reader.readLine();

        while (line != null) {
            if (!line.trim().isEmpty()) list.add(line);
            line = reader.readLine();
        }

        reader.close();
        String[] strings = list.toArray(String[]::new);
        Parser.Response[] resps = Parser.execute(strings);
        for (int i = 0; i < resps.length; i++) {
            System.out.println("Query " + (i + 1) + ":");
            System.out.println(resps[i].toString());
        }
    }
}