package com.example.weather;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** 7 天预报列表 */
public class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.VH> {

    private final String[] days;
    private final int[] mins;
    private final int[] maxs;
    private final int[] codes;
    private final int[] pops;
    private final android.content.Context ctx;

    public DailyAdapter(android.content.Context ctx, String[] days, int[] mins,
                        int[] maxs, int[] codes, int[] pops) {
        this.ctx = ctx;
        this.days = days;
        this.mins = mins;
        this.maxs = maxs;
        this.codes = codes;
        this.pops = pops;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        h.tvDay.setText(days[pos]);
        h.ivIcon.setImageResource(WeatherCodes.icon(codes[pos], false));
        h.tvMin.setText(WeatherPrefs.fmtTempDeg(mins[pos], ctx));
        h.tvMax.setText(WeatherPrefs.fmtTempDeg(maxs[pos], ctx));

        int pop = pops != null && pos < pops.length ? pops[pos] : 0;
        h.tvPop.setText(pop >= 20 ? pop + "%" : "");
    }

    @Override
    public int getItemCount() {
        return days == null ? 0 : days.length;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDay, tvMin, tvMax, tvPop;
        ImageView ivIcon;

        VH(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_day);
            tvMin = v.findViewById(R.id.tv_min);
            tvMax = v.findViewById(R.id.tv_max);
            tvPop = v.findViewById(R.id.tv_pop);
            ivIcon = v.findViewById(R.id.iv_icon);
        }
    }
}
