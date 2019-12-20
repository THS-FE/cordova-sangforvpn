package cn.com.ths.sangfor.vpn;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.sangfor.ssl.BaseMessage;
import com.sangfor.ssl.IConstants;
import com.sangfor.ssl.IVpnDelegate;
import com.sangfor.ssl.LoginResultListener;
import com.sangfor.ssl.OnStatusChangedListener;
import com.sangfor.ssl.RandCodeListener;
import com.sangfor.ssl.SFException;
import com.sangfor.ssl.SangforAuthManager;
import com.sangfor.ssl.StatusChangedReason;
import com.sangfor.ssl.common.ErrorCode;
import com.sangfor.ssl.service.utils.jni.CertUtils;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;

import static android.content.Context.MODE_PRIVATE;
import org.apache.cordova.PermissionHelper;

/**
 * 深信服VPN.
 */
public class sangforvpn extends CordovaPlugin implements LoginResultListener, RandCodeListener {
    private Context context;
    private static final String TAG = "sangforvpn";
    private String   mVpnAddress,mUserName,mUserPassword,mCertPath,mCertPassword;
    private SangforAuthManager mSFManager = null;
    private VPNMode mVpnMode = VPNMode.L3VPN;            //默认开启L3VPN模式
    //主认证默认采用用户名+密码方式
    private int mAuthMethod = AUTH_TYPE_PASSWORD;
    private URL mVpnAddressURL = null;
    private static final int CERTFILE_REQUESTCODE = 33;        //主界面中证书选择器请求码
    private static final int DIALOG_CERTFILE_REQUESTCODE = 34; //对话框中选择器的请求码
    private static final int DEFAULT_SMS_COUNTDOWN = 30;       //短信验证码默认倒计时时间
  /**
   * 权限列表
   */
  private String[] locPerArr = new String[] {
    Manifest.permission.INTERNET,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACCESS_NETWORK_STATE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.ACCESS_WIFI_STATE
  };
    private static sangforvpn instance;
    public sangforvpn() {
        instance = this;
    }
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        cordova.setActivityResultCallback (this);
        super.initialize(cordova, webView);
        this.context = cordova.getActivity();
        //尝试申请权限并进行初始化
        promtForInit();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        // 设置登录信息
//        if (action.equals("setLoginInfo")) {
//            String message = args.getString(0);
//            this.setLoginInfo();
       //     return true;
        // 启动VPN 并初始化登录
//        }else
       if(action.equals("startVPNInitAndLogin")){
                //mVpnAddress,mVpnPort,mUserName,mUserPassword
                mVpnAddress = "https://"+args.getString(0)+":"+args.getString(1);
                mUserName = args.getString(2).trim();
                mUserPassword = args.getString(3).trim();
            try {
                mVpnAddressURL = new URL(mVpnAddress);
                if (TextUtils.isEmpty(mVpnAddressURL.getHost()))
                {
                    throw new IllegalArgumentException();
                }
                //开启登录流程
                startVPNInitAndLogin();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return true;
            // 注销VPN登录
        }else if(action.equals("doVPNLogout")){
           cordova.getActivity().runOnUiThread(new Runnable() {
               @Override
               public void run() {
                   doVPNLogout();
                   callbackContext.success("success");
               }
           });
           // 获取VPN状态
        }else  if(action.equals("getVpnState")){
           String vpnSate= getVpnState();
           callbackContext.success(vpnSate);
        }
        return false;
    }

    private void setLoginInfo() {
        SharedPreferences sharedPreferences = context.getSharedPreferences("config", MODE_PRIVATE);

        mVpnAddress = sharedPreferences.getString("VpnAddress", mVpnAddress);
        mUserName = sharedPreferences.getString("UserName", mUserName);
        mUserPassword = sharedPreferences.getString("UserPassword", mUserPassword);
        mCertPath = sharedPreferences.getString("CertPath", mCertPath);
        mCertPassword = sharedPreferences.getString("CertPassword", mCertPassword);
    }
    /**
     * 登录失败回调接口
     * @param errorCode  错误码
     * @param errorStr   错误信息
     */
    @Override
    public void onLoginFailed(ErrorCode errorCode, String errorStr) {

        if (!TextUtils.isEmpty(errorStr)) {
           // Toast.makeText(context, "登录失败！" + errorStr, Toast.LENGTH_SHORT).show();
        } else {
           // Toast.makeText(context, "登录失败！", Toast.LENGTH_SHORT).show();
        }
        sendMsg(errorStr,"onLogin");
    }

