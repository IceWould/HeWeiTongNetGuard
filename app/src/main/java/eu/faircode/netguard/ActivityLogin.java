package eu.faircode.netguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActivityLogin extends Activity implements View.OnClickListener {

    private int countSeconds = 60;//倒计时秒数
    private EditText mobile_login, yanzhengma;
    private Button getyanzhengma1, login_btn;

    private Context mContext;
    private String usersuccess;
    private static SharedPreferences prefs;

    private Handler mCountHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (countSeconds > 0) {
                --countSeconds;
                getyanzhengma1.setText("(" + countSeconds + ")后获取验证码");
                mCountHandler.sendEmptyMessageDelayed(0, 1000);
            } else {
                countSeconds = 60;
                getyanzhengma1.setText("请重新获取验证码");
            }
        }
    };
    private String userinfomsg;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean authorized = prefs.getBoolean("authorized", false);
        mContext = this;
        setContentView(R.layout.activity_login);
        initView();
        initEvent();
        initData();
        if(authorized){
            startActivity(new Intent(this, ActivityMain.class));
        }
    }

        private void initView(){
            mobile_login = (EditText) findViewById(R.id.mobile_login);
            getyanzhengma1 = (Button) findViewById(R.id.getyanzhengma1);
            yanzhengma = (EditText) findViewById(R.id.yanzhengma);
            login_btn = (Button) findViewById(R.id.login_btn);

        }

        private void initEvent() {
            getyanzhengma1.setOnClickListener(this);
            login_btn.setOnClickListener(this);

        }

        private void initData() {

        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.getyanzhengma1:
                    if (countSeconds == 60) {
                        String mobile = mobile_login.getText().toString();
                        Log.e("tag", "mobile==" + mobile);
                        getMobiile(mobile);
                    } else {
                        Toast.makeText(ActivityLogin.this, "不能重复发送验证码", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case R.id.login_btn:
                    login();
                    break;
                default:
                    break;
            }
        }
        //获取信息进行登录
        public void login() {
            final String mobile = mobile_login.getText().toString().trim();
            final String verifyCode = yanzhengma.getText().toString().trim();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection connection = null;
                    BufferedReader reader = null;
                    try {
                        URL url = new URL("http://47.118.48.173:8581/install?phoneNumber="+mobile+"&code="+verifyCode);
                        connection = (HttpURLConnection) url.openConnection();
                        //设置请求方法
                        connection.setRequestMethod("GET");
                        //设置连接超时时间（毫秒）
                        connection.setConnectTimeout(10000);
                        //设置读取超时时间（毫秒）
                        connection.setReadTimeout(10000);

                        //返回输入流
                        InputStream in = connection.getInputStream();

                        //读取输入流
                        reader = new BufferedReader(new InputStreamReader(in));
                        StringBuilder result = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            result.append(line);
                        }
                        if(result.toString().trim().equals("true")){
                            startActivity(new Intent(ActivityLogin.this, ActivityMain.class));
                            prefs.edit().putBoolean("authorized",true).apply();
                        }else{
                            Toast.makeText(ActivityLogin.this, "验证失败，请重试！", Toast.LENGTH_LONG).show();
                        }
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (ProtocolException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        if (connection != null) {//关闭连接
                            connection.disconnect();
                        }
                    }
                }
            }).start();
        }

        //获取验证码信息，判断是否有手机号码
        private void getMobiile(String mobile) {
            if ("".equals(mobile)) {
                Log.e("tag", "mobile=" + mobile);
                new AlertDialog.Builder(mContext).setTitle("提示").setMessage("手机号码不能为空").setCancelable(true).show();
            } else if (isMobileNO(mobile) == false) {
                new AlertDialog.Builder(mContext).setTitle("提示").setMessage("请输入正确的手机号码").setCancelable(true).show();
            } else {
                Log.e("tag", "输入了正确的手机号");
                requestVerifyCode(mobile);
            }
        }

        //获取验证码信息，进行验证码请求
        private void requestVerifyCode(final String mobile) {
//            RequestParams requestParams = new RequestParams(“这里是你请求的验证码接口，让后台给你，参数什么的加在后面”);
//
//            x.http().post(requestParams, new Callback.ProgressCallback<String>() {
//                @Override
//                public void onWaiting() {
//
//                }
//
//                @Override
//                public void onStarted() {
//
//                }
//
//                @Override
//                public void onLoading(long total, long current, boolean isDownloading) {
//
//                }
//
//                @Override
//                public void onSuccess(String result) {
//
//                    try {
//                        JSONObject jsonObject2 = new JSONObject(result);
//                        Log.e("tag", "jsonObject2" + jsonObject2);
//                        String state = jsonObject2.getString("success");
//                        String verifyCode = jsonObject2.getString("msg");
//                        Log.e("tag", "获取验证码==" + verifyCode);
//                        if ("true".equals(state)) {
//                            Toast.makeText(ActivityLogin.this, verifyCode, Toast.LENGTH_SHORT).show();
//                            startCountBack();//这里是用来进行请求参数的
//                        } else {
//                            Toast.makeText(ActivityLogin.this, verifyCode, Toast.LENGTH_SHORT).show();
//                        }
//
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//                @Override
//                public void onError(Throwable ex, boolean isOnCallback) {
//                    ex.printStackTrace();
//                }
//
//                @Override
//                public void onCancelled(CancelledException cex) {
//
//                }
//
//                @Override
//                public void onFinished() {
//
//                }
//            });
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection connection = null;
                    BufferedReader reader = null;
                    try {
                        URL url = new URL("http://47.118.48.173:8581/getCode?phoneNumber="+mobile);

                        connection = (HttpURLConnection) url.openConnection();
                        //设置请求方法
                        connection.setRequestMethod("GET");
                        //设置连接超时时间（毫秒）
                        connection.setConnectTimeout(10000);
                        //设置读取超时时间（毫秒）
                        connection.setReadTimeout(10000);

                        //返回输入流
                        InputStream in = connection.getInputStream();

                        //读取输入流
                        reader = new BufferedReader(new InputStreamReader(in));
                        StringBuilder result = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            result.append(line);
                        }
//                            JSONObject jsonObject2 = new JSONObject(result);
//                            Log.e("tag", "jsonObject2" + jsonObject2);
//                            String state = jsonObject2.getString("success");
//                            String verifyCode = jsonObject2.getString("msg");
//                            Log.e("tag", "获取验证码==" + verifyCode);
                        if ("true".equals(result.toString().trim())) {
//                                Toast.makeText(ActivityLogin.this, verifyCode, Toast.LENGTH_SHORT).show();
                            startCountBack();//这里是用来进行请求参数的
                        } else {
                            Toast.makeText(ActivityLogin.this, "请求验证码失败，请稍后再试！", Toast.LENGTH_LONG).show();
                        }

                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (ProtocolException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        if (connection != null) {//关闭连接
                            connection.disconnect();
                        }
                    }
                }
            }).start();
        }

        //使用正则表达式判断电话号码
        public static boolean isMobileNO(String tel) {
            Pattern p = Pattern.compile("^(13[0-9]|15([0-3]|[5-9])|14[5,7,9]|17[1,3,5,6,7,8]|18[0-9])\\d{8}$");
            Matcher m = p.matcher(tel);
            System.out.println(m.matches() + "---");
            return m.matches();
        }

        //获取验证码信息,进行计时操作
        private void startCountBack() {
            ((Activity) mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getyanzhengma1.setText(countSeconds + "");
                    mCountHandler.sendEmptyMessage(0);
                }
            });
        }



    }