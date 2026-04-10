package sample;

import org.assertj.core.api.AbstractAssert;

public class MainClassAssert extends AbstractAssert<MainClassAssert, MainClass> {

    protected MainClassAssert(MainClass actual) {
        super(actual, MainClassAssert.class);
    }

    public static MainClassAssert assertThat(MainClass actual) {
        return new MainClassAssert(actual);
    }

    public MainClassAssert hasGreeting(String expected) {
        isNotNull();
        if (!actual.greeting().equals(expected)) {
            failWithMessage("Expected MainClass to have greeting %s but was %s", expected, actual.greeting());
        }
        return this;
    }
}
