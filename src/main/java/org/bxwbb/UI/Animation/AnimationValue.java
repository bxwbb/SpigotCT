package org.bxwbb.UI.Animation;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class AnimationValue<T extends Number> {

    Supplier<T> getFunction;
    Consumer<Double> setFunction;

    public void setGetFunction(Supplier<T> getFunction) {
        this.getFunction = getFunction;
    }

    public void setSetFunction(Consumer<Double> setFunction) {
        this.setFunction = setFunction;
    }

    protected T getValue() {
        return getFunction.get();
    }

    protected void setValue(double value) {
        setFunction.accept(value);
    }

}
