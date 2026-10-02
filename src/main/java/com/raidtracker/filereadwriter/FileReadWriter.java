package com.raidtracker.filereadwriter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerItem;
import com.raidtracker.RaidType;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.RuneLite.RUNELITE_DIR;
import net.runelite.client.util.Text;

@Slf4j
public class FileReadWriter {

	public static final String LEAGUE_SUFFIX = "-league";

	@Getter
	private String username;
	@Getter
	private boolean league;
	private String coxDir;
	private String tobDir;
	private String toaDir;
    private String defaultDir;

	@Inject
    @Setter
	private Gson gson;

	// New Chambers lines write these keys only. Theatre and Tombs still write the whole object.
	// A later Chambers field has to be added here or it will not be stored.
	private static final String[] CHAMBERS_LOG_FIELDS = {
		"challengeMode",
		"FreeForAll",
		"upperTime",
		"middleTime",
		"lowerTime",
		"raidTime",
		"tektonTime",
		"crabsTime",
		"iceDemonTime",
		"shamansTime",
		"vanguardsTime",
		"thievingTime",
		"vespulaTime",
		"tightropeTime",
		"guardiansTime",
		"vasaTime",
		"mysticsTime",
		"muttadilesTime",
		"olmMageHandTimes",
		"olmPhaseTimes",
		"olmHeadTime",
		"deaths",
		"extraDrops",
		"uniques",
		"totalPoints",
		"personalPoints",
		"teamSize",
		"completionCount",
		"specialLoot",
		"specialLootReceiver",
		"specialLootInOwnName",
		"specialLootValue",
		"kitReceiver",
		"dustReceiver",
		"petReceiver",
		"petInMyName",
		"lootSplitReceived",
		"lootSplitPaid",
		"lootList",
		"uniqueID",
		"killCountID",
		"date"
	};

    public void writeToFile(RaidTracker raidTracker) {
        RaidType raidType = storedRaidType(raidTracker);
        String fileName = getRaidFileName(raidType);
		if (raidType == null) {
			log.warn("writeToFile called without an inRaid flag set.", new IllegalStateException());
		}

		JsonParser parser = new JsonParser();
		String line = getJSONString(raidTracker, gson, parser, raidType);
        try (FileWriter fw = new FileWriter(fileName, true)) {
			log.debug("writer started");
			fw.write(line);
			fw.write('\n');
        } catch (IOException ioe) {
			log.error("IOException: {} in writeToFile", ioe.getMessage());
		}
	}

	public String getJSONString(RaidTracker raidTracker, Gson gson, JsonParser parser) {
		return getJSONString(raidTracker, gson, parser, storedRaidType(raidTracker));
	}

    public String getJSONString(RaidTracker raidTracker, Gson gson, JsonParser parser, RaidType raidType) {
		if (raidTracker.getOlmMageHandTimes() == null) {
			raidTracker.setOlmMageHandTimes(new ArrayList<>());
		}
		if (raidTracker.getOlmPhaseTimes() == null) {
			raidTracker.setOlmPhaseTimes(new ArrayList<>());
		}
		if (raidTracker.getDeaths() == null) {
			raidTracker.setDeaths(new ArrayList<>());
		}
		if (raidTracker.getExtraDrops() == null) {
			raidTracker.setExtraDrops(new ArrayList<>());
		}
		if (raidTracker.getUniques() == null) {
			raidTracker.setUniques(new ArrayList<>());
		}

		JsonObject RTJson = parser.parse(gson.toJson(raidTracker)).getAsJsonObject();

		// The chest loot stays a JSON array. A string property here used to be unwrapped with a global replace.
		JsonArray lootJson = new JsonArray();
		List<RaidTrackerItem> lootList = raidTracker.getLootList();
		if (lootList != null) {
			for (RaidTrackerItem item : lootList) {
				lootJson.add(parser.parse(gson.toJson(item, new TypeToken<RaidTrackerItem>() {
				}.getType())));
			}
		}
		RTJson.add("lootList", lootJson);

		if (raidType == RaidType.COX) {
			JsonObject chambersJson = new JsonObject();
			for (String field : CHAMBERS_LOG_FIELDS) {
				if (includeChambersField(field, RTJson.get(field))) {
					chambersJson.add(field, RTJson.get(field));
				}
			}
			RTJson = chambersJson;
		}

        return gson.toJson(RTJson);
	}

