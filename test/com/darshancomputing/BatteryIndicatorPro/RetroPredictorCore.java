/*
    Copyright (c) 2012-2013 Darshan-Josiah Barber

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
*/

package com.stylovid.fastbattery;

import java.util.LinkedList;

public class RetroPredictorCore {
    private static final int DISCHARGE    = 0;
    private static final int RECHARGE_AC  = 1;
    private static final int RECHARGE_WL  = 2;
    private static final int RECHARGE_USB = 3;

    private double[] average = new double[4];

    private static final int[] DEFAULT = { 24 * 60 * 60 * 1000 / 100,
                                           3 * 60 * 60 * 1000 / 100,
                                           4 * 60 * 60 * 1000 / 100,
                                           6 * 60 * 60 * 1000 / 100 };

    private static final int RECENT_DURATION = 5 * 60 * 1000;

    private static final double WEIGHT_OLD_AVERAGE = 0.998;
    private static final double WEIGHT_NEW_DATA =  1 - WEIGHT_OLD_AVERAGE;

    private LinkedList<Double> recents;

    private long last_ms;
    private int last_level;
    private int last_status;
    private int last_plugged;
    private int last_index;
    private double recent_average;
    private boolean partial;
    private boolean initial;

    private long now;

    public RetroPredictorCore(float ave_discharge, float ave_recharge_ac, float ave_recharge_wl, float ave_recharge_usb) {
        average[DISCHARGE]    = ave_discharge    == -1 ? DEFAULT[DISCHARGE]    : ave_discharge;
        average[RECHARGE_AC]  = ave_recharge_ac  == -1 ? DEFAULT[RECHARGE_AC]  : ave_recharge_ac;
        average[RECHARGE_WL]  = ave_recharge_wl  == -1 ? DEFAULT[RECHARGE_WL]  : ave_recharge_wl;
        average[RECHARGE_USB] = ave_recharge_usb == -1 ? DEFAULT[RECHARGE_USB] : ave_recharge_usb;

        recents = new LinkedList<Double>();
    }

    public void update(BatteryInfo info, long when) {
        now = when;

        if (info.status != last_status || info.plugged != last_plugged || last_ms == 0 || info.status == BatteryInfo.STATUS_FULLY_CHARGED) {
            recents.clear();
            partial = false;
            initial = true;
            setLasts(info);
            updateInfoPrediction(info);
            return;
        }

        if ((info.status == BatteryInfo.STATUS_CHARGING && info.percent < last_level) ||
            (info.status != BatteryInfo.STATUS_CHARGING && info.percent > last_level))
        {
            // There may be better ways to account for this backslide, but the simplest solution is to just ignore it
            return;
        }

        int status = indexFor(info.status, info.plugged);
        int level_diff = java.lang.Math.abs(last_level - info.percent);
        double ms_diff = (double) (now - last_ms);

        if (level_diff == 0) {
            if (ms_diff <= recent_average)
                return;

            if (partial) {
                recents.set(0, ms_diff);
            } else {
                recents.addFirst(ms_diff);
                partial = true;
            }
        } else {
            if (partial) {
                recents.remove(0);
                partial = false;
            }

            ms_diff /= level_diff;

            if (initial && ms_diff < average[status]) { // Non-useful initial level change after status change
                initial = false;
                setLasts(info);
                return;
            }

            initial = false;

            for (int i = 0; i < level_diff; i++) {
                recents.addFirst(ms_diff);

                average[status] = average[status] * WEIGHT_OLD_AVERAGE + ms_diff * WEIGHT_NEW_DATA;
            }

            setLasts(info);
        }

        updateInfoPrediction(info);
    }

    private void updateInfoPrediction(BatteryInfo info) {
        int secs_left;

        if (info.status == BatteryInfo.STATUS_CHARGING)
            secs_left = secondsUntilCharged();
        else
            secs_left = secondsUntilDrained();

        info.prediction.update(secs_left);
    }

    public int secondsUntilDrained() {
        if (last_status != BatteryInfo.STATUS_UNPLUGGED) {
            return -1;
        }

        double predicted = recentAverage();

        return (int) (predicted * last_level / 1000);
    }

    public int secondsUntilCharged() {
        if (last_status == BatteryInfo.STATUS_FULLY_CHARGED) {
            return 0;
        }

        if (last_status != BatteryInfo.STATUS_CHARGING) {
            return -1;
        }

        return (int) ((100 - last_level) * recentAverage() / 1000);
    }

    private void setLasts(BatteryInfo info) {
        last_level = info.percent;
        last_status = info.status;
        last_plugged = info.plugged;
        last_index = indexFor(last_status, last_plugged);
        last_ms = now;
    }

    private double recentAverage() {
        double total_points = 0d;
        double total_ms = 0d;
        double needed_ms = RECENT_DURATION;

        int i;
        for (i = 0; i < recents.size(); i++) {
            double t = recents.get(i);

            if (t > needed_ms) {
                total_points += needed_ms / t;
                total_ms += needed_ms;
                needed_ms = 0;

                i++;
                while (recents.size() > i) // This is a convenient place to trim recents
                    recents.remove(i);

                break;
            }

            total_points += 1;
            total_ms += t;
            needed_ms -= t;
        }

        if (needed_ms > 0)
            total_points += needed_ms / average[last_index];

        recent_average = RECENT_DURATION / total_points;
        return recent_average;
    }

    private int indexFor(int status, int plugged) {
        if (status == BatteryInfo.STATUS_CHARGING) {
            if (plugged == BatteryInfo.PLUGGED_USB)
                return RECHARGE_USB;
            else if (plugged == BatteryInfo.PLUGGED_WIRELESS)
                return RECHARGE_WL;
            else
                return RECHARGE_AC;
        } else {
            return DISCHARGE;
        }
    }
}
