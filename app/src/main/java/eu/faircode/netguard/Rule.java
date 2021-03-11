package eu.faircode.netguard;

/*
    This file is part of NetGuard.

    NetGuard is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2015-2019 by Marcel Bokhorst (M66B)
*/

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import org.xmlpull.v1.XmlPullParser;

import java.text.CharacterIterator;
import java.text.Collator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static android.content.Context.TELEPHONY_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

public class Rule {
    private static final String TAG = "NetGuard.Rule";

    public int uid;
    public String packageName;
    public int icon;
    public String name;
    public String usage;
    public String version;
    public boolean system;
    public boolean internet;
    public boolean enabled;
    public boolean pkg = true;

    public boolean wifi_default = false;
    public boolean other_default = false;
    public boolean screen_wifi_default = false;
    public boolean screen_other_default = false;
    public boolean roaming_default = false;

    public boolean wifi_blocked = false;
    public boolean other_blocked = false;
    public boolean screen_wifi = false;
    public boolean screen_other = false;
    public boolean roaming = false;
    public boolean lockdown = false;

    public boolean apply = true;
    public boolean notify = true;

    public boolean relateduids = false;
    public String[] related = null;

    public long hosts;
    public boolean changed;

    public boolean expanded = false;

    private static List<PackageInfo> cachePackageInfo = null;
    private static Map<PackageInfo, String> cacheLabel = new HashMap<>();
    private static Map<String, Boolean> cacheSystem = new HashMap<>();
    private static Map<String, Boolean> cacheInternet = new HashMap<>();
    private static Map<PackageInfo, Boolean> cacheEnabled = new HashMap<>();

    private static List<PackageInfo> getPackages(Context context) {
        if (cachePackageInfo == null) {
            PackageManager pm = context.getPackageManager();
            cachePackageInfo = pm.getInstalledPackages(0);
        }
        return new ArrayList<>(cachePackageInfo);
    }

    private static String getLabel(PackageInfo info, Context context) {
        if (!cacheLabel.containsKey(info)) {
            PackageManager pm = context.getPackageManager();
            cacheLabel.put(info, info.applicationInfo.loadLabel(pm).toString());
        }
        return cacheLabel.get(info);
    }

    private static boolean isSystem(String packageName, Context context) {
        if (!cacheSystem.containsKey(packageName))
            cacheSystem.put(packageName, Util.isSystem(packageName, context));
        return cacheSystem.get(packageName);
    }

    private static boolean hasInternet(String packageName, Context context) {
        if (!cacheInternet.containsKey(packageName))
            cacheInternet.put(packageName, Util.hasInternet(packageName, context));
        return cacheInternet.get(packageName);
    }

    private static boolean isEnabled(PackageInfo info, Context context) {
        if (!cacheEnabled.containsKey(info))
            cacheEnabled.put(info, Util.isEnabled(info, context));
        return cacheEnabled.get(info);
    }

    public static void clearCache(Context context) {
        Log.i(TAG, "Clearing cache");
        synchronized (context.getApplicationContext()) {
            cachePackageInfo = null;
            cacheLabel.clear();
            cacheSystem.clear();
            cacheInternet.clear();
            cacheEnabled.clear();
        }

        DatabaseHelper dh = DatabaseHelper.getInstance(context);
        dh.clearApps();
    }

