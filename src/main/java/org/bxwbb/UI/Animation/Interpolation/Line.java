package org.bxwbb.UI.Animation.Interpolation;

import org.bxwbb.UI.Animation.InterpolationFunction;

public class Line extends InterpolationFunction {

    private double startNumber = 0, endNumber = 1;

    @Override
    public double interpolation(double t) {
        return (endNumber - startNumber) * t + startNumber;
    }

    public double getStartNumber() {
        return startNumber;
    }

    public void setStartNumber(double startNumber) {
        this.startNumber = startNumber;
    }

    public double getEndNumber() {
        return endNumber;
    }

    public void setEndNumber(double endNumber) {
        this.endNumber = endNumber;
    }

}