	private boolean includeChambersField(String field, JsonElement value) {
		if (value == null || value.isJsonNull()) {
			return false;
		}
		if ("deaths".equals(field) || "uniques".equals(field) || "extraDrops".equals(field)) {
			return value.isJsonArray() && value.getAsJsonArray().size() > 0;
		}
		if (!value.isJsonPrimitive()) {
			return true;
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if ("specialLoot".equals(field) || "specialLootReceiver".equals(field)
			|| "kitReceiver".equals(field) || "dustReceiver".equals(field) || "petReceiver".equals(field)) {
			return primitive.isString() && !primitive.getAsString().isEmpty();
		}
		if ("specialLootInOwnName".equals(field) || "petInMyName".equals(field) || "FreeForAll".equals(field)) {
			return primitive.isBoolean() && primitive.getAsBoolean();
		}
		if ("specialLootValue".equals(field) || "lootSplitReceived".equals(field) || "lootSplitPaid".equals(field)) {
			return primitive.isNumber() && primitive.getAsInt() >= 0;
		}
		return true;
	}

	private RaidType storedRaidType(RaidTracker raidTracker) {
		if (raidTracker.isInTheatreOfBlood()) {
			return RaidType.TOB;
		}
		if (raidTracker.isInTombsOfAmascut()) {
			return RaidType.TOA;
		}
		if (raidTracker.isInRaidChambers()) {
			return RaidType.COX;
		}
		return null;
	}

	public void restoreRaidType(RaidTracker raidTracker, RaidType raidType) {
		if (raidType == RaidType.COX && raidTracker != null) {
			raidTracker.setInRaidChambers(true);
		}
	}

    public ArrayList<RaidTracker> readFromFile(String alternateFile, RaidType raidType) {
        String fileName = getRaidFileName(raidType);
        boolean foundReplacementUnicode = false;

		if (alternateFile.length() != 0) {
			fileName = alternateFile;
		}

		try {
			JsonParser parser = new JsonParser();
			BufferedReader bufferedreader = new BufferedReader(new FileReader(fileName));
			String line;
			ArrayList<RaidTracker> RTList = new ArrayList<>();

			while ((line = bufferedreader.readLine()) != null && line.length() > 0) {
                if (line.contains("\uFFFD")) {
                    foundReplacementUnicode = true;
                    line = line.replace("\uFFFD", " ");
                }

				try {
					RaidTracker parsed = gson.fromJson(parser.parse(line), RaidTracker.class);
					restoreRaidType(parsed, raidType);
					RTList.add(parsed);
				} catch (JsonSyntaxException e) {
					log.warn("Bad line: {}", line);
				}
			}

			bufferedreader.close();

            if (foundReplacementUnicode) {
                log.info("Found replacement unicode character while reading {} log: attempting to overwrite", Text.titleCase(raidType));
                updateRTList(RTList, raidType);
            }

			return RTList;
		} catch (IOException e) {
			log.error("Error occurred reading from file", e);
			return new ArrayList<>();
		}
	}

	public ArrayList<RaidTracker> readFromFile() {
		return readFromFile("", RaidType.COX);
	}

	public ArrayList<RaidTracker> readFromFile(RaidType raidType) {
		return readFromFile("", raidType);
	}

    public void createFolders() {
		File dir = new File(RUNELITE_DIR, "raid-data tracker");
		IGNORE_RESULT(dir.mkdir());
		dir = new File(dir, accountDirectoryName(username, league));
		IGNORE_RESULT(dir.mkdir());
		File dir_cox = new File(dir, "cox");
		File dir_tob = new File(dir, "tob");
		File dir_toa = new File(dir, "toa");
        File dir_default = new File(dir, "unknown");
		IGNORE_RESULT(dir_cox.mkdir());
		IGNORE_RESULT(dir_tob.mkdir());
		IGNORE_RESULT(dir_toa.mkdir());
        IGNORE_RESULT(dir_default.mkdir());
		this.coxDir = dir_cox.getAbsolutePath();
		this.tobDir = dir_tob.getAbsolutePath();
		this.toaDir = dir_toa.getAbsolutePath();
        this.defaultDir = dir_default.getAbsolutePath();

		try {
            IGNORE_RESULT(new File(getRaidFileName(RaidType.COX)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TOB)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(RaidType.TOA)).createNewFile());
            IGNORE_RESULT(new File(getRaidFileName(null)).createNewFile());
		} catch (IOException e) {
			log.error("Error occurred creating new files", e);
		}
	}

