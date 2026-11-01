package com.example.weather;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** 生活指数 3 列网格 */
public class LifeIndexAdapter extends RecyclerView.Adapter<LifeIndexAdapter.VH> {

    private final List<LifeIndex.Item> items = new ArrayList<>();

    public void setItems(List<LifeIndex.Item> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_index, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        LifeIndex.Item it = items.get(pos);
        h.name.setText(it.name);
        h.value.setText(it.value);
        h.value.setTextColor(it.color);
        h.detail.setText(it.detail);

        Drawable dot = h.dot.getBackground();
        if (dot != null) {
            dot = DrawableCompat.wrap(dot.mutate());
            DrawableCompat.setTint(dot, it.color);
            h.dot.setBackground(dot);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, value, detail;
        View dot;

        VH(View v) {
            super(v);
            name = v.findViewById(R.id.index_name);
            value = v.findViewById(R.id.index_value);
            detail = v.findViewById(R.id.index_detail);
            dot = v.findViewById(R.id.index_dot);
        }
    }
}