    /**
     * 登录进行中回调接口
     * @param nextAuthType 下次认证类型
     *                     组合认证时必须实现该接口
     */
    @Override
    public void onLoginProcess(int nextAuthType, BaseMessage baseMessage) {

        // 存在多认证, 需要进行下一次认证
        Log.e(TAG,"onLoginProcess，nextAuthType："+nextAuthType+",ErrorStr:" + baseMessage.getErrorStr());
    }

    @Override
    public void onLoginSuccess() {
//        //停止登录进度框
//        cancelWaitingProgressDialog();
        //保存登录信息
        Log.e(TAG,"onLoginSuccess");
        saveLoginInfo();
        // 认证成功后即可开始访问资源
        sendMsg("success","onLogin");
    }

    @Override
    public void onShowRandCode(Drawable drawable) {

    }

    /**
     * SharedPreferences保存登录信息
     */
    private void saveLoginInfo() {
        SharedPreferences sharedPreferences = context.getSharedPreferences("config",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("VpnAddress", mVpnAddress);
        //保存用户名和密码，暂不加密存储
        editor.putString("UserName", mUserName);
        editor.putString("UserPassword", mUserPassword);
        editor.putString("CertPath", mCertPath);
        editor.putString("CertPassword", mCertPassword);
        editor.apply();
    }
    /**
     * 进行免密登录流程
     */
    private void startTicketLogin() {
        if (((Activity)context).isFinishing()) {
            return;
        }
        initLoginParms();

        //判断是否开启免密，如果免密直接进行一次登录，如果无法免密或免密登录失败，通知界面
        if (mSFManager.ticketAuthAvailable(context)) { //允许免密，直接走免密流程
            //开启登录进度框
            //createWaitingProgressDialog();
            try {
                addStatusChangedListener(); //添加vpn状态变化监听器
                mSFManager.startTicketAuthLogin(((Activity)context).getApplication(), ((Activity)context), mVpnMode);
            } catch (SFException e) {
                //关闭登录进度框
                //cancelWaitingProgressDialog();
                com.sangfor.bugreport.logger.Log.info(TAG, "SFException:%s", e);
            }
        } else {
            Toast.makeText(context, "免密未开启或失效", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 初始化登录参数
     */
    private void initLoginParms() {
        // 1.构建SangforAuthManager对象
        mSFManager = SangforAuthManager.getInstance();

        // 2.设置VPN认证结果回调
        try {
            mSFManager.setLoginResultListener(this);
        }catch (SFException e) {
            com.sangfor.bugreport.logger.Log.info(TAG, "SFException:%s", e);
        }

        //3.设置登录超时时间，单位为秒
        mSFManager.setAuthConnectTimeOut(8);
    }

    /**
     * 注册vpn状态监听器，可在多处进行注册
     */
    private void addStatusChangedListener() throws SFException{
        mSFManager.addStatusChangedListener(new OnStatusChangedListener() {
            @Override
            public void onStatusCallback(IVpnDelegate.VPNStatus vpnStatus, StatusChangedReason reason) {
                //对回调结果进行处理，这里只是简单的显示，可根据业务需求自行扩展
                String status = "";
                switch (vpnStatus){
                    case VPNONLINE:
                        status = "vpn在线";
                        break;
                    case VPNOFFLINE:
                        status = "vpn离线";
                        break;
                    case VPNRECONNECTED:
                        status = "vpn重连中";
                        break;
                }
               // Toast.makeText(context, status, Toast.LENGTH_SHORT).show();
                sendMsg(status,"addStatusChangedListener");
            }
        });
    }

    /**
     * 初始登录统一接口
     */
    private void startVPNInitAndLogin() {
        if (((Activity)context).isFinishing()) {
            return;
        }
        initLoginParms();

        //开启登录进度框
        //createWaitingProgressDialog();

        try {
            addStatusChangedListener(); //添加vpn状态变化监听器
            //依据登录方式调用相应的登录接口
            switch (mAuthMethod) {
                case AUTH_TYPE_PASSWORD:
                    //该接口做了两件事：1.vpn初始化；2.用户名/密码主认证过程
                    try {
                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mSFManager.startPasswordAuthLogin(((Activity)context).getApplication(), ((Activity)context), mVpnMode,
                                            mVpnAddressURL, mUserName, mUserPassword);
                                } catch (SFException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }catch (Exception e){
                        Log.e(TAG,e.getMessage());
                    }

                    break;
                case AUTH_TYPE_CERTIFICATE:
                    //该接口做了两件事：1.vpn初始化；2.证书主认证过程
                    mSFManager.startCertificateAuthLogin(((Activity)context).getApplication(), ((Activity)context), mVpnMode,
                            mVpnAddressURL, mCertPath, mCertPassword);
                    break;
                default:
                    Toast.makeText(((Activity)context), "认证方式错误", Toast.LENGTH_SHORT).show();
                    break;
            }
        }catch (SFException e) {
            //关闭登录进度框
            // cancelWaitingProgressDialog();
            com.sangfor.bugreport.logger.Log.info(TAG, "SFException:%s", e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case CERTFILE_REQUESTCODE:
                //获取证书选择器结果
                String certPath = "";
                if (resultCode == Activity.RESULT_OK) {
                    certPath = CertUtils.fromUriGetRealPath(context, data.getData()).trim();
                }
                // mCertPathEditView.setText(certPath);
                break;
            case DIALOG_CERTFILE_REQUESTCODE:
                //当证书认证是辅助认证时获取证书选择器结果
                String certPathDialog = "";
                if (resultCode == Activity.RESULT_OK) {
                    certPathDialog = CertUtils.fromUriGetRealPath(context, data.getData()).trim();
                }
                // mCertPathDialogEditView.setText(certPathDialog);
                break;
            case IVpnDelegate.REQUEST_L3VPNSERVICE:
                /* L3VPN模式下下必须回调此方法, EasyApp模式下不用
                 * 注意：当前Activity的launchMode不能设置为 singleInstance，否则L3VPN服务启动会失败。
                 */
                mSFManager.onActivityResult(requestCode, resultCode);
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    /**
     * 注销流程
     */
    private void doVPNLogout() {
        // 注销VPN登录.
        SangforAuthManager.getInstance().vpnLogout();
    }

    /**
     * 获取VPN的状态
     * 在线状态IConstants.VPNStatus.VPNONLINE，vpn资源可用
     * 离线状态IConstants.VPNStatus.VPNOFFLINE，vpn资源不可用
     */
    private String getVpnState(){
        IConstants.VPNStatus vpnStatus = SangforAuthManager.getInstance().queryStatus();
        return  vpnStatus.toString();
    }

    /**
     * 发送消息到
     * @param data
     * @param methodStr
     */
    private  void sendMsg(String data,String methodStr){
        String format = "cordova.plugins.sangforvpn."+methodStr+"InAndroidCallback(%s);";
        final String js = String.format(format, "'"+data+"'");
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                instance.webView.loadUrl("javascript:" + js);
            }
        });
    }

  /**
   * 检查权限并申请
   */
  private void promtForInit() {
    for (int i = 0, len = locPerArr.length; i < len; i++) {
      if (!PermissionHelper.hasPermission(this, locPerArr[i])) {
        PermissionHelper.requestPermission(this, i, locPerArr[i]);
        return;
      }
    }
    // exeLoc(action);
    //尝试进行免密登录
    startTicketLogin();
    setLoginInfo();
  }
  @Override
  public void onRequestPermissionResult(int requestCode,
                                        String[] permissions, int[] grantResults) throws JSONException {
    // TODO Auto-generated method stub
    for (int r : grantResults) {
      if (r == PackageManager.PERMISSION_DENIED) {
        Toast.makeText(context,"禁用权限将影响app正常运行",Toast.LENGTH_LONG).show();
        return;
      }
    }
    promtForInit();
  }
}
