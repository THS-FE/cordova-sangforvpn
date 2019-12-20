var exec = require('cordova/exec');
// /**
//  * 设置登录信息
//  * @param mVpnAddress vpn地址ip
//  * @param mVpnPort vpn地址对应的端口
//  * @param mUserName vpn用户名
//  * @param mUserPassword vpn密码
//  */
// exports.setLoginInfo = function (mVpnAddress,mVpnPort,mUserName,mUserPassword, success, error) {
//     exec(success, error, 'sangforvpn', 'setLoginInfo', [mVpnAddress,mVpnPort,mUserName,mUserPassword]);
// };

/**
 * 启动vpn 初始化并登录
 * @param mVpnAddress vpn地址ip
 * @param mVpnPort vpn地址对应的端口 
 * @param mUserName vpn用户名
 * @param mUserPassword vpn密码
 */
exports.startVPNInitAndLogin = function (mVpnAddress,mVpnPort,mUserName,mUserPassword, success, error) {
    exec(success, error, 'sangforvpn', 'startVPNInitAndLogin', [mVpnAddress,mVpnPort,mUserName,mUserPassword]);
};

/**
 * 注销vpn
 */
exports.doVPNLogout = function ( success, error) {
    exec(success, error, 'sangforvpn', 'doVPNLogout', []);
};

/**
 * 获取vpn状态，返回字段 VPNONLINE    VPNOFFLINE
 */
exports.getVpnState = function (success, error) {
    exec(success, error, 'sangforvpn', 'getVpnState', []);
};

/**
 * 注册vpn状态监听器，
 */
exports.addStatusChangedListenerInAndroidCallback = function(data) {
   //console.log('onDisconnectedReceiverInAndroidCallback' + data);
   const d = {status: data};  // data对应值 vpn在线  vpn离线  vpn 重连中 
   cordova.fireDocumentEvent('sangforvpn.addStatusChangedListener', d);
};

/**
 * 登录状态监听
 */
exports.onLoginInAndroidCallback = function(data) {
    //console.log('onDisconnectedReceiverInAndroidCallback' + data);
    const d = {loginRes: data}; // data 对应值 success
    cordova.fireDocumentEvent('sangforvpn.onLogin', d);
 };


