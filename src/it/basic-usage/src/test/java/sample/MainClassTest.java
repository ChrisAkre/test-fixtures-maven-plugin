package sample;

import org.junit.jupiter.api.Test;
import static sample.MainClassAssert.assertThat;

public class MainClassTest {
    
    @Test
    public void testMainGreeting() {
        MainClass main = new MainClass();
        assertThat(main).hasGreeting("Hello World");
    }
}
