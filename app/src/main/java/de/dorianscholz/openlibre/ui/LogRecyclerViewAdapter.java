package de.dorianscholz.openlibre.ui;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Date;

import de.dorianscholz.openlibre.R;
import de.dorianscholz.openlibre.model.PredictionData;
import de.dorianscholz.openlibre.model.ReadingData;
import io.realm.OrderedRealmCollection;
import io.realm.RealmRecyclerViewAdapter;

import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_UNIT_IS_MMOL;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.TREND_UP_DOWN_LIMIT;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDate;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatTimeShort;
import static java.lang.Math.max;
import static java.lang.Math.min;

class LogRecyclerViewAdapter
        extends RealmRecyclerViewAdapter<ReadingData, LogRecyclerViewAdapter.LogRowViewHolder> {
    private static final String LOG_ID = "GLUCOSE::" + LogRecyclerViewAdapter.class.getSimpleName();

    private final LogFragment fragment;

    LogRecyclerViewAdapter(LogFragment fragment, OrderedRealmCollection<ReadingData> data) {
        super(fragment.getActivity(), data, true);
        this.fragment = fragment;
    }

    @Override
    public LogRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = inflater.inflate(R.layout.fragment_log_row, parent, false);
        return new LogRowViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(LogRowViewHolder holder, int position) {
        ReadingData readingData;
        try {
            readingData = getData().get(position);
        } catch (NullPointerException e) {
            Log.e(LOG_ID, "Null pointer at position: " + position);
            holder.itemView.setVisibility(View.GONE);
            return;
        }
        if (readingData.trend.size() == 0) {
            Log.e(LOG_ID, "No trend data at position: " + position);
            holder.itemView.setVisibility(View.GONE);
            return;
        }
        holder.itemView.setVisibility(View.VISIBLE);
        PredictionData predictedGlucose = new PredictionData(readingData.trend);
        holder.readingData = readingData;
        holder.tv_date.setText(mFormatDate.format(new Date(readingData.date)));
        holder.tv_time.setText(mFormatTimeShort.format(new Date(readingData.date)));
        holder.tv_glucose.setText(predictedGlucose.glucoseData.glucoseString());
        if (GLUCOSE_UNIT_IS_MMOL) {
            holder.iv_unit.setImageResource(R.drawable.ic_unit_mmoll);
        } else {
            holder.iv_unit.setImageResource(R.drawable.ic_unit_mgdl);
        }
        //holder.iv_predictionArrow.setImageResource(trendArrowMap.get(getTrendArrow(predictedGlucose)));
        float rotationDegrees = -90f * max(-1f, min(1f, (float) (predictedGlucose.glucoseSlopeRaw / TREND_UP_DOWN_LIMIT)));
        holder.iv_predictionArrow.setRotation(rotationDegrees);
        // reduce trend arrow visibility according to prediction confidence
        holder.iv_predictionArrow.setAlpha((float) min(1, 0.1 + predictedGlucose.confidence()));
    }

    class LogRowViewHolder
            extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener  {
        TextView tv_date;
        TextView tv_time;
        TextView tv_glucose;
        ImageView iv_unit;
        ImageView iv_predictionArrow;
        ReadingData readingData;

        LogRowViewHolder(View view) {
            super(view);
            tv_date = (TextView) view.findViewById(R.id.tv_log_date);
            tv_time = (TextView) view.findViewById(R.id.tv_log_time);
            tv_glucose = (TextView) view.findViewById(R.id.tv_log_glucose);
            iv_unit = (ImageView) view.findViewById(R.id.iv_log_unit);
            iv_predictionArrow = (ImageView) view.findViewById(R.id.iv_log_prediction);
            view.setOnCreateContextMenuListener(this);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            fragment.showScanData(readingData);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
            MenuInflater inflater = new MenuInflater(itemView.getContext());
            inflater.inflate(R.menu.menu_log_item_context, menu);
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setOnMenuItemClickListener(this);
            }
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int id = item.getItemId();
            switch (id) {
                case R.id.action_show_scan:
                    fragment.showScanData(readingData);
                    return true;
                case R.id.action_delete_scan:
                    fragment.deleteScanData(readingData);
                    return true;
            }
            return false;
        }
    }
}
