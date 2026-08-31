package app.belgarion.java.uql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class ParserTest {
    @TempDir
    Path tempDir;
    private String dbFile;
    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("test.udb").toString();
    }
    @Test
    void insertAndSelect() throws Exception {
        Parser.Response[] responses = Parser.execute(new String[]{
                "load " + dbFile,
                "create-table players id:autoincrement_id username:text score:number",
                "insert into players belgarion 2500",
                "select all rows from players"
        });
        Parser.Response result = responses[3];
        assertEquals(1, result.rows().get().length);
        assertEquals("belgarion", result.rows().get()[0].columns().get(1));
    }
    @Test
    void whereClauseFilters() throws Exception {
        Parser.Response[] responses = Parser.execute(new String[]{
                "load " + dbFile,
                "create-table players id:autoincrement_id username:text score:number",
                "insert into players belgarion 2500",
                "insert into players silk 1800",
                "select all rows from players where score>2000"
        });
        Parser.Response result = responses[4];
        assertEquals(1, result.rows().get().length); // only belgarion should pass
    }
    @Test
    void updateThenDelete() throws Exception {
        Parser.Response[] responses = Parser.execute(new String[]{
                "load " + dbFile,
                "create-table players id:autoincrement_id username:text score:number",
                "insert into players belgarion 2500",
                "insert into players silk 1800",
                "update players set score=9999 where username=belgarion",
                "delete from players where score<2000",
                "select all rows from players"
        });
        Parser.Response result = responses[6];
        assertEquals(1, result.rows().get().length);         // silk got deleted
        assertEquals("9999", result.rows().get()[0].columns().get(2)); // belgarion's update stuck
    }
}