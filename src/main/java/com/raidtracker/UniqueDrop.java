package com.raidtracker;

import lombok.Data;

/**
 * One Chambers unique on a raid: a purple, twisted kit, metamorphic dust, or pet.
 * Split fields are set for a purple. They stay unset for a kit, dust, or pet.
 */
@Data
public class UniqueDrop {
	String item = "";
	String receiver = "";
	boolean own;
	Integer value;
	Boolean freeForAll;
	Integer splitReceived;
	Integer splitPaid;
}
