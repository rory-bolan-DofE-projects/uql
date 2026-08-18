package app.belgarion.java.db_files;



import app.belgarion.java.uql.Parser;

//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//
//import static app.belgarion.java.db_files.Database.*;



public class Main {
    public static void run(String[] args) {
                try {
                    String dbName = "test_game.udb";

                    System.out.println("--- 1. Creating Database ---");
                    Database.New(dbName);

                    String[] queries = {
                            "load test_game.udb",
                            "create-table players id:autoincrement_id username:text score:number",


                            "insert into players 1 BelgarionofRiva 2500",
                            "insert into players 2 BelgarionofRiva2 3200",

                            "select all rows from players;"
                    };


                    System.out.println("\n2. Executing UQL Queries");
                    Parser.Response[] responses = Parser.execute(queries);


                    System.out.println("\n3. Query Results");
                    for (int i = 0; i < responses.length; i++) {
                        System.out.println("Query " + (i + 1) + ":");
                        System.out.println(responses[i].toString());
                    }

                    System.out.println("4. Internal Database Dump");
                    Database.getAll(dbName);

                } catch (Exception e) {
                    System.err.println("An error occurred during testing:");
                    e.printStackTrace();
                }


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




    public static void main(String[] args){

        run(args);
    }
}