package app.belgarion.java;



import java.io.IOException;
import java.util.Arrays;

import static app.belgarion.java.Database.*;



public class Main  {



    public static void main(String[] args) throws IOException {


        if (Arrays.equals(args, new String[]{})) {
            CLI();
            System.exit(0);
        }
        String command = args[0];
        String file = args[1];
        if (!command.equals("new") && !command.equals("load")) {
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
        } else {
            if (file == null) {
                System.err.println("Missing command line argument: database file");
                System.exit(1);
            }
            if (!file.endsWith(".udb")) {
                System.err.printf("File '%s' does not end with .udb%n", file);
                System.exit(1);
            }
            
        }

    }
}
