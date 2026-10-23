package com.raidtracker;

import com.raidtracker.ui.chambers.ChambersFacts;
import com.raidtracker.ui.chambers.ChambersFilter;
import com.raidtracker.ui.chambers.ChambersPurples;
import com.raidtracker.ui.chambers.ChambersTargets;
import com.raidtracker.ui.chambers.ChambersTimes;
import com.raidtracker.ui.chambers.ChambersTimesView;
import com.raidtracker.ui.chambers.TargetTimeSettings;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ChambersViewTest {
	@Test
	public void olmIsTheFinishAfterTheLowerFloor() {
		RaidTracker raid = raid();
		raid.setLowerTime(500);
		raid.setRaidTime(800);
		Assert.assertEquals(300, ChambersFacts.olmSeconds(raid));
		raid.setLowerTime(-1);
		Assert.assertEquals(-1, ChambersFacts.olmSeconds(raid));
	}

	@Test
	public void preOlmAddsOnlyRoomsThatHaveATime() {
		RaidTracker raid = raid();
		raid.setTektonTime(60);
		raid.setGuardiansTime(0);
		raid.setMuttadilesTime(40);
		Assert.assertEquals(100, ChambersFacts.preOlmSeconds(raid));
		raid.setLowerTime(200);
		raid.setRaidTime(340);
		Assert.assertEquals(140, ChambersFacts.olmSeconds(raid));
		Assert.assertEquals(100, ChambersFacts.betweenSeconds(raid));
	}

	@Test
	public void betweenRoomsIsReadyWhenTheLowerFloorCompletes() {
		RaidTracker raid = raid();
		raid.setTektonTime(60);
		raid.setMuttadilesTime(40);
		raid.setLowerTime(200);
		Assert.assertEquals(100, ChambersFacts.betweenSeconds(raid));
		raid.setRaidTime(340);
		Assert.assertEquals(100, ChambersFacts.betweenSeconds(raid));
		raid.setRaidTime(150);
		Assert.assertEquals(-1, ChambersFacts.betweenSeconds(raid));
	}

	@Test
	public void aDeathMarksThatRoomOnOneRaidAndNotOnBestSplits() {
		RaidTracker raid = raid();
		raid.setChallengeMode(true);
		raid.setTeamSize(1);
		raid.setTektonTime(80);
		raid.setLowerTime(200);
		raid.setRaidTime(400);
		raid.setPersonalPoints(30000);
		ChambersDeath death = new ChambersDeath();
		death.setRoom("Tekton");
		death.setPointsLost(1000);
		raid.setDeaths(new ArrayList<>());
		raid.getDeaths().add(death);
		ChambersTimes.Table average = ChambersTimes.average(Arrays.asList(raid), null, -1);
		Assert.assertTrue(row(average, "Tekton").died);
		Assert.assertFalse(row(average, "Raid time").died);
		ChambersTimes.Table fastest = ChambersTimes.bestRaid(Arrays.asList(raid));
		Assert.assertTrue(row(fastest, "Tekton").died);
		ChambersTimes.Table splits = ChambersTimes.bestSplits(Arrays.asList(raid));
		Assert.assertFalse(row(splits, "Tekton").died);
		RaidTrackerConfig config = new RaidTrackerConfig() {};
		ChambersTimes.Table target = ChambersTargets.compare(
			Arrays.asList(raid), null, -1, config, ChambersFilter.ALL, ChambersFilter.ALL, false, 10);
		Assert.assertTrue(row(target, "Tekton").died);
		ChambersTimes.Table last = ChambersTargets.compare(
			Arrays.asList(raid), null, -1, config, ChambersFilter.ALL, ChambersFilter.ALL, true, 10);
		Assert.assertFalse(row(last, "Tekton").died);
		RaidTracker copy = ChambersFacts.copyTimes(raid);
		Assert.assertEquals("Tekton", copy.getDeaths().get(0).getRoom());
		death.setRoom(null);
		Assert.assertFalse(ChambersFacts.diedInRoom(raid, "Tekton"));
	}

	@Test
	public void regularFullIsElevenPrepRoomsAndChallengeModeStaysCm() {
		RaidTracker full = raid();
		for (int i = 0; i < 11; i++) {
			setRoom(full, ChambersFacts.PREP_ROOMS[i], 60);
		}
		full.setTeamSize(1);
		RaidTracker shortRaid = raid();
		shortRaid.setTektonTime(60);
		shortRaid.setTeamSize(1);
		RaidTracker cm = raid();
		cm.setChallengeMode(true);
		cm.setTektonTime(60);
		cm.setTeamSize(3);
		Assert.assertTrue(ChambersFilter.matches(full, ChambersFilter.REGULAR_FULL, ChambersFilter.SOLO));
		Assert.assertFalse(ChambersFilter.matches(full, ChambersFilter.REGULAR, ChambersFilter.ALL));
		Assert.assertTrue(ChambersFilter.matches(shortRaid, ChambersFilter.REGULAR, ChambersFilter.SOLO));
		Assert.assertTrue(ChambersFilter.matches(cm, ChambersFilter.CM, "3"));
		Assert.assertFalse(ChambersFilter.matches(cm, ChambersFilter.REGULAR_FULL, ChambersFilter.ALL));
		Assert.assertFalse(ChambersFilter.matches(shortRaid, ChambersFilter.ALL, ChambersFilter.TEAM));
	}

	@Test
	public void averageAndBestIgnoreAShortSplit() {
		RaidTracker slow = raid();
		slow.setTektonTime(80);
		slow.setCompletionCount(2);
		RaidTracker glitch = raid();
		glitch.setTektonTime(5);
		glitch.setCompletionCount(3);
		List<RaidTracker> raids = Arrays.asList(slow, glitch);
		ChambersTimes.Table best = ChambersTimes.bestSplits(raids);
		Assert.assertEquals("1:20", row(best, "Tekton").value);
		Assert.assertEquals("KC 2", row(best, "Tekton").aside);
		ChambersTimes.Table average = ChambersTimes.average(raids, null, -1);
		Assert.assertEquals("0:05", row(average, "Tekton").value);
		Assert.assertEquals(Integer.valueOf(5 - 80), row(average, "Tekton").delta);
	}

	@Test
	public void iceOverThreeFiftyStaysOutUnlessIceMilkIsOn() {
		RaidTracker normal = raid();
		normal.setIceDemonTime(120);
		normal.setCompletionCount(1);
		RaidTracker milking = raid();
		milking.setIceDemonTime(240);
		milking.setCompletionCount(2);
		List<RaidTracker> raids = Arrays.asList(normal, milking);

		ChambersTimes.Table off = ChambersTimes.average(raids, null, -1);
		Assert.assertEquals("4:00", row(off, "Ice demon").value);
		Assert.assertEquals(Integer.valueOf(240 - 120), row(off, "Ice demon").delta);
		Assert.assertEquals("2:00", row(ChambersTimes.bestSplits(raids), "Ice demon").value);

		ChambersTimes.Table on = ChambersTimes.average(raids, null, -1, true);
		Assert.assertEquals("4:00", row(on, "Ice demon").value);
		Assert.assertEquals(Integer.valueOf(240 - 180), row(on, "Ice demon").delta);
		Assert.assertEquals("2:00", row(ChambersTimes.bestSplits(raids, true), "Ice demon").value);
		Assert.assertNull(rowOrNull(ChambersTimes.bestSplits(Arrays.asList(milking)), "Ice demon"));
		Assert.assertEquals("4:00", row(ChambersTimes.bestSplits(Arrays.asList(milking), true), "Ice demon").value);

		RaidTracker atNormalMax = raid();
		atNormalMax.setIceDemonTime(230);
		ChambersTimes.Table kept = ChambersTimes.average(Arrays.asList(normal, atNormalMax), null, -1);
		Assert.assertEquals(Integer.valueOf(230 - 175), row(kept, "Ice demon").delta);

		RaidTracker justOver = raid();
		justOver.setIceDemonTime(231);
		ChambersTimes.Table justOverOff = ChambersTimes.average(Arrays.asList(normal, justOver), null, -1);
		Assert.assertEquals(Integer.valueOf(231 - 120), row(justOverOff, "Ice demon").delta);
		ChambersTimes.Table justOverOn = ChambersTimes.average(Arrays.asList(normal, justOver), null, -1, true);
		Assert.assertEquals(Integer.valueOf(231 - 176), row(justOverOn, "Ice demon").delta);

		RaidTracker atMilkMax = raid();
		atMilkMax.setIceDemonTime(270);
		ChambersTimes.Table milkMax = ChambersTimes.average(Arrays.asList(normal, atMilkMax), null, -1, true);
		Assert.assertEquals(Integer.valueOf(270 - 195), row(milkMax, "Ice demon").delta);

		RaidTracker tooLong = raid();
		tooLong.setIceDemonTime(271);
		ChambersTimes.Table stillOut = ChambersTimes.average(Arrays.asList(normal, tooLong), null, -1, true);
		Assert.assertEquals("4:31", row(stillOut, "Ice demon").value);
		Assert.assertEquals(Integer.valueOf(271 - 120), row(stillOut, "Ice demon").delta);

		RaidTrackerConfig milk = new RaidTrackerConfig() {
			@Override
			public boolean iceMilking() {
				return true;
			}
		};
		ChambersTimes.Table last = ChambersTargets.compare(raids, null, -1, milk, ChambersFilter.ALL, ChambersFilter.ALL, true, 10);
		Assert.assertEquals("3:00", row(last, "Ice demon").value);
		ChambersTimes.Table lastOff = ChambersTargets.compare(raids, null, -1, new RaidTrackerConfig() {}, ChambersFilter.ALL, ChambersFilter.ALL, true, 10);
		Assert.assertEquals("2:00", row(lastOff, "Ice demon").value);
	}

	@Test
	public void ninthOlmPhaseIsNotShown() {
		RaidTracker raid = raid();
		ArrayList<Integer> phases = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			phases.add(50);
		}
		raid.setOlmPhaseTimes(phases);
		raid.setRaidTime(600);
		List<String> labels = new ArrayList<>();
		ChambersTimes.Table table = ChambersTimes.average(Arrays.asList(raid), null, -1);
		for (int i = 0; i < table.rows.size(); i++) {
			labels.add(table.rows.get(i).label);
		}
		Assert.assertTrue(labels.contains("Olm P8"));
		Assert.assertFalse(labels.contains("Olm P9"));
	}

	@Test
	public void targetTimesApplyOnEveryFilter() {
		RaidTrackerConfig config = new RaidTrackerConfig() {};
		RaidTracker raid = cmSoloOnTarget();
		ChambersTimes.Table table = ChambersTargets.compare(Arrays.asList(raid), null, -1, config, ChambersFilter.CM, ChambersFilter.SOLO, false, 10);
		Assert.assertEquals(Integer.valueOf(0), row(table, "Tekton").delta);
		Assert.assertEquals(Integer.valueOf(0), row(table, "Pre-Olm").delta);
		Assert.assertEquals(Integer.valueOf(0), row(table, "Olm").delta);
		Assert.assertEquals(Integer.valueOf(0), row(table, "Raid time").delta);
		Assert.assertEquals(Integer.valueOf(0), row(table, "Mage P1").delta);
		Assert.assertNull(row(table, "Mage P3").delta);
		Assert.assertEquals(0, mark(table, "Start"));
		Assert.assertEquals(0, mark(table, "Tekton"));
		Assert.assertNull(table.personalBestGap);
		Assert.assertEquals("target", table.paceAgainst);
		Assert.assertEquals("25:35", ChambersTargets.settingsTotal(config));
		Assert.assertEquals("138685", ChambersTargets.settingsPph(config));

		ChambersTimes.Table anyFilter = ChambersTargets.compare(Arrays.asList(raid), null, -1, config, ChambersFilter.ALL, ChambersFilter.ALL, false, 10);
		Assert.assertEquals(Integer.valueOf(0), row(anyFilter, "Tekton").delta);
		ChambersTimes.Table regular = ChambersTargets.compare(Arrays.asList(raid), null, -1, config, ChambersFilter.REGULAR_FULL, ChambersFilter.TEAM, false, 10);
		Assert.assertEquals(Integer.valueOf(0), row(regular, "Raid time").delta);
	}

	@Test
	public void targetGraphDrawsThePersonalBestFromTheComparison() {
		RaidTrackerConfig config = new RaidTrackerConfig() {};
		RaidTracker earlier = cmSoloOnTarget();
		earlier.setRaidTime(1400);
		RaidTracker latest = cmSoloOnTarget();
		ChambersTimes.Table shown = ChambersTargets.compare(Arrays.asList(earlier, latest), null, -1, config, ChambersFilter.CM, ChambersFilter.SOLO, false, 10);
		Assert.assertEquals(Integer.valueOf(1535 - 1400), shown.personalBestGap);
		Assert.assertEquals(0, mark(shown, "Finish"));

		ChambersTimes.Table live = ChambersTargets.compare(Arrays.asList(earlier), latest, 30, config, ChambersFilter.ALL, ChambersFilter.ALL, false, 10);
		Assert.assertEquals(Integer.valueOf(1535 - 1400), live.personalBestGap);

		ChambersTimes.Table last = ChambersTargets.compare(Arrays.asList(earlier, latest), null, -1, config, ChambersFilter.ALL, ChambersFilter.ALL, true, 10);
		Assert.assertEquals(Integer.valueOf(1535 - 1400), last.personalBestGap);

		ChambersTimes.Table average = ChambersTargets.averageVersusTarget(Arrays.asList(earlier, latest), config);
		Assert.assertEquals(Integer.valueOf(1535 - 1400), average.personalBestGap);

		RaidTracker tooShort = cmSoloOnTarget();
		tooShort.setRaidTime(5);
		ChambersTimes.Table ignored = ChambersTargets.compare(Arrays.asList(tooShort, latest), null, -1, config, ChambersFilter.CM, ChambersFilter.SOLO, false, 10);
		Assert.assertNull(ignored.personalBestGap);

		RaidTracker slower = cmSoloOnTarget();
		slower.setRaidTime(1600);
		ChambersTimes.Table behind = ChambersTargets.compare(Arrays.asList(slower, latest), null, -1, config, ChambersFilter.CM, ChambersFilter.SOLO, false, 10);
		Assert.assertEquals(Integer.valueOf(1535 - 1600), behind.personalBestGap);
	}

	@Test
	public void personalBestLineCanBeTurnedOff() {
		Assert.assertEquals(Integer.valueOf(130), ChambersTimesView.paceGap(new RaidTrackerConfig() {}, 130));
		Assert.assertNull(ChambersTimesView.paceGap(new RaidTrackerConfig() {
			@Override
			public boolean showPersonalBest() {
				return false;
			}
		}, 130));
	}

	@Test
	public void targetAddsApplyOnlyWhenTheRoomHasATime() {
		RaidTrackerConfig slower = new RaidTrackerConfig() {
			@Override
			public boolean crabTank() {
				return false;
			}

			@Override
			public boolean useTbow() {
				return false;
			}

			@Override
			public boolean iceMilking() {
				return true;
			}
		};
		RaidTracker raid = raid();
		raid.setChallengeMode(true);
		raid.setTeamSize(1);
		raid.setCrabsTime(56);
		raid.setShamansTime(63);
		raid.setIceDemonTime(144);
		ChambersTimes.Table table = ChambersTargets.compare(Arrays.asList(raid), null, -1, slower, ChambersFilter.CM, ChambersFilter.SOLO, false, 10);
		Assert.assertEquals(Integer.valueOf(56 - 60), row(table, "Crabs").delta);
		Assert.assertEquals(Integer.valueOf(63 - 69), row(table, "Shamans").delta);
		Assert.assertEquals(Integer.valueOf(144 - 214), row(table, "Ice demon").delta);

		RaidTrackerConfig blankShamans = new RaidTrackerConfig() {
			@Override
			public boolean useTbow() {
				return false;
			}

			@Override
			public String cmShamans() {
				return "";
			}
		};
		RaidTracker regular = raid();
		regular.setTeamSize(1);
		regular.setShamansTime(63);
		ChambersTimes.Table blank = ChambersTargets.compare(Arrays.asList(regular), null, -1, blankShamans, ChambersFilter.ALL, ChambersFilter.SOLO, false, 10);
		Assert.assertNull(row(blank, "Shamans").delta);
		Assert.assertEquals("+ 0:04", ChambersTargets.addNote(slower, "Crabs"));
		Assert.assertEquals("+ 0:06", ChambersTargets.addNote(slower, "Shamans"));
		Assert.assertEquals("+ 1:10", ChambersTargets.addNote(slower, "Ice demon"));
		Assert.assertEquals("", ChambersTargets.addNote(blankShamans, "Shamans"));
		Assert.assertEquals("+ 0:04", ChambersTargets.addNote(new RaidTrackerConfig() {
			@Override
			public boolean overThieve() {
				return true;
			}
		}, "Thieving"));
		RaidTrackerConfig crabOff = new RaidTrackerConfig() {
			@Override
			public boolean crabTank() {
				return false;
			}
		};
		Assert.assertEquals("25:39", ChambersTargets.settingsTotal(crabOff));
		Assert.assertEquals("138325", ChambersTargets.settingsPph(crabOff));
		Assert.assertEquals("+ 0:04", ChambersTargets.addNote(crabOff, "Target total"));
	}

	@Test
	public void moneyRunPointsComeBackWhenThoseThreeAreOn() {
		RaidTracker raid = raid();
		raid.setTeamSize(1);
		raid.setVespulaTime(59);
		raid.setPersonalPoints(59134);
		ChambersTimes.Table plain = ChambersTargets.compare(Arrays.asList(raid), null, -1, new RaidTrackerConfig() {}, ChambersFilter.ALL, ChambersFilter.SOLO, false, 10);
		Assert.assertEquals(Integer.valueOf(0), row(plain, "Vespula").delta);
		Assert.assertEquals(Integer.valueOf(0), row(plain, "Total points").delta);
		Assert.assertEquals(0, ChambersTargets.addPoints(new RaidTrackerConfig() {}, "Total Points"));

		RaidTrackerConfig milk = new RaidTrackerConfig() {
			@Override
			public boolean milkVespula() {
				return true;
			}
		};
		raid.setPersonalPoints(60134);
		ChambersTimes.Table milked = ChambersTargets.compare(Arrays.asList(raid), null, -1, milk, ChambersFilter.ALL, ChambersFilter.SOLO, false, 10);
		Assert.assertEquals(Integer.valueOf(59 - 74), row(milked, "Vespula").delta);
		Assert.assertEquals(Integer.valueOf(0), row(milked, "Total points").delta);
		Assert.assertEquals(1000, ChambersTargets.addPoints(milk, "Total Points"));

		RaidTrackerConfig all = new RaidTrackerConfig() {
			@Override
			public boolean milkVespula() {
				return true;
			}

			@Override
			public boolean killRope() {
				return true;
			}

			@Override
			public boolean overThieve() {
				return true;
			}
		};
		raid.setPersonalPoints(64100);
		ChambersTimes.Table checked = ChambersTargets.compare(Arrays.asList(raid), null, -1, all, ChambersFilter.ALL, ChambersFilter.SOLO, false, 10);
		Assert.assertEquals(Integer.valueOf(0), row(checked, "Total points").delta);
		Assert.assertEquals(4966, ChambersTargets.addPoints(all, "Total Points"));
		Assert.assertEquals("26:53 (25:35 + 1:18)", ChambersTargets.settingsTotalLine(all));
		Assert.assertEquals("64100 (59134 + 4966)", ChambersTargets.settingsPointsLine(all));
		Assert.assertEquals("25:35", ChambersTargets.settingsTotalLine(new RaidTrackerConfig() {}));
		Assert.assertEquals("59134", ChambersTargets.settingsPointsLine(new RaidTrackerConfig() {}));

		RaidTrackerConfig blank = new RaidTrackerConfig() {
			@Override
			public boolean milkVespula() {
				return true;
			}

			@Override
			public String cmTotalPoints() {
				return "";
			}
		};
		Assert.assertEquals(0, ChambersTargets.addPoints(blank, "Total Points"));
	}

	@Test
	public void lastNAveragesTheWindowAndKeepsAThreePhaseOlmWhenSizesMix() {
		RaidTrackerConfig config = new RaidTrackerConfig() {};
		RaidTracker solo = raid();
		solo.setChallengeMode(true);
		solo.setTeamSize(1);
		solo.setTektonTime(80);
		solo.setLowerTime(100);
		solo.setRaidTime(568);
		RaidTracker team = raid();
		team.setChallengeMode(true);
		team.setTeamSize(8);
		team.setTektonTime(5);
		team.setLowerTime(100);
		team.setRaidTime(568);
		ChambersTimes.Table mixed = ChambersTargets.compare(Arrays.asList(solo, team), null, -1, config, ChambersFilter.CM, ChambersFilter.TEAM, true, 10);
		Assert.assertEquals(Integer.valueOf(0), row(mixed, "Olm").delta);
		Assert.assertEquals("1:20", row(mixed, "Tekton").value);
		Assert.assertEquals(Integer.valueOf(10), row(mixed, "Tekton").delta);

		RaidTracker earlier = raid();
		earlier.setChallengeMode(true);
		earlier.setTeamSize(1);
		earlier.setTektonTime(60);
		RaidTracker latest = raid();
		latest.setChallengeMode(true);
		latest.setTeamSize(1);
		latest.setTektonTime(80);
		ChambersTimes.Table lastTwo = ChambersTargets.compare(Arrays.asList(earlier, latest), null, -1, config, ChambersFilter.CM, ChambersFilter.SOLO, true, 2);
		Assert.assertEquals("1:10", row(lastTwo, "Tekton").value);
		Assert.assertEquals(Integer.valueOf(0), row(lastTwo, "Tekton").delta);
	}

	@Test
	public void averageVersusTargetShowsTheAverageAndHowFarItSitsFromTheTarget() {
		RaidTrackerConfig config = new RaidTrackerConfig() {};
		RaidTracker slower = raid();
		slower.setChallengeMode(true);
		slower.setTeamSize(1);
		slower.setTektonTime(90);
		RaidTracker faster = raid();
		faster.setChallengeMode(true);
		faster.setTeamSize(1);
		faster.setTektonTime(50);
		RaidTracker tooShort = raid();
		tooShort.setChallengeMode(true);
		tooShort.setTeamSize(1);
		tooShort.setTektonTime(5);
		ChambersTimes.Table even = ChambersTargets.averageVersusTarget(Arrays.asList(slower, faster, tooShort), config);
		Assert.assertEquals("1:10", row(even, "Tekton").value);
		Assert.assertEquals("0:00", row(even, "Tekton").aside);
		Assert.assertEquals(Integer.valueOf(0), row(even, "Tekton").delta);

		RaidTracker behind = raid();
		behind.setChallengeMode(true);
		behind.setTeamSize(1);
		behind.setTektonTime(100);
		ChambersTimes.Table late = ChambersTargets.averageVersusTarget(Arrays.asList(behind, slower), config);
		Assert.assertEquals("1:35", row(late, "Tekton").value);
		Assert.assertEquals("+0:25", row(late, "Tekton").aside);
		Assert.assertEquals(Integer.valueOf(25), row(late, "Tekton").delta);
		Assert.assertEquals(-25, mark(late, "Tekton"));
	}

	@Test
	public void aTeamOfEightUsesFourOlmPhases() {
		RaidTracker raid = raid();
		raid.setChallengeMode(true);
		raid.setTeamSize(8);
		raid.setLowerTime(100);
		raid.setRaidTime(568);
		ChambersTimes.Table table = ChambersTargets.compare(Arrays.asList(raid), null, -1, new RaidTrackerConfig() {}, ChambersFilter.ALL, "8", false, 10);
		Assert.assertEquals(Integer.valueOf(468 - 612), row(table, "Olm").delta);
	}

	@Test
	public void targetTotalIsGrayWhenTheSectionJoinsThePage() {
		JPanel page = new JPanel();
		page.add(new JLabel("Raid Data Tracker"));
		JPanel main = new JPanel();
		page.add(main);
		JPanel section = new JPanel();
		JPanel header = new JPanel();
		header.add(new JLabel("Target times"));
		JPanel body = new JPanel();
		JPanel totalRow = new JPanel();
		totalRow.add(new JLabel("Target total"));
		JTextArea total = new JTextArea("25:30");
		total.setEditable(true);
		totalRow.add(total);
		body.add(totalRow);
		JPanel pphRow = new JPanel();
		pphRow.add(new JLabel("PPH"));
		JTextArea pph = new JTextArea("130000");
		pph.setEditable(true);
		pphRow.add(pph);
		body.add(pphRow);
		JPanel tektonRow = new JPanel();
		tektonRow.add(new JLabel("Tekton"));
		tektonRow.add(new JTextArea("2:00"));
		body.add(tektonRow);
		section.add(header);
		section.add(body);
		TargetTimeSettings settings = new TargetTimeSettings(new RaidTrackerConfig() {}, null);
		settings.notice(total);
		Assert.assertTrue(total.isEditable());
		main.add(section);
		settings.notice(section);
		Assert.assertFalse(total.isEditable());
		Assert.assertEquals("25:35", total.getText());
		Assert.assertFalse(pph.isEditable());
		Assert.assertEquals("138685", pph.getText());
		Assert.assertTrue(body.getComponent(0) instanceof JButton);
		Assert.assertEquals("Reset to default", ((JButton) body.getComponent(0)).getText());
	}

	@Test
	public void resetStaysOffTheSidebarWhenARoomNoteIsColored() {
		JPanel shell = new JPanel();
		JPanel tracker = new JPanel();
		JLabel trackerRoom = new JLabel("Tekton");
		tracker.add(trackerRoom);
		shell.add(tracker);

		JPanel config = new JPanel();
		config.add(new JLabel("Raid Data Tracker"));
		JPanel section = new JPanel();
		JPanel header = new JPanel();
		header.add(new JLabel("Target times"));
		JPanel body = new JPanel();
		JPanel totalRow = new JPanel();
		totalRow.add(new JLabel("Target total"));
		totalRow.add(new JTextArea("25:30"));
		body.add(totalRow);
		JPanel tektonRow = new JPanel();
		tektonRow.add(new JLabel("Tekton"));
		tektonRow.add(new JTextArea("1:10"));
		body.add(tektonRow);
		section.add(header);
		section.add(body);
		config.add(section);
		shell.add(config);

		TargetTimeSettings settings = new TargetTimeSettings(new RaidTrackerConfig() {
			@Override
			public boolean preVeng() {
				return false;
			}
		}, null);
		settings.notice(section);
		settings.notice(trackerRoom);

		Assert.assertEquals(1, tracker.getComponentCount());
		Assert.assertTrue(body.getComponent(0) instanceof JButton);
	}

	@Test
	public void fasterThanTheOtherRaidIsAheadOnThePaceLine() {
		RaidTracker earlier = raid();
		earlier.setTektonTime(100);
		earlier.setLowerTime(500);
		earlier.setRaidTime(800);
		RaidTracker latest = raid();
		latest.setTektonTime(80);
		latest.setLowerTime(480);
		latest.setRaidTime(760);
		ChambersTimes.Table table = ChambersTimes.average(Arrays.asList(earlier, latest), null, -1);
		Assert.assertEquals(20, mark(table, "Tekton"));
		Assert.assertEquals(Integer.valueOf(0), table.personalBestGap);
	}

	@Test
	public void ownPurplesCountItemsAndOneDryBreak() {
		RaidTracker dry = raid();
		dry.setPersonalPoints(867600);
		RaidTracker doubled = raid();
		doubled.setPersonalPoints(867600);
		doubled.setUniques(new ArrayList<>());
		doubled.getUniques().add(purple("Twisted bow"));
		doubled.getUniques().add(purple("Dexterous prayer scroll"));
		doubled.getUniques().add(side("Olmlet"));
		ChambersPurples board = ChambersPurples.of(Arrays.asList(dry, doubled));
		Assert.assertEquals(1, board.actual);
		Assert.assertEquals(1, board.scrolls);
		Assert.assertEquals(1, item(board, "Twisted Bow").got);
		Assert.assertEquals(1, item(board, "Dexterous Prayer Scroll").got);
		Assert.assertEquals(0, board.dryNow);
		Assert.assertEquals(1, board.sides.get(0).got);
		Assert.assertEquals(3, board.tracked.size());
	}

	@Test
	public void ownPurpleWithoutAPriceStillCounts() {
		RaidTracker raid = raid();
		raid.setPersonalPoints(867600);
		raid.setUniques(new ArrayList<>());
		UniqueDrop unpriced = new UniqueDrop();
		unpriced.setItem("Twisted buckler");
		unpriced.setOwn(true);
		raid.getUniques().add(unpriced);
		UniqueDrop teammate = new UniqueDrop();
		teammate.setItem("Elder maul");
		teammate.setOwn(false);
		teammate.setValue(1_000_000);
		raid.getUniques().add(teammate);
		ChambersPurples board = ChambersPurples.of(Arrays.asList(raid));
		Assert.assertEquals(1, board.actual);
		Assert.assertEquals(1, item(board, "Twisted Buckler").got);
		Assert.assertEquals(0, item(board, "Elder Maul").got);
		Assert.assertEquals(1, board.tracked.size());
	}

	@Test
	public void legacyOwnPurpleStillCounts() {
		RaidTracker raid = raid();
		raid.setPersonalPoints(867600);
		raid.setSpecialLoot("Elder Maul");
		raid.setSpecialLootInOwnName(true);
		ChambersPurples board = ChambersPurples.of(Arrays.asList(raid));
		Assert.assertEquals(1, item(board, "Elder Maul").got);
		Assert.assertEquals(1, board.actual);
	}

	@Test
	public void deathsUseTheDeathList() {
		RaidTracker raid = raid();
		raid.setPersonalPoints(867600);
		raid.setPersonalDeathCount(4);
		ChambersDeath first = new ChambersDeath();
		first.setPointsLost(8000);
		first.setRoom("Tekton");
		ChambersDeath second = new ChambersDeath();
		second.setPointsLost(7000);
		second.setRoom("Crabs");
		raid.setDeaths(new ArrayList<>());
		raid.getDeaths().add(first);
		raid.getDeaths().add(second);
		ChambersPurples board = ChambersPurples.of(Arrays.asList(raid));
		Assert.assertEquals(15000, board.pointsLost);
		Assert.assertEquals(2, board.rooms.size());
		Assert.assertEquals("Crabs", board.rooms.get(0).name);
		Assert.assertEquals(1, board.rooms.get(0).deaths);
		Assert.assertEquals("Tekton", board.rooms.get(1).name);
		Assert.assertEquals(1, board.rooms.get(1).deaths);

		ChambersDeath unnamed = new ChambersDeath();
		unnamed.setPointsLost(1500);
		raid.getDeaths().add(unnamed);
		board = ChambersPurples.of(Arrays.asList(raid));
		Assert.assertEquals(16500, board.pointsLost);
		Assert.assertEquals(2, board.rooms.size());
	}

	@Test
	public void raidsTodayStartsAtFourInTheMorning() {
		ZoneId zone = ZoneId.of("UTC");
		RaidTracker beforeFour = raid();
		beforeFour.setDate(utc(2026, 10, 8, 3, 59, zone));
		RaidTracker atFour = raid();
		atFour.setDate(utc(2026, 10, 8, 4, 0, zone));
		RaidTracker lastNight = raid();
		lastNight.setDate(utc(2026, 10, 7, 23, 0, zone));
		RaidTracker previousMorning = raid();
		previousMorning.setDate(utc(2026, 10, 7, 3, 30, zone));
		List<RaidTracker> raids = Arrays.asList(beforeFour, atFour, lastNight, previousMorning);

		long tenAm = utc(2026, 10, 8, 10, 0, zone);
		Assert.assertEquals(1, ChambersFacts.raidsToday(raids, tenAm, zone));

		long justBeforeFour = utc(2026, 10, 8, 3, 59, zone);
		Assert.assertEquals(2, ChambersFacts.raidsToday(raids, justBeforeFour, zone));
		Assert.assertEquals("1 raid today", ChambersFacts.raidsTodayLabel(1));
		Assert.assertEquals("0 raids today", ChambersFacts.raidsTodayLabel(0));
		Assert.assertEquals("4 raids today", ChambersFacts.raidsTodayLabel(4));
	}

	private static long utc(int year, int month, int day, int hour, int minute, ZoneId zone) {
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli();
	}

	private static ChambersTimes.Row row(ChambersTimes.Table table, String label) {
		ChambersTimes.Row found = rowOrNull(table, label);
		if (found == null) {
			Assert.fail("Missing " + label);
		}
		return found;
	}

	private static ChambersTimes.Row rowOrNull(ChambersTimes.Table table, String label) {
		for (int i = 0; i < table.rows.size(); i++) {
			if (label.equals(table.rows.get(i).label)) {
				return table.rows.get(i);
			}
		}
		return null;
	}

	private static int mark(ChambersTimes.Table table, String label) {
		for (int i = 0; i < table.pace.size(); i++) {
			if (label.equals(table.pace.get(i).label)) {
				return table.pace.get(i).aheadOfAverage;
			}
		}
		Assert.fail("Missing pace " + label);
		return 0;
	}

	private static ChambersPurples.Item item(ChambersPurples board, String name) {
		for (int i = 0; i < board.items.size(); i++) {
			if (name.equals(board.items.get(i).name)) {
				return board.items.get(i);
			}
		}
		Assert.fail("Missing " + name);
		return null;
	}

	private static UniqueDrop purple(String name) {
		UniqueDrop drop = new UniqueDrop();
		drop.setItem(name);
		drop.setOwn(true);
		drop.setValue(1_000_000);
		return drop;
	}

	private static UniqueDrop side(String name) {
		UniqueDrop drop = new UniqueDrop();
		drop.setItem(name);
		drop.setOwn(true);
		return drop;
	}

	private static void setRoom(RaidTracker raid, String room, int seconds) {
		switch (room) {
			case "Tekton":
				raid.setTektonTime(seconds);
				break;
			case "Crabs":
				raid.setCrabsTime(seconds);
				break;
			case "Ice demon":
				raid.setIceDemonTime(seconds);
				break;
			case "Shamans":
				raid.setShamansTime(seconds);
				break;
			case "Vanguards":
				raid.setVanguardsTime(seconds);
				break;
			case "Thieving":
				raid.setThievingTime(seconds);
				break;
			case "Vespula":
				raid.setVespulaTime(seconds);
				break;
			case "Tightrope":
				raid.setTightropeTime(seconds);
				break;
			case "Guardians":
				raid.setGuardiansTime(seconds);
				break;
			case "Vasa":
				raid.setVasaTime(seconds);
				break;
			case "Mystics":
				raid.setMysticsTime(seconds);
				break;
			default:
				raid.setMuttadilesTime(seconds);
				break;
		}
	}

	private static RaidTracker raid() {
		RaidTracker raid = new RaidTracker();
		raid.setInRaidChambers(true);
		return raid;
	}

	/** A Challenge Mode solo whose splits match the default sheet, including a mage hand on the last phase. */
	private static RaidTracker cmSoloOnTarget() {
		RaidTracker raid = raid();
		raid.setChallengeMode(true);
		raid.setTeamSize(1);
		raid.setCompletionCount(1);
		raid.setTektonTime(70);
		raid.setCrabsTime(56);
		raid.setIceDemonTime(144);
		raid.setShamansTime(63);
		raid.setVanguardsTime(132);
		raid.setThievingTime(75);
		raid.setVespulaTime(59);
		raid.setTightropeTime(47);
		raid.setGuardiansTime(110);
		raid.setVasaTime(70);
		raid.setMysticsTime(100);
		raid.setMuttadilesTime(85);
		raid.setLowerTime(1067);
		raid.setRaidTime(1535);
		ArrayList<Integer> hands = new ArrayList<>();
		hands.add(56);
		hands.add(56);
		hands.add(56);
		raid.setOlmMageHandTimes(hands);
		return raid;
	}
}
