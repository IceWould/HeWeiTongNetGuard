package eu.faircode.netguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.lzf.easyfloat.EasyFloat;

import org.w3c.dom.Text;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static eu.faircode.netguard.ActivityMain.FLOAT_WINDOW_TAG;

public class TrafficChecker extends Service {
    private static long prevUsage = -1;
    private static long lastNotifyTime = 0;
    public static SharedPreferences prefs;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i( "kd", "This is  your alarm !");
        HashMap<Integer, Long> result = updateDataUsage(this.getApplicationContext());
        View view = EasyFloat.getAppFloatView(FLOAT_WINDOW_TAG);
        long totalUsage = 0;
        Set<Map.Entry<Integer, Long>> entrySet = result.entrySet();
        for(Map.Entry<Integer, Long> entry: entrySet){
            totalUsage += entry.getValue();
        }
        String string = humanReadableByteCountBin(totalUsage);
        String[] splited = string.split(" ");
        ((TextView) view.findViewById(R.id.fw_usage_number)).setText(splited[0]);
        if(splited[0].length() >= 3){
            ((TextView) view.findViewById(R.id.fw_usage_number)).setTextSize(30);
        }else{
            ((TextView) view.findViewById(R.id.fw_usage_number)).setTextSize(40);
        }
        ((TextView) view.findViewById(R.id.fw_usage_unit)).setText(splited[1]);
        System.out.println(string);

        long difUsage = totalUsage - prevUsage;

//        final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
//        WindowManager.LayoutParams para = new WindowManager.LayoutParams();
//        //设置弹窗的宽高
//        para.height = WindowManager.LayoutParams.WRAP_CONTENT;
//        para.width = WindowManager.LayoutParams.WRAP_CONTENT;
//        //期望的位图格式。默认为不透明
//        para.format = 1;
//        //当FLAG_DIM_BEHIND设置后生效。该变量指示后面的窗口变暗的程度。
//        //1.0表示完全不透明，0.0表示没有变暗。
//        para.dimAmount = 0.6f;
//        para.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
//        //设置为系统提示
//        para.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
//        //获取要显示的View
//        final View mView = LayoutInflater.from(getApplicationContext()).inflate(
//                R.layout.warning, null);
//        //单击View是关闭弹窗
//        Button confirmBtn = mView.findViewById(R.id.confirm);
//        confirmBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                wm.removeView(mView);
//            }
//        });
//        //显示弹窗
//        wm.addView(mView, para);





//        new AlertDialog.Builder(getApplicationContext())
//                .setTitle("Delete entry")
//                .setMessage("Are you sure you want to delete this entry?")
//
//                // Specifying a listener allows you to take an action before dismissing the dialog.
//                // The dialog is automatically dismissed when a dialog button is clicked.
//                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        // Continue with delete operation
//                    }
//                })
//
//                // A null listener allows the button to dismiss the dialog and take no further action.
//                .setNegativeButton(android.R.string.no, null)
//                .setIcon(android.R.drawable.ic_dialog_alert)
//                .show();

        if(prevUsage != -1 && difUsage > ((long)5 << 20)){
            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle("流量预警")
                    .setMessage("在过去一分钟内使用了"+ humanReadableByteCountBin(difUsage) +"流量！！！")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .setIcon(R.drawable.ic_security_color_24dp)
                    .create();

            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            alertDialog.show();
        }


        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isMobileConn = false;
        for (Network network : connMgr.getAllNetworks()) {
            NetworkInfo networkInfo = connMgr.getNetworkInfo(network);
            if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                isMobileConn |= networkInfo.isConnected();
            }
        }

        if(isMobileConn && lastNotifyTime < System.currentTimeMillis() - 1000 * 60 * 10 && prefs.getBoolean("enabled", false)){
            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle("连接警告")
                    .setMessage("已检测到陆地信号，请断开海洋wifi！")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .setIcon(R.drawable.ic_security_color_24dp)
                    .create();

            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            alertDialog.show();
            lastNotifyTime = System.currentTimeMillis();
        }

        prevUsage = totalUsage;
        return super.onStartCommand(intent, flags, startId);

    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public static HashMap<Integer, Long> updateDataUsage(Context context){
        NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);

        HashMap<Integer, Long> toReturn = new HashMap<Integer, Long>();

        NetworkStats summaryStats;
        NetworkStats.Bucket summaryBucket = new NetworkStats.Bucket();
        try {
            summaryStats = networkStatsManager.querySummary(ConnectivityManager.TYPE_WIFI, "", getTimesMorning(), System.currentTimeMillis());
            do {
                summaryStats.getNextBucket(summaryBucket);
                int summaryUid = summaryBucket.getUid();
                if (toReturn.containsKey(summaryUid)) {
                    toReturn.put(summaryUid, toReturn.get(summaryUid) + summaryBucket.getRxBytes() + summaryBucket.getTxBytes());
                }else{
                    toReturn.put(summaryUid, summaryBucket.getRxBytes() + summaryBucket.getTxBytes());
                }
            } while (summaryStats.hasNextBucket());
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return  toReturn;
    }


    public static String humanReadableByteCountBin(Long bytes) {
        if(bytes == null){
            return "0 KB";
        }
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return "0 KB";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        if(value / 1024.0 >= 10){
            return String.format(Locale.US, "%.0f %cB", value / 1024.0, ci.current());
        }
        return String.format(Locale.US, "%.1f %cB", value / 1024.0, ci.current());
    }



    public static long getTimesMorning(){
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
