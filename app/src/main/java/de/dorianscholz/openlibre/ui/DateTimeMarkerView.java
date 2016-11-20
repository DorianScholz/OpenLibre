package de.dorianscholz.openlibre.ui;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import de.dorianscholz.openlibre.R;

import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDateTime;
import static de.dorianscholz.openlibre.model.GlucoseData.formatValue;
import static de.dorianscholz.openlibre.model.GlucoseData.getDisplayUnit;

public class DateTimeMarkerView extends MarkerView {

    private TextView tvContent;
    private long mFirstDate;

    public DateTimeMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvContent = (TextView) findViewById(R.id.tvContent);
    }

    public void setFirstDate(long firstDate) {
        mFirstDate = firstDate;
    }

    private long convertXAxisValueToDate(float value) {
        return mFirstDate + (long) (value * TimeUnit.MINUTES.toMillis(1L));
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        tvContent.setText(formatValue(e.getY()) + " " + getDisplayUnit() + " at " + mFormatDateTime.format(new Date(convertXAxisValueToDate(e.getX()))));
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }

}