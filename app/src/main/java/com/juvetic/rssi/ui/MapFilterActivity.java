package com.juvetic.rssi.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import com.juvetic.rssi.R;
import com.juvetic.rssi.model.AccessPoint;
import com.juvetic.rssi.util.ApComparator;
import com.juvetic.rssi.util.ToolUtil;
import com.juvetic.rssi.util.formulas.Formula;
import com.juvetic.rssi.util.helper.AssetsHelper;
import id.recharge.library.SVGMapView;
import id.recharge.library.SVGMapViewListener;
import id.recharge.library.overlay.SVGMapLocationOverlay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MapFilterActivity extends BaseActivity {

    public static final String EXTRA_FILTER = "extra_filter";

    private SVGMapView mapView;

    private List<AccessPoint> accessPointList = new ArrayList<>();

    WifiManager wifiManager;

    WifiScanReceiver wifiReceiver;

    AccessPoint accessPoint;

    SVGMapLocationOverlay locationOverlay, locationOverlayKalman1, locationOverlayKalman2, locationOverlayFeedback;

    double d1 = 0;

    double d2 = 0;

    double d3 = 0;

    String filter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_location);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new MapFilterActivity.WifiScanReceiver();
        wifiManager.startScan();

        mapView = findViewById(R.id.location_mapview);

        Intent intent = getIntent();
        filter = intent.getStringExtra(EXTRA_FILTER);

        if (filter.equals("kalman1")) {
            setTitle("Map - KF1");
        } else if (filter.equals("kalman2")) {
            setTitle("Map - KF2");
        } else if (filter.equals("feedback")) {
            setTitle("Map - Feedback");
        }
    }

    //78:8a:20:d4:a4:d8
    //78:8a:20:d4:a9:74
    //78:8a:20:d4:ac:28
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.map_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.map_menu_deploy:
                InformationDialog bottomSheetDialog = InformationDialog.getInstance();
                bottomSheetDialog.show(getSupportFragmentManager(), "Custom Bottom Sheet");
                return true;
            case R.id.map_menu_reload:
                wifiManager.startScan();
                return true;