    private Rule(DatabaseHelper dh, PackageInfo info, Context context) {
        this.uid = info.applicationInfo.uid;
        this.packageName = info.packageName;
        this.icon = info.applicationInfo.icon;
        this.version = info.versionName;
        if (info.applicationInfo.uid == 0) {
            this.name = context.getString(R.string.title_root);
            this.system = true;
            this.internet = true;
            this.enabled = true;
            this.pkg = false;
        } else if (info.applicationInfo.uid == 1013) {
            this.name = context.getString(R.string.title_mediaserver);
            this.system = true;
            this.internet = true;
            this.enabled = true;
            this.pkg = false;
        } else if (info.applicationInfo.uid == 1020) {
            this.name = "MulticastDNSResponder";
            this.system = true;
            this.internet = true;
            this.enabled = true;
            this.pkg = false;
        } else if (info.applicationInfo.uid == 1021) {
            this.name = context.getString(R.string.title_gpsdaemon);
            this.system = true;
            this.internet = true;
            this.enabled = true;
            this.pkg = false;
        } else if (info.applicationInfo.uid == 1051) {
            this.name = context.getString(R.string.title_dnsdaemon);
            this.system = true;
            this.internet = true;
            this.enabled = true;
            this.pkg = false;
        } else if (info.applicationInfo.uid == 9999) {
            this.name = context.getString(R.string.title_nobody);
            this.system = true;
            this.internet = true;
            this.enabled = true;
            this.pkg = false;
        } else {
            Cursor cursor = null;
            try {
                cursor = dh.getApp(this.packageName);
                if (cursor.moveToNext()) {
                    this.name = cursor.getString(cursor.getColumnIndex("label"));
                    this.system = cursor.getInt(cursor.getColumnIndex("system")) > 0;
                    this.internet = cursor.getInt(cursor.getColumnIndex("internet")) > 0;
                    this.enabled = cursor.getInt(cursor.getColumnIndex("enabled")) > 0;
                } else {
                    this.name = getLabel(info, context);
                    this.system = isSystem(info.packageName, context);
                    this.internet = hasInternet(info.packageName, context);
                    this.enabled = isEnabled(info, context);

                    dh.addApp(this.packageName, this.name, this.system, this.internet, this.enabled);
                }
            } finally {
                if (cursor != null)
                    cursor.close();
            }
        }
    }

    public static List<Rule> getRules(final boolean all, Context context) {
        synchronized (context.getApplicationContext()) {
            HashMap<Integer, Long> dataUsage = new HashMap<>();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                dataUsage = Rule.updateDataUsage(context);
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences wifi = context.getSharedPreferences("wifi", Context.MODE_PRIVATE);
            SharedPreferences other = context.getSharedPreferences("other", Context.MODE_PRIVATE);
            SharedPreferences screen_wifi = context.getSharedPreferences("screen_wifi", Context.MODE_PRIVATE);
            SharedPreferences screen_other = context.getSharedPreferences("screen_other", Context.MODE_PRIVATE);
            SharedPreferences roaming = context.getSharedPreferences("roaming", Context.MODE_PRIVATE);
            SharedPreferences lockdown = context.getSharedPreferences("lockdown", Context.MODE_PRIVATE);
            SharedPreferences apply = context.getSharedPreferences("apply", Context.MODE_PRIVATE);
            SharedPreferences notify = context.getSharedPreferences("notify", Context.MODE_PRIVATE);

            // Get settings
            boolean default_wifi = prefs.getBoolean("whitelist_wifi", true);
            boolean default_other = prefs.getBoolean("whitelist_other", true);
            boolean default_screen_wifi = prefs.getBoolean("screen_wifi", false);
            boolean default_screen_other = prefs.getBoolean("screen_other", false);
            boolean default_roaming = prefs.getBoolean("whitelist_roaming", true);

            boolean manage_system = prefs.getBoolean("manage_system", false);
            boolean screen_on = prefs.getBoolean("screen_on", true);
            boolean show_user = prefs.getBoolean("show_user", true);
            boolean show_system = prefs.getBoolean("show_system", false);
            boolean show_nointernet = prefs.getBoolean("show_nointernet", true);
            boolean show_disabled = prefs.getBoolean("show_disabled", true);

            default_screen_wifi = default_screen_wifi && screen_on;
            default_screen_other = default_screen_other && screen_on;

            // Get predefined rules
            Map<String, Boolean> pre_wifi_blocked = new HashMap<>();
            Map<String, Boolean> pre_other_blocked = new HashMap<>();
            Map<String, Boolean> pre_roaming = new HashMap<>();
            Map<String, String[]> pre_related = new HashMap<>();
            Map<String, Boolean> pre_system = new HashMap<>();
            try {
                XmlResourceParser xml = context.getResources().getXml(R.xml.predefined);
                int eventType = xml.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG)
                        if ("wifi".equals(xml.getName())) {
                            String pkg = xml.getAttributeValue(null, "package");
                            boolean pblocked = xml.getAttributeBooleanValue(null, "blocked", false);
                            pre_wifi_blocked.put(pkg, pblocked);

                        } else if ("other".equals(xml.getName())) {
                            String pkg = xml.getAttributeValue(null, "package");
                            boolean pblocked = xml.getAttributeBooleanValue(null, "blocked", false);
                            boolean proaming = xml.getAttributeBooleanValue(null, "roaming", default_roaming);
                            pre_other_blocked.put(pkg, pblocked);
                            pre_roaming.put(pkg, proaming);

                        } else if ("relation".equals(xml.getName())) {
                            String pkg = xml.getAttributeValue(null, "package");
                            String[] rel = xml.getAttributeValue(null, "related").split(",");
                            pre_related.put(pkg, rel);

                        } else if ("type".equals(xml.getName())) {
                            String pkg = xml.getAttributeValue(null, "package");
                            boolean system = xml.getAttributeBooleanValue(null, "system", true);
                            pre_system.put(pkg, system);
                        }


                    eventType = xml.next();
                }
            } catch (Throwable ex) {
                Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }

