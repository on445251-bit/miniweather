package com.example.weather;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.VH> {

    private final String[] times;
    private final int[] temps;
    private final int[] codes;
    private final android.content.Context ctx;
    private final boolean night;

    public HourlyAdapter(android.content.Context ctx, boolean night, String[] times, int[] temps, int[] codes) {
        this.ctx = ctx;
        this.night = night;
        this.times = times;
        this.temps = temps;
        this.codes = codes;
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
        // t 形如 2026-11-01T14:00
        String label = t.contains("T") ? t.substring(t.indexOf('T') + 1) : t;
        if (pos == 0) label = "现在";
        h.tvTime.setText(label);
        h.ivIcon.setImageResource(WeatherCodes.icon(codes[pos], night));
        h.tvTemp.setText(WeatherPrefs.fmtTemp(temps[pos], ctx) + "°");
    }

    @Override
    public int getItemCount() {
        return times == null ? 0 : times.length;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTime, tvTemp;
        ImageView ivIcon;
        VH(View v) {
            super(v);
            tvTime = v.findViewById(R.id.tv_time);
            tvTemp = v.findViewById(R.id.tv_temp);
            ivIcon = v.findViewById(R.id.iv_icon);
        }
    }
}
