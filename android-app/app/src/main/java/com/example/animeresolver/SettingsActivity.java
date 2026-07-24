package com.example.animeresolver;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends Activity {
    private static final int BLUE = Color.rgb(25, 112, 243);
    private static final int INK = Color.rgb(22, 25, 31);
    private static final int MUTED = Color.rgb(105, 108, 115);
    private static final int WARM = Color.rgb(253, 252, 250);

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildPage();
    }

    private void buildPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WARM);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(6), dp(8), 0);
        MaterialButton back = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
        back.setIconResource(R.drawable.ic_arrow_back_24);
        back.setIconTint(ColorStateList.valueOf(INK));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(56)));
        TextView title = text("设置", 21, INK, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(20), dp(22), dp(30));
        body.addView(text("索引数据源", 20, INK, true));
        TextView hint = text("控制放送页优先使用的数据。AniFun 暂时不可用或没有结果时，会自动回退到 Bangumi。", 14, MUTED, false);
        hint.setLineSpacing(dp(3), 1f);
        body.addView(hint, margin(0, dp(9), 0, dp(18)));

        RadioGroup choices = new RadioGroup(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setBackground(cardBackground());
        addChoice(choices, IndexSourceStore.AUTO, "自动", "优先使用 AniFun 周表，失败时回退 Bangumi / AniList。");
        addChoice(choices, IndexSourceStore.BANGUMI, "Bangumi / AniList", "使用 Bangumi 索引，并补充 AniList 放送时间。");
        addChoice(choices, IndexSourceStore.ANIFUN, "AniFun", "优先使用 AniFun 周表；找不到时仍会回退 Bangumi。");
        String selected = IndexSourceStore.get(this);
        for (int i = 0; i < choices.getChildCount(); i++) {
            View child = choices.getChildAt(i);
            if (selected.equals(child.getTag())) ((RadioButton) child).setChecked(true);
        }
        choices.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton checked = group.findViewById(checkedId);
            if (checked != null) IndexSourceStore.set(this, (String) checked.getTag());
        });
        body.addView(choices, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView detail = text("详情页会按作品标题优先匹配 Bangumi 条目，从而复用现有简介、集数与播放流程。", 13, MUTED, false);
        detail.setLineSpacing(dp(3), 1f);
        body.addView(detail, margin(0, dp(18), 0, 0));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        SystemBars.apply(this, root, WARM);
        setContentView(root);
    }

    private void addChoice(RadioGroup group, String value, String title, String subtitle) {
        RadioButton choice = new RadioButton(this);
        choice.setId(View.generateViewId());
        choice.setTag(value);
        choice.setText(title + "\n" + subtitle);
        choice.setTextColor(INK);
        choice.setTextSize(16);
        choice.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        choice.setGravity(Gravity.CENTER_VERTICAL);
        choice.setButtonTintList(ColorStateList.valueOf(BLUE));
        choice.setPadding(dp(8), dp(13), dp(12), dp(13));
        choice.setLineSpacing(dp(4), 1f);
        group.addView(choice, new RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom); return params;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE); drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), Color.rgb(229, 231, 235)); return drawable;
    }
}
