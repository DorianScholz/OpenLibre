package de.dorianscholz.openlibre.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import de.dorianscholz.openlibre.R;
import de.dorianscholz.openlibre.model.GlucoseData;
import de.dorianscholz.openlibre.model.PredictionData;
import de.dorianscholz.openlibre.model.ReadingData;

import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_TARGET_MAX;
import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_TARGET_MIN;
import static de.dorianscholz.openlibre.OpenLibre.GLUCOSE_UNIT_IS_MMOL;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.TREND_UP_DOWN_LIMIT;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDate;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatDateTime;
import static de.dorianscholz.openlibre.model.AlgorithmUtil.mFormatTimeShort;
import static de.dorianscholz.openlibre.model.GlucoseData.convertGlucoseMGDLToDisplayUnit;
import static de.dorianscholz.openlibre.model.GlucoseData.getDisplayUnit;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class DataPlotFragment extends Fragment implements OnChartValueSelectedListener, OnChartGestureListener {
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
    private Timer mUpdatePlotTitleTimer;
    private TimerTask mUpdatePlotTitleTask = null;
    private DateTimeMarkerView mDateTimeMarkerView;

    @SuppressWarnings("unused")
    public static DataPlotFragment newInstance() {
        return new DataPlotFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mDataPlotView = inflater.inflate(R.layout.fragment_scan, container, false);
        ((ProgressBar) mDataPlotView.findViewById(R.id.pb_scan_circle)).setProgress(0);
        mDataPlotView.findViewById(R.id.scan_progress).setVisibility(View.VISIBLE);
        mDataPlotView.findViewById(R.id.scan_view).setVisibility(View.INVISIBLE);

        mUpdatePlotTitleTimer = new Timer();

        setupPlot();

        clearScanData();

        return mDataPlotView;
    }
    
    @Override
    public void onDetach() {
        super.onDetach();
        if (mUpdatePlotTitleTask != null) {
            mUpdatePlotTitleTask.cancel();
        }
    }
    
    private void setupPlot() {
        mPlot = (LineChart) mDataPlotView.findViewById(R.id.cv_last_scan);
        mPlot.setNoDataText("");
        mPlot.setOnChartGestureListener(this);
        mDateTimeMarkerView = new DateTimeMarkerView(getContext(), R.layout.date_time_marker);
        mPlot.setMarkerView(mDateTimeMarkerView);

        // prevent scrolling in the plot to trigger switching of the view pager tabs
        mPlot.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        ViewPager viewPager = (ViewPager) getActivity().findViewById(R.id.view_pager);
                        viewPager.requestDisallowInterceptTouchEvent(true);
                        break;
                    }
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP: {
                        ViewPager viewPager = (ViewPager) getActivity().findViewById(R.id.view_pager);
                        viewPager.requestDisallowInterceptTouchEvent(false);
                        break;
                    }
                }
                return false;
            }
        });

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
        xAxis.setDrawGridLines(false);
        xAxis.setCenterAxisLabels(false);
        xAxis.setGranularity(convertDateToXAxisValue(TimeUnit.MINUTES.toMillis(5L))); // same unit as x axis values
        xAxis.setDrawLimitLinesBehindData(true);
        xAxis.setLabelCount(4);

        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                long date = convertXAxisValueToDate(value);
                return mFormatTimeShort.format(new Date(date));
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
                getResources().getString(R.string.pref_glucose_target_max)
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

    public void clearScanData() {
        mDataPlotView.findViewById(R.id.scan_data).setVisibility(View.INVISIBLE);
    }

    public void showMultipleScans(List<ReadingData> readingDataList) {
        mPlot.clear();
        mDataPlotView.findViewById(R.id.scan_progress).setVisibility(View.INVISIBLE);
        mDataPlotView.findViewById(R.id.scan_view).setVisibility(View.VISIBLE);

        for (ReadingData readingData : readingDataList) {
            addLineData(readingData.history, readingData.trend);
        }

        updatePlotTitle(false);
        updateChartViewConstrains();
        ((TextView) mDataPlotView.findViewById(R.id.tv_plot_date)).setText("");
    }

    void showHistory(List<GlucoseData> history, List<GlucoseData> trend) {
        mPlot.clear();
        mDataPlotView.findViewById(R.id.scan_progress).setVisibility(View.INVISIBLE);
        mDataPlotView.findViewById(R.id.scan_view).setVisibility(View.VISIBLE);

        updatePlot(history, trend);
    }

    void showScan(ReadingData readData) {
        mPlot.clear();
        mDataPlotView.findViewById(R.id.scan_progress).setVisibility(View.INVISIBLE);
        mDataPlotView.findViewById(R.id.scan_view).setVisibility(View.VISIBLE);

        updateScanData(readData.trend);
        updatePlot(readData.history, readData.trend);
    }

    private void updateScanData(List<GlucoseData> trend) {
        if (trend.size() == 0) {
            Toast.makeText(this.getContext(), "No current data available!", Toast.LENGTH_LONG).show();
            return;
        }

        mDataPlotView.findViewById(R.id.scan_data).setVisibility(View.VISIBLE);

        GlucoseData currentGlucose = trend.get(trend.size() - 1);
        TextView tv_currentGlucose = (TextView) mDataPlotView.findViewById(R.id.tv_glucose_current_value);
        tv_currentGlucose.setText(
                getResources().getString(R.string.glucose_current_value) +
                ": " +
                String.valueOf(currentGlucose.glucoseString()) +
                " " +
                getDisplayUnit()
        );

        PredictionData predictedGlucose = new PredictionData(trend);

        TextView tv_predictedGlucose = (TextView) mDataPlotView.findViewById(R.id.tv_glucose_prediction);
        tv_predictedGlucose.setText(String.valueOf(predictedGlucose.glucoseData.glucoseString()));
        tv_predictedGlucose.setAlpha((float) min(1, 0.1 + predictedGlucose.confidence()));

        ImageView iv_unit = (ImageView) mDataPlotView.findViewById(R.id.iv_unit);
        if (GLUCOSE_UNIT_IS_MMOL) {
            iv_unit.setImageResource(R.drawable.ic_unit_mmoll);
        } else {
            iv_unit.setImageResource(R.drawable.ic_unit_mgdl);
        }
        iv_unit.setAlpha((float) min(1, 0.1 + predictedGlucose.confidence()));

        ImageView iv_predictionArrow = (ImageView) mDataPlotView.findViewById(R.id.iv_glucose_prediction);

        // rotate trend arrow according to glucose prediction slope
        float rotationDegrees = -90f * max(-1f, min(1f, (float) (predictedGlucose.glucoseSlopeRaw / TREND_UP_DOWN_LIMIT)));
        iv_predictionArrow.setRotation(rotationDegrees);

        // reduce trend arrow visibility according to prediction confidence
        iv_predictionArrow.setAlpha((float) min(1, 0.1 + predictedGlucose.confidence()));
    }

    private void updatePlot(List<GlucoseData> history, List<GlucoseData> trend) {
        Log.d(LOG_ID, String.format("#history: %d, #trend: %d", history.size(), trend == null ? 0 : trend.size()));

        if (history.size() == 0) {
            Toast.makeText(this.getContext(), "No data available!", Toast.LENGTH_LONG).show();
            return;
        }

        addLineData(history, trend);

        updatePlotTitle(trend != null);
        updateChartViewConstrains();
        ((TextView) mDataPlotView.findViewById(R.id.tv_plot_date)).setText("");
    }

    private void addLineData(List<GlucoseData> history, List<GlucoseData> trend) {
        if (mFirstDate < 0) {
            mFirstDate = history.get(0).date;
            mDateTimeMarkerView.setFirstDate(mFirstDate);
        }

        LineData lineData = mPlot.getData();
        if (lineData == null) {
            lineData = new LineData();
        }
        lineData.addDataSet(makeLineData(history));
        if (trend != null) {
            // connect history and trend data
            List<GlucoseData> connection = new ArrayList<>();
            connection.add(history.get(history.size() - 1));
            connection.add(trend.get(0));
            lineData.addDataSet(makeLineData(connection));

            // show trend data
            lineData.addDataSet(makeLineData(trend));

            // also show regression data
            PredictionData predictionData = new PredictionData(trend);
            List<GlucoseData> prediction = predictionData.getPredictedData(
                    new int[]{trend.get(0).ageInSensorMinutes, trend.get(trend.size() - 1).ageInSensorMinutes});
            lineData.addDataSet(makeLineData(prediction));
        }
        mPlotColorIndex++;
        mPlot.setData(lineData);
    }

    private void setPlotTitleUpdateTimer() {
        // update 3 minutes after most recent data timestamp
        Date updateTime = new Date(3 * 60 * 1000 + convertXAxisValueToDate(mPlot.getData().getXMax()));
        if (mUpdatePlotTitleTask != null) {
            mUpdatePlotTitleTask.cancel();
        }
        mUpdatePlotTitleTask = new TimerTask() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            TextView tv_plotTitle = (TextView) mDataPlotView.findViewById(R.id.tv_plot_title);
                            tv_plotTitle.setTextColor(Color.RED);
                        }
                    });
                }
            }
        };
        mUpdatePlotTitleTimer.schedule(mUpdatePlotTitleTask, updateTime);
    }

    private void updatePlotTitle(boolean isScanData) {
        TextView tv_plotTitle = (TextView) mDataPlotView.findViewById(R.id.tv_plot_title);
        String plotTitle;
        if (isScanData) {
            plotTitle = mFormatDateTime.format(new Date(convertXAxisValueToDate(mPlot.getData().getXMax())));
        } else {
            plotTitle = String.format("Data from %s to %s",
                    mFormatDateTime.format(new Date(convertXAxisValueToDate(mPlot.getData().getXMin()))),
                    mFormatDateTime.format(new Date(convertXAxisValueToDate(mPlot.getData().getXMax()))));
        }
        tv_plotTitle.setTextColor(Color.BLACK);
        setPlotTitleUpdateTimer();
        tv_plotTitle.setText(plotTitle);
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

    private LineDataSet makeLineData(List<GlucoseData> glucoseDataList) {
        String title = "History";
        if (glucoseDataList.get(0).isTrendData) title = "Trend";

        LineDataSet lineDataSet = new LineDataSet(new ArrayList<Entry>(), title);
        for (GlucoseData gd : glucoseDataList) {
            float x = convertDateToXAxisValue(gd.date);
            float y = gd.glucose();
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
        int softColor = Color.argb(150, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        int hardColor = PLOT_COLORS[mPlotColorIndex % NUM_PLOT_COLORS][1];
        if (glucoseDataList.get(0).isTrendData) {
            lineDataSet.setColor(hardColor);
            lineDataSet.setLineWidth(2f);

            lineDataSet.setCircleColor(softColor);

            lineDataSet.setMode(LineDataSet.Mode.LINEAR);
        } else {
            lineDataSet.setColor(softColor);
            lineDataSet.setLineWidth(4f);

            lineDataSet.setCircleColor(hardColor);

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

    @Override
    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onChartLongPressed(MotionEvent me) {

    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {

    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {

    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {

    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {

    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {
        updatePlotDate();
    }

    public void updatePlotDate() {
        TextView plotTitle = (TextView) mDataPlotView.findViewById(R.id.tv_plot_date);
        if (convertXAxisValueToDate(mPlot.getData().getXMax()) -
                convertXAxisValueToDate(mPlot.getData().getXMin())
                > TimeUnit.HOURS.toMillis(12L)) {
            String minDate = mFormatDate.format(new Date(convertXAxisValueToDate(mPlot.getLowestVisibleX())));
            String maxDate = mFormatDate.format(new Date(convertXAxisValueToDate(mPlot.getHighestVisibleX())));
            if (minDate.compareTo(maxDate) == 0 || mPlot.getLowestVisibleX() > mPlot.getHighestVisibleX()) {
                plotTitle.setText(maxDate);
            } else {
                plotTitle.setText(minDate + " - " + maxDate);
            }
        } else {
            // no need to show the date, if showing less then 12 hours of data (e.g. a single scan)
            plotTitle.setText("");
        }
    }

}
