package com.example.animeresolver;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scores a video-site title against the known names of one anime entry. */
final class AnimeTitleMatcher {
    private static final Pattern CHINESE_SEASON = Pattern.compile(
            "第\\s*([0-9一二三四五六七八九十]+)\\s*[季部]");
    private static final Pattern ENGLISH_SEASON = Pattern.compile(
            "(?:season\\s*|s)(\\d+)|(?:^|\\s)(\\d+)(?:st|nd|rd|th)\\s*season");
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern WEAK_WORDS = Pattern.compile(
            "(?i)(?:第\\s*[0-9一二三四五六七八九十]+\\s*[季部]|season\\s*\\d+|s\\d+|"
                    + "\\b(?:tv|ova|ona|movie|anime)\\b|动画|动漫|剧场版|电影版|全集|完结|"
                    + "在线观看|免费播放|高清|中字|字幕版)");

    private AnimeTitleMatcher() {
    }

    static int score(String candidateTitle, Collection<String> expectedTitles) {
        if (candidateTitle == null || candidateTitle.isBlank()
                || expectedTitles == null || expectedTitles.isEmpty()) return 0;

        List<String> expected = uniqueNames(expectedTitles);
        int expectedSeason = firstKnownSeason(expected);
        int candidateSeason = extractSeason(candidateTitle);
        int expectedYear = firstKnownYear(expected);
        int candidateYear = extractYear(candidateTitle);
        TitleKind candidateKind = titleKind(candidateTitle);
        int best = 0;

        for (String title : expected) {
            String candidate = normalize(candidateTitle);
            String wanted = normalize(title);
            if (candidate.isBlank() || wanted.isBlank()) continue;

            int value;
            if (candidate.equals(wanted)) {
                value = 100;
            } else {
                String candidateCore = core(candidateTitle);
                String wantedCore = core(title);
                if (!candidateCore.isBlank() && candidateCore.equals(wantedCore)) {
                    value = 94;
                } else if (containsDistinctive(candidateCore, wantedCore)) {
                    int shorter = Math.min(candidateCore.length(), wantedCore.length());
                    int longer = Math.max(candidateCore.length(), wantedCore.length());
                    float coverage = (float) shorter / Math.max(1, longer);
                    value = 78 + Math.round(18f * coverage);
                } else {
                    int similarity = similarity(candidateCore, wantedCore);
                    value = similarity >= 72 ? Math.min(89, similarity) : similarity;
                }
            }

            value = applyMetadataChecks(value, expectedSeason, candidateSeason,
                    expectedYear, candidateYear, titleKind(title), candidateKind);
            best = Math.max(best, value);
        }
        return Math.max(0, Math.min(100, best));
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static String core(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return normalize(WEAK_WORDS.matcher(normalized).replaceAll(""));
    }

    static int extractSeason(String value) {
        if (value == null) return -1;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        Matcher chinese = CHINESE_SEASON.matcher(normalized);
        if (chinese.find()) return parseSeasonNumber(chinese.group(1));
        Matcher english = ENGLISH_SEASON.matcher(normalized);
        if (english.find()) {
            String number = english.group(1) == null ? english.group(2) : english.group(1);
            try {
                return Integer.parseInt(number);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static int applyMetadataChecks(int value, int expectedSeason, int candidateSeason,
            int expectedYear, int candidateYear, TitleKind expectedKind, TitleKind candidateKind) {
        if (expectedSeason > 0 && candidateSeason > 0) {
            if (expectedSeason != candidateSeason) return Math.min(value, 25);
            value = Math.min(100, value + 4);
        } else if (expectedSeason > 0) {
            // A site may omit the season, so keep it eligible but rank an explicit match first.
            value -= 8;
        } else if (expectedSeason <= 0 && candidateSeason > 1) {
            value -= 28;
        }
        if (expectedYear > 0 && candidateYear > 0) {
            int difference = Math.abs(expectedYear - candidateYear);
            if (difference == 0) value = Math.min(100, value + 3);
            else if (difference > 1) value -= 30;
        }
        if (expectedKind != TitleKind.UNKNOWN && candidateKind != TitleKind.UNKNOWN) {
            if (expectedKind != candidateKind) value -= 55;
            else value = Math.min(100, value + 3);
        }
        return value;
    }

    private static boolean containsDistinctive(String left, String right) {
        if (left.isBlank() || right.isBlank()) return false;
        String shorter = left.length() <= right.length() ? left : right;
        String longer = left.length() <= right.length() ? right : left;
        if (shorter.length() < 3 || !longer.contains(shorter)) return false;
        float coverage = (float) shorter.length() / longer.length();
        return shorter.length() >= 4 || coverage >= 0.55f;
    }

    private static int similarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) return 0;
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            previous = current;
        }
        int distance = previous[right.length()];
        return Math.max(0, Math.round(100f * (1f
                - (float) distance / Math.max(left.length(), right.length()))));
    }

    private static List<String> uniqueNames(Collection<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            if (normalized.add(normalize(value))) result.add(value.trim());
        }
        return result;
    }

    private static int firstKnownSeason(Collection<String> values) {
        for (String value : values) {
            int season = extractSeason(value);
            if (season > 0) return season;
        }
        return -1;
    }

    private static int extractYear(String value) {
        if (value == null) return -1;
        Matcher matcher = YEAR.matcher(value);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int firstKnownYear(Collection<String> values) {
        for (String value : values) {
            int year = extractYear(value);
            if (year > 0) return year;
        }
        return -1;
    }

    private static int parseSeasonNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // Continue with Chinese numerals.
        }
        String digits = "一二三四五六七八九";
        if ("十".equals(value)) return 10;
        int ten = value.indexOf('十');
        if (ten >= 0) {
            int high = ten == 0 ? 1 : digits.indexOf(value.charAt(0)) + 1;
            int low = ten == value.length() - 1 ? 0 : digits.indexOf(value.charAt(ten + 1)) + 1;
            return high * 10 + Math.max(0, low);
        }
        return value.length() == 1 ? digits.indexOf(value.charAt(0)) + 1 : -1;
    }

    private static TitleKind titleKind(String value) {
        if (value == null) return TitleKind.UNKNOWN;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (normalized.contains("剧场版") || normalized.contains("电影版")
                || Pattern.compile("\\bmovie\\b").matcher(normalized).find()) return TitleKind.MOVIE;
        if (Pattern.compile("\\bova\\b").matcher(normalized).find()) return TitleKind.OVA;
        if (Pattern.compile("\\bona\\b").matcher(normalized).find()) return TitleKind.ONA;
        if (Pattern.compile("\\btv\\b").matcher(normalized).find()) return TitleKind.TV;
        return TitleKind.UNKNOWN;
    }

    private enum TitleKind {
        TV, MOVIE, OVA, ONA, UNKNOWN
    }
}