            // Build rule list
            List<Rule> listRules = new ArrayList<>();
            List<PackageInfo> listPI = getPackages(context);

            int userId = Process.myUid() / 100000;

            // Add root
            PackageInfo root = new PackageInfo();
            root.packageName = "root";
            root.versionCode = Build.VERSION.SDK_INT;
            root.versionName = Build.VERSION.RELEASE;
            root.applicationInfo = new ApplicationInfo();
            root.applicationInfo.uid = 0;
            root.applicationInfo.icon = 0;
            listPI.add(root);

            // Add mediaserver
            PackageInfo media = new PackageInfo();
            media.packageName = "android.media";
            media.versionCode = Build.VERSION.SDK_INT;
            media.versionName = Build.VERSION.RELEASE;
            media.applicationInfo = new ApplicationInfo();
            media.applicationInfo.uid = 1013 + userId * 100000;
            media.applicationInfo.icon = 0;
            listPI.add(media);

            // MulticastDNSResponder
            PackageInfo mdr = new PackageInfo();
            mdr.packageName = "android.multicast";
            mdr.versionCode = Build.VERSION.SDK_INT;
            mdr.versionName = Build.VERSION.RELEASE;
            mdr.applicationInfo = new ApplicationInfo();
            mdr.applicationInfo.uid = 1020 + userId * 100000;
            mdr.applicationInfo.icon = 0;
            listPI.add(mdr);

            // Add GPS daemon
            PackageInfo gps = new PackageInfo();
            gps.packageName = "android.gps";
            gps.versionCode = Build.VERSION.SDK_INT;
            gps.versionName = Build.VERSION.RELEASE;
            gps.applicationInfo = new ApplicationInfo();
            gps.applicationInfo.uid = 1021 + userId * 100000;
            gps.applicationInfo.icon = 0;
            listPI.add(gps);

            // Add DNS daemon
            PackageInfo dns = new PackageInfo();
            dns.packageName = "android.dns";
            dns.versionCode = Build.VERSION.SDK_INT;
            dns.versionName = Build.VERSION.RELEASE;
            dns.applicationInfo = new ApplicationInfo();
            dns.applicationInfo.uid = 1051 + userId * 100000;
            dns.applicationInfo.icon = 0;
            listPI.add(dns);

            // Add nobody
            PackageInfo nobody = new PackageInfo();
            nobody.packageName = "nobody";
            nobody.versionCode = Build.VERSION.SDK_INT;
            nobody.versionName = Build.VERSION.RELEASE;
            nobody.applicationInfo = new ApplicationInfo();
            nobody.applicationInfo.uid = 9999;
            nobody.applicationInfo.icon = 0;
            listPI.add(nobody);

