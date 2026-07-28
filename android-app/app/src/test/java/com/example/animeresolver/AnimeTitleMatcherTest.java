package com.example.animeresolver;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class AnimeTitleMatcherTest {
    @Test
    public void exactAliasWins() {
        int score = AnimeTitleMatcher.score("転生したらスライムだった件",
                List.of("关于我转生变成史莱姆这档事", "転生したらスライムだった件"));
        assertTrue(score >= 95);
    }

    @Test
    public void acceptsDistinctiveShortTitle() {
        int score = AnimeTitleMatcher.score("与你相恋",
                List.of("与你相恋到世界尽头"));
        assertTrue(score >= 78);
    }

    @Test
    public void rejectsVeryShortGenericFragment() {
        int score = AnimeTitleMatcher.score("恋爱",
                List.of("我和班上最讨厌的女生结婚了"));
        assertTrue(score < 74);
    }

    @Test
    public void rejectsDifferentSeason() {
        int score = AnimeTitleMatcher.score("关于我转生变成史莱姆这档事 第三季",
                List.of("关于我转生变成史莱姆这档事 第四季"));
        assertTrue(score < 74);
    }

    @Test
    public void explicitCorrectSeasonRanksAboveSeasonlessAlias() {
        List<String> names = List.of("关于我转生变成史莱姆这档事 第四季",
                "転生したらスライムだった件");
        int seasonless = AnimeTitleMatcher.score("転生したらスライムだった件", names);
        int explicit = AnimeTitleMatcher.score("关于我转生变成史莱姆这档事 第四季", names);
        assertTrue(explicit > seasonless);
        assertTrue(seasonless >= 74);
    }

    @Test
    public void acceptsSeasonWrittenInEnglish() {
        int score = AnimeTitleMatcher.score("SPY x FAMILY Season 2",
                List.of("间谍过家家 第二季", "SPY×FAMILY 第2季"));
        assertTrue(score >= 90);
    }

    @Test
    public void rejectsMovieWhenTvIsExplicit() {
        int score = AnimeTitleMatcher.score("紫罗兰永恒花园 剧场版",
                List.of("紫罗兰永恒花园 TV"));
        assertTrue(score < 74);
    }
}
