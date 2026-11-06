package com.example.weather;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

/** 24 小时逐小时预报 */
public class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.VH> {

    private final String[] times;
    private final int[] temps;
    private final int[] codes;
    private final int[] pops;
    private final android.content.Context ctx;

    /** 点击某个小时的回调 */
    public interface OnHourClick {
        void onHourClick(int index);
    }

    private OnHourClick click;

    public void setOnHourClick(OnHourClick l) {
        this.click = l;
    }

    public HourlyAdapter(android.content.Context ctx, String[] times, int[] temps,
                         int[] codes, int[] pops) {
        this.ctx = ctx;
        this.times = times;
        this.temps = temps;
        this.codes = codes;
        this.pops = pops;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hourly, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        String t = times[pos];
        String label = clock(t);
        if (pos == 0) label = "现在";
        h.tvTime.setText(label);

        h.ivIcon.setImageResource(WeatherCodes.icon(codes[pos], isNightHour(t)));
        h.tvTemp.setText(WeatherPrefs.fmtTempDeg(temps[pos], ctx));

        int pop = pops != null && pos < pops.length ? pops[pos] : 0;
        // 降水概率小柱：宽度按百分比伸缩，概率越高柱越满
        int trackPx = Math.round(38 * h.itemView.getResources().getDisplayMetrics().density);
        int fillPx = Math.round(trackPx * Math.max(0, Math.min(100, pop)) / 100f);
        ViewGroup.LayoutParams lp = h.precipFill.getLayoutParams();
        if (lp != null) {
            lp.width = fillPx;
            h.precipFill.setLayoutParams(lp);
        }
        h.precipFill.setVisibility(pop >= 5 ? View.VISIBLE : View.INVISIBLE);
        h.precipFill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx,
                pop >= 60 ? R.color.precip_bar : R.color.precip_bar_soft)));

        if (pop >= 20) {
            h.tvPop.setText(String.format(Locale.US, "%d%%", pop));
            h.tvPop.setVisibility(View.VISIBLE);
        } else {
            h.tvPop.setText("");
            h.tvPop.setVisibility(View.INVISIBLE);
        }

        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onHourClick(h.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return times == null ? 0 : times.length;
    }

    /** "2026-11-01T14:00" → "14:00" */
    private static String clock(String t) {
        if (t == null) return "";
        int i = t.indexOf('T');
        return i >= 0 && t.length() >= i + 6 ? t.substring(i + 1, i + 6) : t;
    }

    /** 依据小时判断昼夜（18:00–06:00 视为夜间） */
    private static boolean isNightHour(String t) {
        if (t == null) return WeatherCodes.isNightNow();
        int i = t.indexOf('T');
        if (i < 0 || t.length() < i + 3) return WeatherCodes.isNightNow();
        try {
            int hour = Integer.parseInt(t.substring(i + 1, i + 3));
            return hour >= 18 || hour < 6;
        } catch (Exception e) {
            return WeatherCodes.isNightNow();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTime, tvTemp, tvPop;
        ImageView ivIcon;
        View precipFill;

        VH(View v) {
            super(v);
            tvTime = v.findViewById(R.id.tv_time);
            tvTemp = v.findViewById(R.id.tv_temp);
            tvPop = v.findViewById(R.id.tv_pop);
            ivIcon = v.findViewById(R.id.iv_icon);
            precipFill = v.findViewById(R.id.precip_fill);
        }
    }
}
