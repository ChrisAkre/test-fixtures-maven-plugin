package sample.b;

import org.junit.jupiter.api.Test;
import sample.a.FixtureClassA;
import sample.a.MainClassA;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModuleBTest {

    @Test
    public void testUsingModuleAFixture() {
        MainClassA mainA = FixtureClassA.createInstance();
        assertEquals("Hello from Module A", mainA.getMessage());
    }
}
