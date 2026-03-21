package sample;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainClassTest {
    
    @Test
    public void testMockedMain() {
        MainClass mock = FixtureClass.mockMain();
        assertEquals("Hello from Fixture", mock.greeting());
    }
}
