package com.example.weather;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.VH> {

    private final String[] days;
    private final int[] mins;
    private final int[] maxs;
    private final int[] codes;
    private final android.content.Context ctx;
    private final boolean night;

    public DailyAdapter(android.content.Context ctx, boolean night, String[] days, int[] mins, int[] maxs, int[] codes) {
        this.ctx = ctx;
        this.night = night;
        this.days = days;
        this.mins = mins;
        this.maxs = maxs;
        this.codes = codes;
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
        h.ivIcon.setImageResource(WeatherCodes.icon(codes[pos], night));
        h.tvMin.setText(WeatherPrefs.fmtTemp(mins[pos], ctx) + "°");
        h.tvMax.setText(WeatherPrefs.fmtTemp(maxs[pos], ctx) + "°");
    }

    @Override
    public int getItemCount() {
        return days == null ? 0 : days.length;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDay, tvMin, tvMax;
        ImageView ivIcon;
        VH(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_day);
            tvMin = v.findViewById(R.id.tv_min);
            tvMax = v.findViewById(R.id.tv_max);
            ivIcon = v.findViewById(R.id.iv_icon);
        }
    }
}
