package com.raidtracker;

import lombok.Data;

/**
 * One of your Chambers of Xeric deaths. {@code room} is absent unless Challenge Mode or a full layout named it.
 */
@Data
public class ChambersDeath {
    String room;
    int pointsLost;
}
