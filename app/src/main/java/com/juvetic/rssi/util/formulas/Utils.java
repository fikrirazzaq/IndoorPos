package com.juvetic.rssi.util.formulas;

import java.util.List;

final class Utils {

    /* Statistical support methods */

    static Double variance(List<Double> values) {
        Double sum = 0.0;
        Double mean = mean(values);
        for (double num : values) {
            sum += Math.pow(num - mean, 2);
        }
        return sum / (values.size());
    }

    static Double mean(List<Double> values) {
        return sum(values) / values.size();
    }

    private static Double sum(List<Double> values) {
        Double sum = 0.0;
        for (Double num : values) {
            sum += num;
        }
        return sum;
    }
}

