package com.rentrix.rentrixserver.location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Typo-tolerant word-prefix matching of what a user types against a place name (D-164): every typed word must start
 * one word of the name, in any order, with a few typos allowed in the longer words. "prestge lake" finds
 * "Prestige Lakeside", "lakeside" finds it too, "prestige x" does not. The cost is the number of edits used; lower is
 * a better match.
 */
public final class NameMatch {

	/** No match. */
	public static final int NONE = -1;

	/** Words that only say what a level is ("Tower B", "Block C", "Wing A"), so they never decide a tower match. */
	public static final Set<String> LEVEL_WORDS = Set.of("tower", "towers", "block", "blk", "wing", "phase");

	/** Words people put before a flat number: "Lakeside flat 1204", "Villa 12". */
	public static final Set<String> UNIT_WORDS = Set.of("flat", "unit", "no", "number", "apt", "house", "villa");

	private NameMatch() {
	}

	/** Lower-cased runs of letters and digits: "Lakeside B-1204" → [lakeside, b, 1204]. */
	public static List<String> words(String text) {
		List<String> words = new ArrayList<>();
		if (text == null) {
			return words;
		}
		for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
			if (!word.isEmpty()) {
				words.add(word);
			}
		}
		return words;
	}

	/** Typos allowed in a typed word: none up to 3 letters, 1 up to 6, then 2. */
	static int allowedEdits(int length) {
		return length <= 3 ? 0 : length <= 6 ? 1 : 2;
	}

	/**
	 * Matches every typed word to a different word of the name (cheapest first); the summed edits, or {@link #NONE}.
	 * An empty typed list never matches.
	 */
	public static int cost(List<String> typed, List<String> name) {
		if (typed.isEmpty() || typed.size() > name.size()) {
			return NONE;
		}
		boolean[] used = new boolean[name.size()];
		int total = 0;
		for (String word : typed) {
			int best = NONE;
			int bestIndex = -1;
			for (int i = 0; i < name.size(); i++) {
				if (used[i]) {
					continue;
				}
				int edits = prefixEdits(word, name.get(i));
				if (edits != NONE && (best == NONE || edits < best)) {
					best = edits;
					bestIndex = i;
				}
			}
			if (best == NONE) {
				return NONE;
			}
			used[bestIndex] = true;
			total += best;
		}
		return total;
	}

	/**
	 * Edits (insert, delete, change, swap two neighbours) that turn {@code typed} into some prefix of {@code word},
	 * or {@link #NONE} when more than {@link #allowedEdits} are needed. Digits must match exactly, so "1204" never
	 * finds "1205".
	 */
	static int prefixEdits(String typed, String word) {
		if (word.startsWith(typed)) {
			return 0;
		}
		int allowed = allowedEdits(typed.length());
		if (allowed == 0 || typed.chars().anyMatch(Character::isDigit)) {
			return NONE;
		}
		int n = typed.length();
		int m = Math.min(word.length(), n + allowed);
		int[][] d = new int[n + 1][m + 1];
		for (int i = 0; i <= n; i++) {
			d[i][0] = i;
		}
		for (int j = 0; j <= m; j++) {
			d[0][j] = j;
		}
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= m; j++) {
				int change = typed.charAt(i - 1) == word.charAt(j - 1) ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + change);
				if (i > 1 && j > 1 && typed.charAt(i - 1) == word.charAt(j - 2)
					&& typed.charAt(i - 2) == word.charAt(j - 1)) {
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
				}
			}
		}
		int best = Integer.MAX_VALUE;
		for (int j = 0; j <= m; j++) {
			best = Math.min(best, d[n][j]);
		}
		return best <= allowed ? best : NONE;
	}

	/** {@code words} without {@link #LEVEL_WORDS}. */
	public static List<String> withoutLevelWords(List<String> words) {
		return words.stream().filter(w -> !LEVEL_WORDS.contains(w)).toList();
	}

}
