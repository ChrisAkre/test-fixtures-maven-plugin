package sample;

import org.mockito.Mockito;

public class FixtureClass {
    public static MainClass mockMain() {
        MainClass mock = Mockito.mock(MainClass.class);
        Mockito.when(mock.greeting()).thenReturn("Hello from Fixture");
        return mock;
    }
}
