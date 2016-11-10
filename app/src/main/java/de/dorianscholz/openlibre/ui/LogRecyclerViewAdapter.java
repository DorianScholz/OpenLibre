package de.dorianscholz.openlibre.ui;

import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

import de.dorianscholz.openlibre.R;
import de.dorianscholz.openlibre.model.PredictionData;
import de.dorianscholz.openlibre.model.ReadingData;
import io.realm.OrderedRealmCollection;
import io.realm.RealmRecyclerViewAdapter;

import static de.dorianscholz.openlibre.model.AlgorithmUtil.getPredictionData;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.getTrendArrow;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDate;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatTime;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.trendArrowMap;
import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_UNIT_IS_MMOL;
import static java.lang.Math.min;

class LogRecyclerViewAdapter
        extends RealmRecyclerViewAdapter<ReadingData, LogRecyclerViewAdapter.LogRowViewHolder> {

    private final LogFragment fragment;

    LogRecyclerViewAdapter(LogFragment fragment, OrderedRealmCollection<ReadingData> data) {
        super(fragment.getActivity() ,data, true);
        this.fragment = fragment;
    }

    @Override
    public LogRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = inflater.inflate(R.layout.fragment_log_row, parent, false);
        return new LogRowViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(LogRowViewHolder holder, int position) {
        ReadingData readingData = getData().get(position);
        PredictionData predictedGlucose = getPredictionData(readingData.trend);
        holder.readingData = readingData;
        holder.tv_date.setText(mFormatDate.format(new Date(readingData.date)));
        holder.tv_time.setText(mFormatTime.format(new Date(readingData.date)));
        holder.tv_glucose.setText(predictedGlucose.glucoseData.glucoseString(GLUCOSE_UNIT_IS_MMOL));
        if (GLUCOSE_UNIT_IS_MMOL) {
            holder.iv_unit.setImageResource(R.drawable.unit_mmoll);
        } else {
            holder.iv_unit.setImageResource(R.drawable.unit_mgdl);
        }
        holder.iv_prediction.setImageResource(trendArrowMap.get(getTrendArrow(predictedGlucose)));
        // reduce trend arrow visibility according to prediction confidence
        holder.iv_prediction.setAlpha((float) min(1, 0.1 + predictedGlucose.confidence()));
    }

    class LogRowViewHolder
            extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener  {
        TextView tv_date;
        TextView tv_time;
        TextView tv_glucose;
        ImageView iv_unit;
        ImageView iv_prediction;
        ReadingData readingData;

        LogRowViewHolder(View view) {
            super(view);
            tv_date = (TextView) view.findViewById(R.id.tv_log_date);
            tv_time = (TextView) view.findViewById(R.id.tv_log_time);
            tv_glucose = (TextView) view.findViewById(R.id.tv_log_glucose);
            iv_unit = (ImageView) view.findViewById(R.id.iv_log_unit);
            iv_prediction = (ImageView) view.findViewById(R.id.iv_log_prediction);
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
                default:
                    Toast.makeText(itemView.getContext(),
                            String.format("unknown menu item id: %d", id),
                            Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    }
}
