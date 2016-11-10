package de.dorianscholz.openlibre.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.dorianscholz.openlibre.R;
import de.dorianscholz.openlibre.model.GlucoseData;
import de.dorianscholz.openlibre.model.PredictionData;
import de.dorianscholz.openlibre.model.ReadingData;

import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_TARGET_MAX;
import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_TARGET_MIN;
import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_UNIT_IS_MMOL;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.convertGlucoseMGDLToDisplayUnit;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.convertGlucoseRawToDisplayUnit;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.getPredictionData;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.getTrendArrow;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDate;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDateShort;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDateTime;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDayTime;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatTime;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.trendArrowMap;
import static java.lang.Math.min;

public class DataPlotFragment extends Fragment implements OnChartValueSelectedListener {
    private static final String LOG_ID = "GLUCOSE::" + DataPlotFragment.class.getSimpleName();

    private final static int NUM_PLOT_COLORS = 1;
    private final static int[][] PLOT_COLORS = new int[][] {
            {Color.BLUE, Color.BLUE},
            {Color.MAGENTA, Color.RED},
            {Color.CYAN, Color.GREEN},
    };
    private static int mPlotColorIndex = 0;

    private View mDataPlotView;
    LineChart mPlot;
    private long mFirstDate = -1;

    @SuppressWarnings("unused")
    public static DataPlotFragment newInstance() {
        return new DataPlotFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mDataPlotView = inflater.inflate(R.layout.fragment_data_plot, container, false);

        setupPlot();

        clearScanData();

        return mDataPlotView;
    }

    public void clearScanData() {
        (mDataPlotView.findViewById(R.id.ll_scan)).setVisibility(View.VISIBLE);
        (mDataPlotView.findViewById(R.id.pb_scan_spinning)).setVisibility(View.INVISIBLE);

        mPlot.setNoDataText("");
        ((TextView) mDataPlotView.findViewById(R.id.tv_plotTitle)).setText("");
        ((TextView) mDataPlotView.findViewById(R.id.tv_currentGlucose)).setText("");
        mDataPlotView.findViewById(R.id.iv_unit).setVisibility(View.INVISIBLE);
        mDataPlotView.findViewById(R.id.iv_prediction).setVisibility(View.INVISIBLE);
    }

    private void updateScanData(List<GlucoseData> trend) {
        if (trend.size() == 0) {
            Toast.makeText(this.getContext(), "No current data available!", Toast.LENGTH_LONG).show();
            return;
        }

        (mDataPlotView.findViewById(R.id.ll_scan)).setVisibility(View.INVISIBLE);

        PredictionData predictedGlucose = getPredictionData(trend);

        TextView tv_currentGlucose = (TextView) mDataPlotView.findViewById(R.id.tv_currentGlucose);
        tv_currentGlucose.setText(String.valueOf(predictedGlucose.glucoseData.glucoseString(GLUCOSE_UNIT_IS_MMOL)));

        ImageView iv_unit = (ImageView) mDataPlotView.findViewById(R.id.iv_unit);
        if (GLUCOSE_UNIT_IS_MMOL) {
            iv_unit.setImageResource(R.drawable.unit_mmoll);
        } else {
            iv_unit.setImageResource(R.drawable.unit_mgdl);
        }
        iv_unit.setVisibility(View.VISIBLE);

        ImageView iv_prediction_arrow = (ImageView) mDataPlotView.findViewById(R.id.iv_prediction);
        iv_prediction_arrow.setImageResource(trendArrowMap.get(getTrendArrow(predictedGlucose)));
        // reduce trend arrow visibility according to prediction confidence
        iv_prediction_arrow.setAlpha((float) min(1, 0.1 + predictedGlucose.confidence()));
        iv_prediction_arrow.setVisibility(View.VISIBLE);
    }

