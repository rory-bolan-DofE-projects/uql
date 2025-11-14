import java.io.*;
public class test {
    public static void main(String[] args) {
        Console console = System.console();
        if (console==null){
            System.out.println("Console null");
            return;
        }
        String name = console.readLine("Enter your name: ");
        char[] password = console.readPassword("Enter your password: ");
        console.printf("Hello, %s! Your password is %d characters long.\n", name, password.length);
        StringBuilder str = new StringBuilder();
        for (char ch : password) {
            str.append(ch);
        }
        System.out.printf("your password is: %s\n", str);
        java.util.Arrays.fill(password, ' ');
    }
}