            DatabaseHelper dh = DatabaseHelper.getInstance(context);
            for (PackageInfo info : listPI)
                try {
                    // Skip self
                    if (info.applicationInfo.uid == Process.myUid())
                        continue;


                    Rule rule = new Rule(dh, info, context);

                    if (pre_system.containsKey(info.packageName))
                        rule.system = pre_system.get(info.packageName);
                    if (info.applicationInfo.uid == Process.myUid())
                        rule.system = true;

                    if (all ||
                            ((rule.system ? show_system : show_user) &&
                                    (show_nointernet || rule.internet) &&
                                    (show_disabled || rule.enabled))) {

                        rule.wifi_default = (pre_wifi_blocked.containsKey(info.packageName) ? pre_wifi_blocked.get(info.packageName) : default_wifi);
                        rule.other_default = (pre_other_blocked.containsKey(info.packageName) ? pre_other_blocked.get(info.packageName) : default_other);
                        rule.screen_wifi_default = default_screen_wifi;
                        rule.screen_other_default = default_screen_other;
                        rule.roaming_default = (pre_roaming.containsKey(info.packageName) ? pre_roaming.get(info.packageName) : default_roaming);

                        rule.wifi_blocked = (!(rule.system && !manage_system) && wifi.getBoolean(info.packageName, rule.wifi_default));
                        rule.other_blocked = (!(rule.system && !manage_system) && other.getBoolean(info.packageName, rule.other_default));
                        rule.screen_wifi = screen_wifi.getBoolean(info.packageName, rule.screen_wifi_default) && screen_on;
                        rule.screen_other = screen_other.getBoolean(info.packageName, rule.screen_other_default) && screen_on;
                        rule.roaming = roaming.getBoolean(info.packageName, rule.roaming_default);
                        rule.lockdown = lockdown.getBoolean(info.packageName, false);

                        rule.apply = apply.getBoolean(info.packageName, true);
                        rule.notify = notify.getBoolean(info.packageName, true);

                        // Related packages
                        List<String> listPkg = new ArrayList<>();
                        if (pre_related.containsKey(info.packageName))
                            listPkg.addAll(Arrays.asList(pre_related.get(info.packageName)));
                        for (PackageInfo pi : listPI)
                            if (pi.applicationInfo.uid == rule.uid && !pi.packageName.equals(rule.packageName)) {
                                rule.relateduids = true;
                                listPkg.add(pi.packageName);
                            }
                        rule.related = listPkg.toArray(new String[0]);

                        rule.hosts = dh.getHostCount(rule.uid, true);
                        if(dataUsage.containsKey(rule.uid)){
                            rule.usage = humanReadableByteCountBin(dataUsage.get(rule.uid));
                        }else{
                            rule.usage = "0.0 KB";
                        }
                        rule.updateChanged(default_wifi, default_other, default_roaming);

                        listRules.add(rule);
                    }
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }

            // Sort rule list
            final Collator collator = Collator.getInstance(Locale.getDefault());
            collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

            String sort = prefs.getString("sort", "name");
            if ("uid".equals(sort))
                Collections.sort(listRules, new Comparator<Rule>() {
                    @Override
                    public int compare(Rule rule, Rule other) {
                        if (rule.uid < other.uid)
                            return -1;
                        else if (rule.uid > other.uid)
                            return 1;
                        else {
                            int i = collator.compare(rule.name, other.name);
                            return (i == 0 ? rule.packageName.compareTo(other.packageName) : i);
                        }
                    }
                });
            else
                Collections.sort(listRules, new Comparator<Rule>() {
                    @Override
                    public int compare(Rule rule, Rule other) {
                        if (all || rule.changed == other.changed) {
                            int i = collator.compare(rule.name, other.name);
                            return (i == 0 ? rule.packageName.compareTo(other.packageName) : i);
                        }
                        return (rule.changed ? -1 : 1);
                    }
                });

            return listRules;
        }
    }

    private void updateChanged(boolean default_wifi, boolean default_other, boolean default_roaming) {
        changed = (wifi_blocked != default_wifi ||
                (other_blocked != default_other) ||
                (wifi_blocked && screen_wifi != screen_wifi_default) ||
                (other_blocked && screen_other != screen_other_default) ||
                ((!other_blocked || screen_other) && roaming != default_roaming) ||
                hosts > 0 || lockdown || !apply);
    }

    public void updateChanged(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean screen_on = prefs.getBoolean("screen_on", false);
        boolean default_wifi = prefs.getBoolean("whitelist_wifi", true) && screen_on;
        boolean default_other = prefs.getBoolean("whitelist_other", true) && screen_on;
        boolean default_roaming = prefs.getBoolean("whitelist_roaming", true);
        updateChanged(default_wifi, default_other, default_roaming);
    }

    @Override
    public String toString() {
        // This is used in the port forwarding dialog application selector
        return this.name;
    }


    public static long getTimesMorning(){
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static HashMap<Integer, Long> updateDataUsage(Context context){
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
            return bytes + "0 KB";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format(Locale.US, "%.1f %cB", value / 1024.0, ci.current());
    }
}
