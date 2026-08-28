package app.belgarion.java.db_files;

import app.belgarion.java.uql.Parser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

import static spark.Spark.*;

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
    }

    private static void api() {
        Gson gson = new Gson();
        port(8080);
        post("/", (req, res) -> {
            res.type("application/json");
            try {
                JSONbody body = gson.fromJson(req.body(), JSONbody.class);
                if (body == null || body.getRequests() == null || body.getRequests().length == 0) {
                    res.status(400);
                    return "{\"error\": \"Missing or empty field 'requests' in JSON body\"}";
                }
                Parser.Response[] resp = Parser.execute(body.getRequests());

                return Parser.Response.responsesToJson(resp);
            } catch (Exception e) {
                res.status(500);
                JsonObject err = new JsonObject();
                err.addProperty("error", e.getMessage());
                return gson.toJson(err);
            }
        });
    }

    private static class JSONbody {
        private String[] requests;
        public String[] getRequests() { return requests; }
    }

    public static void main(String[] args) throws Database.MalformedRequestException, Parser.IncorrectQuerySyntaxException, Parser.MalformedTableException, IOException {
        if (args.length == 0) return;
        if (Objects.equals(args[0], "api")) {
            api();
            return;
        }
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