//            case R.id.menu_filterby_kalman_type_a:
//                Toast.makeText(this, "Filter by KF Type A", Toast.LENGTH_SHORT).show();
//                item.setChecked(true);
//
//                d1 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_kalman_ap1_type_a"));
//                d2 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_kalman_ap2_type_a"));
//                d3 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_kalman_ap3_type_a"));
//                return true;
//            case R.id.menu_filterby_kalman_type_b:
//                Toast.makeText(this, "Filter by KF Type B", Toast.LENGTH_SHORT).show();
//                item.setChecked(true);
//
//                d1 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_kalman_ap1_type_b"));
//                d2 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_kalman_ap2_type_b"));
//                d3 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_kalman_ap3_type_b"));
//                return true;
//            case R.id.menu_filterby_feedback:
//                Toast.makeText(this, "Filter by Feedback", Toast.LENGTH_SHORT).show();
//                item.setChecked(true);
//
//                d1 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_feedback_ap1"));
//                d2 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_feedback_ap2"));
//                d3 = Double.parseDouble(ToolUtil.Storage.getValueString
//                        (MapFilterActivity.this, "dist_feedback_ap3"));
//                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    protected void onPause() {
        super.onPause();

        unregisterReceiver(wifiReceiver);
    }

    protected void onResume() {
        super.onResume();

        mapView.registerMapViewListener(new SVGMapViewListener() {
            @Override
            public void onGetCurrentMap(Bitmap bitmap) {
            }

            @Override
            public void onMapLoadComplete() {
                mapView.refresh();
            }

            @Override
            public void onMapLoadError() {
            }
        });
        mapView.loadMap(AssetsHelper.getContent(this, "hes_lab_v2.svg"));

        mapView.getController().sparkAtPoint(new PointF(Float.valueOf(x1), Float.valueOf(y1)), 20, Color.RED, 1000);
        mapView.getController().sparkAtPoint(new PointF(Float.valueOf(x2), Float.valueOf(y2)), 20, Color.GREEN, 1000);
        mapView.getController().sparkAtPoint(new PointF(Float.valueOf(x3), Float.valueOf(y3)), 20, Color.BLUE, 1000);
        mapView.getController().setScrollGestureEnabled(false);
        mapView.getController().setZoomGestureEnabled(false);

        registerReceiver(
                wifiReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );
    }

    class WifiScanReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            accessPointList.clear();

            List<ScanResult> scanResultList = wifiManager.getScanResults();
            if (scanResultList != null) {
                for (ScanResult scanResult : scanResultList) {
                    int level = WifiManager.calculateSignalLevel(scanResult.level, 4);

                    switch (scanResult.BSSID) {
                        //b6:e6:2d:23:84:90
                        //60:de:f3:03:60:30 SBK Group
                        //78:8a:20:d4:ac:28 Cocowork
                        case "b6:e6:2d:23:84:90": //AP1
                            // KF Type A
                            rssiKFQueueAp1 = tinydb.getQueueDouble("rssi_kalman_list_ap1");
                            rssiKFQueueAp1.add((double) scanResult.level);
                            tinydb.putQueueDouble("rssi_kalman_list_ap1", rssiKFQueueAp1);

                            if (iAp1 == 0) {
                                // KF Type A
                                kfAlgoAp1TypeA = Formula.applyKFAlgorithmTypeA(rssiKFQueueAp1, 1, Double.parseDouble
                                        (ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                variansiAp1TypeA = kfAlgoAp1TypeA.get(4);

                                // KF Type B
                                kfAlgoAp1TypeB = Formula.applyKFAlgorithmTypeB(rssiKFQueueAp1, preRssiAp1KFTypeB, 1,
                                        Double.parseDouble(
                                                ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                preRssiAp1KFTypeB = kfAlgoAp1TypeB.get(3);
                                variansiAp1TypeB = kfAlgoAp1TypeB.get(4);

                                // Feedback
                                fbAlgoAp1 = Formula.applyFeedbackFilterAlgorithm(0, (double) scanResult.level,
                                        Double.parseDouble(ToolUtil.Storage
                                                .getValueString(MapFilterActivity.this, "alpha")));
                            } else {
                                // KF Type A
                                kfAlgoAp1TypeA = Formula.applyKFAlgorithmTypeA(rssiKFQueueAp1, variansiAp1TypeA,
                                        Double.parseDouble(
                                                ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                variansiAp1TypeA = kfAlgoAp1TypeA.get(4);

                                // KF Type B
                                kfAlgoAp1TypeB = Formula
                                        .applyKFAlgorithmTypeB(rssiKFQueueAp1, preRssiAp1KFTypeB, variansiAp1TypeB,
                                                Double.parseDouble(
                                                        ToolUtil.Storage
                                                                .getValueString(MapFilterActivity.this, "noise")));
                                preRssiAp1KFTypeB = kfAlgoAp1TypeB.get(3);
                                variansiAp1TypeB = kfAlgoAp1TypeB.get(4);

                                // Feedback
                                fbAlgoAp1 = Formula.applyFeedbackFilterAlgorithm(kfAlgoAp1TypeA.get(0),
                                        (double) scanResult.level,
                                        Double.parseDouble(ToolUtil.Storage
                                                .getValueString(MapFilterActivity.this, "alpha")));
                            }
                            iAp1 += 1;

                            rssiListAp1.add((double) scanResult.level);
                            rssiKFListAp1.add(kfAlgoAp1TypeA.get(3));

                            ToolUtil.Storage.setValueInt(MapFilterActivity.this, "i_kalman_ap1",
                                    iAp1);
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "pre_rssi_ap1",
                                    String.valueOf(preRssiAp1KFTypeB));

                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "rssi_kalman_ap1_type_a",
                                    String.valueOf(kfAlgoAp1TypeA.get(3)));
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "var_kalman_ap1_type_a",
                                    String.valueOf(variansiAp1TypeA));

                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "rssi_kalman_ap1_type_b",
                                    String.valueOf(kfAlgoAp1TypeB.get(3)));
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "var_kalman_ap1_type_b",
                                    String.valueOf(variansiAp1TypeB));

                            accessPoint = new AccessPoint(
                                    scanResult.SSID,
                                    String.valueOf(scanResult.level) + " dBm",
                                    String.valueOf(scanResult.frequency) + " MHz",
                                    scanResult.capabilities,
                                    Formula.distance((double) scanResult.level, Double.parseDouble(ToolUtil.Storage
                                            .getValueString(MapFilterActivity.this, "n"))),
                                    String.valueOf(level),
                                    scanResult.BSSID,
                                    String.valueOf(kfAlgoAp1TypeA.get(3)) + " dBm",
                                    String.valueOf(kfAlgoAp1TypeB.get(3)) + " dBm",
                                    String.valueOf(fbAlgoAp1.get(3)) + " dBm",
                                    Formula.distance
                                            (kfAlgoAp1TypeA.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n"))),
                                    Formula.distance
                                            (kfAlgoAp1TypeB.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n"))),
                                    Formula.distance(
                                            fbAlgoAp1.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n")))
                            );
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_kalman_ap1_type_a",
                                    accessPoint.getDistanceKalmanTypeA());
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_kalman_ap1_type_b",
                                    accessPoint.getDistanceKalmanTypeB());
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_feedback_ap1",
                                    accessPoint.getDistanceFeedback());
                            accessPointList.add(accessPoint);
                            break;
                        //78:8a:20:d4:a4:d8
                        //6a:c6:3a:d6:9c:92
                        case "6a:c6:3a:d6:9c:92": //2
                            rssiKFQueueAp2 = tinydb.getQueueDouble("rssi_kalman_list_ap2");
                            rssiKFQueueAp2.add((double) scanResult.level);
                            tinydb.putQueueDouble("rssi_kalman_list_ap2", rssiKFQueueAp2);

                            if (iAp2 == 0) {
                                // KF Type A
                                kfAlgoAp2TypeA = Formula.applyKFAlgorithmTypeA(rssiKFQueueAp2, 1,
                                        Double.parseDouble(
                                                ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                variansiAp2TypeA = kfAlgoAp2TypeA.get(4);

                                // KF Type B
                                kfAlgoAp2TypeB = Formula.applyKFAlgorithmTypeB(rssiKFQueueAp2, preRssiAp2KFTypeB, 1,
                                        Double.parseDouble(
                                                ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                preRssiAp2KFTypeB = kfAlgoAp2TypeB.get(3);
                                variansiAp2TypeB = kfAlgoAp2TypeB.get(4);

                                // Feedback
                                fbAlgoAp2 = Formula.applyFeedbackFilterAlgorithm(0, (double) scanResult.level,
                                        Double.parseDouble(ToolUtil.Storage
                                                .getValueString(MapFilterActivity.this, "alpha")));
                            } else {
                                // KF Type A
                                kfAlgoAp2TypeA = Formula.applyKFAlgorithmTypeA(rssiKFQueueAp2, variansiAp2TypeA,
                                        Double.parseDouble(
                                                ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                variansiAp2TypeA = kfAlgoAp2TypeA.get(4);

                                // KF Type B
                                kfAlgoAp2TypeB = Formula
                                        .applyKFAlgorithmTypeB(rssiKFQueueAp2, preRssiAp2KFTypeB, variansiAp2TypeB,
                                                Double.parseDouble(
                                                        ToolUtil.Storage
                                                                .getValueString(MapFilterActivity.this, "noise")));
                                preRssiAp2KFTypeB = kfAlgoAp2TypeB.get(3);
                                variansiAp2TypeB = kfAlgoAp2TypeB.get(4);

                                // Feedback
                                fbAlgoAp2 = Formula.applyFeedbackFilterAlgorithm(kfAlgoAp2TypeA.get(0),
                                        (double) scanResult.level,
                                        Double.parseDouble(ToolUtil.Storage
                                                .getValueString(MapFilterActivity.this, "alpha")));
                            }
                            iAp2 += 1;

                            rssiListAp2.add((double) scanResult.level);
                            rssiKFListAp2.add(kfAlgoAp2TypeA.get(3));

                            ToolUtil.Storage.setValueInt(MapFilterActivity.this, "i_kalman_ap2", iAp2);
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "pre_rssi_ap2",
                                    String.valueOf(preRssiAp2KFTypeB));

                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "rssi_kalman_ap2_type_a",
                                    String.valueOf(kfAlgoAp2TypeA.get(3)));
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "var_kalman_ap2_type_a",
                                    String.valueOf(variansiAp2TypeA));

                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "rssi_kalman_ap2_type_b",
                                    String.valueOf(kfAlgoAp2TypeB.get(3)));
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "var_kalman_ap2_type_b",
                                    String.valueOf(variansiAp2TypeB));

                            accessPoint = new AccessPoint(
                                    scanResult.SSID,
                                    String.valueOf(scanResult.level) + " dBm",
                                    String.valueOf(scanResult.frequency) + " MHz",
                                    scanResult.capabilities,
                                    Formula.distance((double) scanResult.level, Double.parseDouble(ToolUtil.Storage
                                            .getValueString(MapFilterActivity.this, "n"))),
                                    String.valueOf(level),
                                    scanResult.BSSID,
                                    String.valueOf(kfAlgoAp2TypeA.get(3)) + " dBm",
                                    String.valueOf(kfAlgoAp2TypeB.get(3)) + " dBm",
                                    String.valueOf(fbAlgoAp2.get(3)) + " dBm",
                                    Formula.distance
                                            (kfAlgoAp2TypeA.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n"))),
                                    Formula.distance
                                            (kfAlgoAp2TypeB.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n"))),
                                    Formula.distance(
                                            fbAlgoAp2.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n")))
                            );

                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_kalman_ap2_type_a",
                                    accessPoint.getDistanceKalmanTypeA());
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_kalman_ap2_type_b",
                                    accessPoint.getDistanceKalmanTypeB());
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_feedback_ap2",
                                    accessPoint.getDistanceFeedback());

                            accessPointList.add(accessPoint);
                            break;
                        // 78:8a:20:d4:a9:74
                        // be:dd:c2:fe:3b:0b
                        case "be:dd:c2:fe:3b:0b": //AP3
                            rssiKFQueueAp3 = tinydb.getQueueDouble("rssi_kalman_list_ap3");
                            rssiKFQueueAp3.add((double) scanResult.level);
                            tinydb.putQueueDouble("rssi_kalman_list_ap3", rssiKFQueueAp3);

                            if (iAp3 == 0) {
                                // KF Type A
                                kfAlgoAp3TypeA = Formula.applyKFAlgorithmTypeA(rssiKFQueueAp3, 1,
                                        Double.parseDouble(
                                                ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                variansiAp3TypeA = kfAlgoAp3TypeA.get(4);

                                // KF Type B
                                kfAlgoAp3TypeB = Formula.applyKFAlgorithmTypeB(rssiKFQueueAp3, preRssiAp3KFTypeB, 1,
                                        Double.parseDouble(
                                                ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                preRssiAp3KFTypeB = kfAlgoAp3TypeB.get(3);
                                variansiAp3TypeB = kfAlgoAp3TypeB.get(4);

                                // Feedback
                                fbAlgoAp3 = Formula.applyFeedbackFilterAlgorithm(0, (double) scanResult.level,
                                        Double.parseDouble(ToolUtil.Storage
                                                .getValueString(MapFilterActivity.this, "alpha")));
                            } else {
                                // KF Type A
                                kfAlgoAp3TypeA = Formula.applyKFAlgorithmTypeA(rssiKFQueueAp3, variansiAp3TypeA,
                                        Double.parseDouble(
                                                ToolUtil.Storage.getValueString(MapFilterActivity.this, "noise")));
                                variansiAp3TypeA = kfAlgoAp3TypeA.get(4);

                                // KF Type B
                                kfAlgoAp3TypeB = Formula
                                        .applyKFAlgorithmTypeB(rssiKFQueueAp3, preRssiAp3KFTypeB, variansiAp3TypeB,
                                                Double.parseDouble(
                                                        ToolUtil.Storage
                                                                .getValueString(MapFilterActivity.this, "noise")));
                                preRssiAp3KFTypeB = kfAlgoAp3TypeB.get(3);
                                variansiAp3TypeB = kfAlgoAp3TypeB.get(4);

                                // Feedback
                                fbAlgoAp3 = Formula.applyFeedbackFilterAlgorithm(kfAlgoAp3TypeA.get(0),
                                        (double) scanResult.level,
                                        Double.parseDouble(ToolUtil.Storage
                                                .getValueString(MapFilterActivity.this, "alpha")));
                            }
                            iAp3 += 1;

                            rssiListAp3.add((double) scanResult.level);
                            rssiKFListAp3.add(kfAlgoAp3TypeA.get(3));

                            ToolUtil.Storage.setValueInt(MapFilterActivity.this, "i_kalman_ap3", iAp3);
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "pre_rssi_ap3",
                                    String.valueOf(preRssiAp3KFTypeB));

                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "rssi_kalman_ap3_type_a",
                                    String.valueOf(kfAlgoAp3TypeA.get(3)));
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "var_kalman_ap3_type_a",
                                    String.valueOf(variansiAp3TypeA));

                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "rssi_kalman_ap3_type_b",
                                    String.valueOf(kfAlgoAp3TypeB.get(3)));
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "var_kalman_ap3_type_b",
                                    String.valueOf(variansiAp3TypeB));

                            accessPoint = new AccessPoint(
                                    scanResult.SSID,
                                    String.valueOf(scanResult.level) + " dBm",
                                    String.valueOf(scanResult.frequency) + " MHz",
                                    scanResult.capabilities,
                                    Formula.distance((double) scanResult.level, Double.parseDouble(ToolUtil.Storage
                                            .getValueString(MapFilterActivity.this, "n"))),
                                    String.valueOf(level),
                                    scanResult.BSSID,
                                    String.valueOf(kfAlgoAp3TypeA.get(3)) + " dBm",
                                    String.valueOf(kfAlgoAp3TypeB.get(3)) + " dBm",
                                    String.valueOf(fbAlgoAp3.get(3)) + " dBm",
                                    Formula.distance
                                            (kfAlgoAp3TypeA.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n"))),
                                    Formula.distance
                                            (kfAlgoAp3TypeB.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n"))),
                                    Formula.distance(
                                            fbAlgoAp3.get(3), Double.parseDouble(ToolUtil.Storage
                                                    .getValueString(MapFilterActivity.this, "n")))
                            );

                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_kalman_ap3_type_a",
                                    accessPoint.getDistanceKalmanTypeA());
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_kalman_ap3_type_b",
                                    accessPoint.getDistanceKalmanTypeB());
                            ToolUtil.Storage.setValueString(MapFilterActivity.this, "dist_feedback_ap3",
                                    accessPoint.getDistanceFeedback());

                            accessPointList.add(accessPoint);
                            break;
                        default:
                            accessPoint = new AccessPoint(
                                    scanResult.SSID,
                                    String.valueOf(scanResult.level) + " dBm",
                                    String.valueOf(scanResult.frequency) + " MHz",
                                    scanResult.capabilities,
                                    Formula.distance((double) scanResult.level, Double.parseDouble(ToolUtil.Storage
                                            .getValueString(MapFilterActivity.this, "n"))),
                                    String.valueOf(level),
                                    scanResult.BSSID,
                                    "0 dBm",
                                    "0 dBm",
                                    "0 dBm",
                                    "0",
                                    "0",
                                    "0"
                            );
                            accessPointList.add(accessPoint);
                            break;
                    }
                }

                switch (filter) {
                    case "kalman1":
                        d1 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_kalman_ap1_type_a"));
                        d2 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_kalman_ap2_type_a"));
                        d3 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_kalman_ap3_type_a"));
                        break;
                    case "kalman2":
                        d1 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_kalman_ap1_type_b"));
                        d2 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_kalman_ap2_type_b"));
                        d3 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_kalman_ap3_type_b"));
                        break;
                    case "feedback":
                        d1 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_feedback_ap1"));
                        d2 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_feedback_ap2"));
                        d3 = Double.parseDouble(ToolUtil.Storage.getValueString
                                (MapFilterActivity.this, "dist_feedback_ap3"));
                        break;
                }

                xy = Formula.koordinat(
                        Double.valueOf(x1), Double.valueOf(y1), d1,
                        Double.valueOf(x2), Double.valueOf(y2), d2,
                        Double.valueOf(x3), Double.valueOf(y3), d3);
                ToolUtil.Storage
                        .setValueString(MapFilterActivity.this, "d1_filter", String.valueOf(d1));
                ToolUtil.Storage
                        .setValueString(MapFilterActivity.this, "d2_filter", String.valueOf(d2));
                ToolUtil.Storage
                        .setValueString(MapFilterActivity.this, "d3_filter", String.valueOf(d3));

                ToolUtil.Storage
                        .setValueString(MapFilterActivity.this, "xPos_filter",
                                String.valueOf(Math.round(xy.get(0))));
                ToolUtil.Storage
                        .setValueString(MapFilterActivity.this, "yPos_filter",
                                String.valueOf(Math.round(xy.get(1))));

                xPos = ToolUtil.Storage.getValueString(MapFilterActivity.this, "xPos_filter");
                yPos = ToolUtil.Storage.getValueString(MapFilterActivity.this, "yPos_filter");

                mapView.getOverLays().remove(locationOverlay);

                float x_smooth = Float.valueOf(xPos);
                if (x_smooth < MIN_X) {
                    x_smooth = MIN_X;
                } else if (x_smooth > MAX_X) {
                    x_smooth = MAX_X;
                }

                float y_smooth = Float.valueOf(yPos);
                if (y_smooth < MIN_Y) {
                    y_smooth = MIN_Y;
                } else if (y_smooth > MAX_Y) {
                    y_smooth = MAX_Y;
                }

                switch (filter) {
                    case "kalman1":
                        locationOverlay = new SVGMapLocationOverlay(mapView, "kalman1");
                        locationOverlay.setPosition(
                                new PointF(x_smooth, y_smooth));
                        break;
                    case "kalman2":
                        locationOverlay = new SVGMapLocationOverlay(mapView, "kalman2");
                        locationOverlay.setPosition(
                                new PointF(x_smooth, y_smooth));
                        break;
                    case "feedback":
                        locationOverlay = new SVGMapLocationOverlay(mapView, "feedback");
                        locationOverlay.setPosition(
                                new PointF(x_smooth, y_smooth));
                        break;
                    default:
                        locationOverlay = new SVGMapLocationOverlay(mapView, "default");
                        locationOverlay.setPosition(
                                new PointF(x_smooth, y_smooth));
                        break;
                }

                mapView.getOverLays().add(locationOverlay);
                mapView.refresh();
            }

            Collections.sort(accessPointList, new ApComparator());

            wifiManager.startScan();
        }
    }
}