    private void setupPlot() {
        mPlot = (LineChart) mDataPlotView.findViewById(R.id.cv_LastScan);

        // no description text
        mPlot.getDescription().setEnabled(false);

        // enable touch gestures
        mPlot.setTouchEnabled(true);
        mPlot.setDragDecelerationFrictionCoef(0.9f);

        // if disabled, scaling can be done on x- and y-axis separately
        mPlot.setPinchZoom(true);

        // enable scaling and dragging
        mPlot.setDragEnabled(true);
        mPlot.setScaleEnabled(true);
        mPlot.setDrawGridBackground(false);

        // set an alternative background color
        mPlot.setBackgroundColor(Color.argb(0, 255, 255, 255));

        mPlot.setOnChartValueSelectedListener(this);

        Legend legend = mPlot.getLegend();
        legend.setEnabled(false);

        XAxis xAxis = mPlot.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(12f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(true);
        xAxis.setCenterAxisLabels(false);
        xAxis.setGranularity(convertDateToXAxisValue(TimeUnit.MINUTES.toMillis(5L))); // same unit as x axis values
        xAxis.enableGridDashedLine(5f, 5f, 0f);
        xAxis.setDrawLimitLinesBehindData(true);
        xAxis.setLabelCount(4);

        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                long date = convertXAxisValueToDate(value);
                return mFormatTime.format(new Date(date));
            }

            @Override
            public int getDecimalDigits() {
                return 0;
            }
        });

        YAxis leftAxis = mPlot.getAxisLeft();
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        leftAxis.setTextSize(12f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGranularityEnabled(true);
        leftAxis.setGranularity(5f);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(500f);

        YAxis rightAxis = mPlot.getAxisRight();
        rightAxis.setEnabled(false);

        LimitLine limitLineMax = new LimitLine(
                GLUCOSE_TARGET_MAX,
                getResources().getString(R.string.pref_glucuse_target_max)
        );
        limitLineMax.setLineWidth(4f);
        limitLineMax.setTextSize(9f);
        limitLineMax.setLineColor(Color.argb(128, 200, 150, 100));
        leftAxis.addLimitLine(limitLineMax);

        LimitLine limitLineMin = new LimitLine(
                GLUCOSE_TARGET_MIN,
                getResources().getString(R.string.pref_glucose_target_min)
        );
        limitLineMin.setLineWidth(4f);
        limitLineMin.setTextSize(9f);
        limitLineMin.setLineColor(Color.argb(128, 200, 100, 150));
        limitLineMin.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
        leftAxis.addLimitLine(limitLineMin);

        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
                mPlot.setHardwareAccelerationEnabled(false);
            } else {
                mPlot.setHardwareAccelerationEnabled(true);
            }
        } catch (Exception e) {
            Log.d(LOG_ID, "Hardware acceleration for data plot failed: " + e.toString());
        }
    }

    void onDataUpdate(List<GlucoseData> history, List<GlucoseData> trend) {
        updatePlot(history, trend);
    }

    public void onDataUpdate(List<ReadingData> readingDataList) {
        mPlot.clear();
        for (ReadingData readingData : readingDataList) {
            addLineData(readingData.history, readingData.trend);
        }
        updatePlotTitle(false);
        updateChartViewConstrains();
    }

    void onDataUpdate(ReadingData readData) {
        updateScanData(readData.trend);
        updatePlot(readData.history, readData.trend);
    }

    private void updatePlot(List<GlucoseData> history, List<GlucoseData> trend) {
        Log.d(LOG_ID, String.format("#history: %d, #trend: %d", history.size(), trend == null ? 0 : trend.size()));

        if (history.size() == 0) {
            Toast.makeText(this.getContext(), "No data available!", Toast.LENGTH_LONG).show();
            return;
        }

        (mDataPlotView.findViewById(R.id.ll_scan)).setVisibility(View.INVISIBLE);

        mPlot.clear();
        addLineData(history, trend);

        updatePlotTitle(trend != null);

        updateChartViewConstrains();
    }

    private void addLineData(List<GlucoseData> history, List<GlucoseData> trend) {
        if (mFirstDate < 0) {
            mFirstDate = history.get(0).date;
        }

        LineData lineData = mPlot.getData();
        if (lineData == null) {
            lineData = new LineData();
        }
        lineData.addDataSet(makeLineData(history, false));
        if (trend != null) {
            lineData.addDataSet(makeLineData(trend, true));
        }
        mPlotColorIndex++;
        mPlot.setData(lineData);
    }

    private void updatePlotTitle(boolean isScanData) {
        TextView tv_plotTitle = (TextView) mDataPlotView.findViewById(R.id.tv_plotTitle);
        String chartTitle;
        if (isScanData) {
            chartTitle = String.format("Scan date: %s",
                    mFormatDateTime.format(new Date(convertXAxisValueToDate(mPlot.getData().getXMax()))));
        } else {
            chartTitle = String.format("Data from %s to %s",
                    mFormatDateTime.format(new Date(convertXAxisValueToDate(mPlot.getData().getXMin()))),
                    mFormatDateTime.format(new Date(convertXAxisValueToDate(mPlot.getData().getXMax()))));
        }
        tv_plotTitle.setText(chartTitle);
    }

    private void updateChartViewConstrains() {
        mPlot.fitScreen();

        final int maxZoomFactor = 16;
        final float minGlucoseShown = convertGlucoseMGDLToDisplayUnit(20);
        final float maxGlucoseShown = minGlucoseShown * maxZoomFactor;

        mPlot.setVisibleYRangeMinimum(minGlucoseShown, mPlot.getAxisLeft().getAxisDependency());
        mPlot.setVisibleYRangeMaximum(maxGlucoseShown, mPlot.getAxisLeft().getAxisDependency());

        final float minMinutesShown = 30;
        final float maxMinutesShown = minMinutesShown * maxZoomFactor;

        mPlot.setVisibleXRangeMinimum(minMinutesShown);
        mPlot.setVisibleXRangeMaximum(maxMinutesShown);

        mPlot.moveViewTo(
                mPlot.getData().getXMax(),
                (mPlot.getData().getYMax() + mPlot.getData().getYMin()) / 2,
                mPlot.getAxisLeft().getAxisDependency()
        );

        mPlot.invalidate();
    }

    private LineDataSet makeLineData(List<GlucoseData> glucoseDataList, boolean isTrendData) {
        String title = "History";
        if (isTrendData) title = "Trend";

        LineDataSet lineDataSet = new LineDataSet(new ArrayList<Entry>(), title);
        for (GlucoseData gd : glucoseDataList) {
            float x = convertDateToXAxisValue(gd.date);
            float y = convertGlucoseRawToDisplayUnit(gd.glucoseLevelRaw);
            lineDataSet.addEntryOrdered(new Entry(x, y));
            /*
            Log.d(LOG_ID, String.format("%s: %s -> %s: %f -> %f",
                    title,
                    mFormatDateTime.format(new Date(gd.date)),
                    mFormatDateTime.format(new Date(convertXAxisValueToDate(x))),
                    x,
                    y)
            );
            */
        }

        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setDrawCircles(true);
        lineDataSet.setCircleRadius(2f);

        lineDataSet.setDrawCircleHole(false);
        lineDataSet.setDrawValues(false);

        lineDataSet.setDrawHighlightIndicators(true);

        int baseColor = PLOT_COLORS[mPlotColorIndex % NUM_PLOT_COLORS][0];
        int lineColor = Color.argb(150, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        int circleColor = PLOT_COLORS[mPlotColorIndex % NUM_PLOT_COLORS][1];
        if (isTrendData) {
            lineDataSet.setColor(lineColor);
            lineDataSet.setLineWidth(2f);

            lineDataSet.setCircleColor(circleColor);

            lineDataSet.setMode(LineDataSet.Mode.LINEAR);
        } else {
            lineDataSet.setColor(lineColor);
            lineDataSet.setLineWidth(4f);

            lineDataSet.setCircleColor(circleColor);

            lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            lineDataSet.setCubicIntensity(0.1f);
        }

        return lineDataSet;
    }

    private float convertDateToXAxisValue(long date) {
        return (date - mFirstDate) / TimeUnit.MINUTES.toMillis(1L);
    }

    private long convertXAxisValueToDate(float value) {
        return mFirstDate + (long) (value * TimeUnit.MINUTES.toMillis(1L));
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        Log.d(LOG_ID, "Selected: " + e.toString() + " : " +
                mFormatDateTime.format(new Date(convertXAxisValueToDate(e.getX()))));
    }

    @Override
    public void onNothingSelected() {

    }
}