	public void updateUsername(final String username) {
		updateUsername(username, false);
	}

	public void updateUsername(final String username, final boolean league) {
		this.username = username;
		this.league = league;
		createFolders();
	}

	public static String accountDirectoryName(String username, boolean league) {
		if (username == null) {
			return null;
		}
		return league ? username + LEAGUE_SUFFIX : username;
	}

	// Used for making sure ToA loot and points is accurate
	// Initial write made on reward chest interface opened,
	// this updates after player leaves to account for purples/pets others receive
	public void updateRTLog(RaidTracker raidTracker, RaidType raidType) {
		try {
			JsonParser parser = new JsonParser();
            String fileName = getRaidFileName(raidType);
			ArrayList<RaidTracker> RTList = readFromFile(raidType);
			StringBuilder body = new StringBuilder();

			for (RaidTracker RT : RTList) {
				if (RT.getUniqueID().equals(raidTracker.getUniqueID()) && !RT.equals(raidTracker)) {
					log.debug("writer updated log");
					RT = raidTracker;
				}
				body.append(getJSONString(RT, gson, parser, raidType));
				body.append('\n');
			}

			try (FileWriter fw = new FileWriter(fileName, false)) {
				fw.write(body.toString());
			}
		} catch (IOException e) {
			log.error("Error occurred updating the log", e);
		}
	}

	public void updateRTList(ArrayList<RaidTracker> RTList, RaidType raidType) {
		try {
			JsonParser parser = new JsonParser();
            String fileName = getRaidFileName(raidType);
			StringBuilder body = new StringBuilder();

			for (RaidTracker RT : RTList) {
				if (RT.getLootSplitPaid() > 0) {
					RT.setSpecialLootInOwnName(true);
                } else {
					// bit of a wonky check, so try to avoid with lootsplitpaid if possible
                    String specialLoot = RT.getSpecialLoot() == null ? "" : RT.getSpecialLoot();
                    RT.setSpecialLootInOwnName(RT.getLootList() != null && !RT.getLootList().isEmpty()
                            && RT.getLootList().get(0).getName().equalsIgnoreCase(specialLoot));
				}

				body.append(getJSONString(RT, gson, parser, raidType));
				body.append('\n');
			}

			try (FileWriter fw = new FileWriter(fileName, false)) {
				fw.write(body.toString());
			}
		} catch (IOException e) {
			log.error("Error occurred updating the log list", e);
		}
	}

    public RaidTracker getUnclaimedRewardsRT(long accountHash, RaidType raidType) {
        ArrayList<RaidTracker> logs = readFromFile(raidType);

        logs = logs.stream().filter(
            RT -> RT.getAccountHash() == accountHash
        ).collect(Collectors.toCollection(ArrayList::new));

        if (logs.isEmpty()) return null;

        RaidTracker lastLog = logs.get(logs.size() - 1);

        return lastLog.isChestOpened() ? null : lastLog;
    }

	public boolean delete(RaidType raidType) {
        File newFile = new File(getRaidFileName(raidType));

		boolean isDeleted = newFile.delete();

		try {
			IGNORE_RESULT(newFile.createNewFile());
		} catch (IOException e) {
			log.error("Error occurred creating new file", e);
		}

		return isDeleted;
	}

    public void IGNORE_RESULT(boolean b) {
    }

	/** The Chambers folder for the current login, or null before that folder exists. */
	public String getCoxDirectory() {
		return coxDir;
	}

    public String getRaidFileName(RaidType raidType) {
        final String DATA_FILE_NAME = "/raid_tracker_data.log";

        if (raidType == null) {
            return defaultDir + DATA_FILE_NAME;
        }

		switch(raidType) {
            case COX:
                return coxDir + DATA_FILE_NAME;
			case TOB:
                return tobDir + DATA_FILE_NAME;
			case TOA:
                return toaDir + DATA_FILE_NAME;
            default:
                return defaultDir + DATA_FILE_NAME;
		}
	}
}
