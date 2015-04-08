/*
 * Copyright (c) 2015 Tarek Auel
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package de.dhbw.mannheim.assemblylinesim.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

/**
 * @author Tarek Auel
 * @since 08.04.2015
 */
public class Report {

    private final Timestamp startTime;

    private final ArrayList<Timestamp> passedLightBarrier = new ArrayList<>();

    private double speedShaperRPM;
    private double speedDrillerRPM;

    public Report() {
        startTime = new Timestamp(new Date().getTime());
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public ArrayList<Timestamp> getPassedLightBarrier() {
        return passedLightBarrier;
    }

    public void passedLightBarrier() {
        passedLightBarrier.add(new Timestamp(new Date().getTime()));
    }

    public double getSpeedShaperRPM() {
        return speedShaperRPM;
    }

    public void setSpeedShaperRPM(double speedShaperRPM) {
        this.speedShaperRPM = speedShaperRPM;
    }

    public double getSpeedDrillerRPM() {
        return speedDrillerRPM;
    }

    public void setSpeedDrillerRPM(double speedDrillerRPM) {
        this.speedDrillerRPM = speedDrillerRPM;
    }
}
