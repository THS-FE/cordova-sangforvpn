/********* sangforvpn.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import "SangforAuthManager.h"
#import "SangforAuthHeader.h"
#import "errheader.h"
//static NSMutableDictionary *_sangforEventCache;

@interface sangforvpn : CDVPlugin {
  // Member variables go here.
}

//VPN登录
///单例的authManager
@property (nonatomic, retain)   SangforAuthManager     *sdkManager;
///VPN模式
@property (nonatomic, assign)   VPNMode                 mode;
///认证类型
@property (nonatomic, assign)   VPNAuthType             authType;

- (void)startVPNInitAndLogin:(CDVInvokedUrlCommand*)command;
- (void)fireDocumentEvent:(NSString*)eventName jsString:(NSString*)jsString;
@end
// static sangforvpn *SharedSangforvpnPlugin;
@implementation sangforvpn
#ifdef __CORDOVA_4_0_0

- (void)pluginInitialize {
    NSLog(@"### pluginInitialize ");
   [self initPlugin];
}

#else

- (CDVPlugin*)initWithWebView:(UIWebView*)theWebView{
    NSLog(@"### initWithWebView ");
    if (self=[super initWithWebView:theWebView]) {
    }
    [self initPlugin];
    return self;
}

#endif
// 插件初始化需要调用的方法
-(void)initPlugin{
//    if(!SharedSangforvpnPlugin){
//        SharedSangforvpnPlugin = self;
//    }
    NSLog(@"### initPlugin ");
    //初始化sdk
    [self initSangforSdk];
//    // 启动免密认证
    [self startTicketAuthLogin];
//     [self dispatchSangforCacheEvent];
}
//- (void)dispatchSangforCacheEvent {
//  for (NSString* key in _sangforEventCache) {
//    NSArray *evenList = _sangforEventCache[key];
//    for (NSString *event in evenList) {
//        [sangforvpn fireDocumentEvent:key jsString:event];
//    }
//  }
//}



#pragma mark - sdk初始化
///初始化Sdk信息
- (void)initSangforSdk
{
     NSLog(@"### initSangforSdk ");
    //默认VPN模式为L3VPN
    _mode = VPNModeL3VPN;

    //认证类型，默认为用户名密码
    _authType = VPNAuthTypePassword;

    //初始化AuthMangager
    _sdkManager = [SangforAuthManager getInstance];
    _sdkManager.delegate = self;

    //禁止越狱手机登录
    [_sdkManager disableCrackedPhoneAuth];

    //设置日志级别
    [_sdkManager setLogLevel:LogLevelDebug];
}
// 开始VPN登录
- (void)startVPNInitAndLogin:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    /**
         mVpnAddress = "https://"+args.getString(0)+":"+args.getString(1);
                       mUserName = args.getString(2).trim();
                       mUserPassword = args.getString(3).trim();     */
    NSString* ip =[command.arguments objectAtIndex:0];
    NSString* port =[command.arguments objectAtIndex:1];

    NSLog(@"%@", ip);
    NSLog(@"%@", port);
    NSLog(@"%@", [command.arguments objectAtIndex:2]);
    NSLog(@"%@", [command.arguments objectAtIndex:3]);
    NSLog(@"以上为收到参数------------------");
//    NSString* mVpnAddress =[[[@"https://" stringByAppendingString:ip]stringByAppendingString:@":"] stringByAppendingString:port];
    NSString* mVpnAddress =[NSString stringWithFormat:@"https://%@%@%@", ip,@":",port];
    NSLog(@"接完的地址：%@", mVpnAddress);
    NSString* mUserName =[command.arguments objectAtIndex:2];
    NSLog(@"%@", mUserName);
    NSString* mUserPassword =[command.arguments objectAtIndex:3];
    NSLog(@"%@", mUserPassword);
    if (mVpnAddress != nil && [mVpnAddress length] > 0&&mUserName != nil && [mUserName length] > 0&&mUserPassword != nil && [mUserPassword length] > 0) {
        // 进行登录
         NSURL *vpnUrl = [NSURL URLWithString:mVpnAddress];
        [_sdkManager startPasswordAuthLogin:_mode vpnAddress:vpnUrl
        username:mUserName password:mUserPassword];
        NSLog(@"进行登录。。。");
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:mVpnAddress];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}
// 获取登录状态
- (void)getVpnState:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;

    VPNStatus status = [_sdkManager queryStatus];
    NSString* statusString = @"";
    if(status== VPNStatusOnLine){
        //在线
        statusString = @"VPNONLINE";
    } else if(status== VPNStatusOffLine){
        //  离线
        statusString = @"VPNOFFLINE";
    }
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:statusString];

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

//启动免密认证
- (void)startTicketAuthLogin
{
    if ([_sdkManager ticketAuthAvailable]) {
        _authType = VPNAuthTypeTicket;
        // [MBProgressHUD showHUDAddedTo:self.view animated:YES];
        [_sdkManager startTicketAuthLogin:_mode];
    } else {
        NSLog(@"当前不支持免密认证");
        //[self showAlertView:@"提示" message:@"当前不支持免密认证"];
    }
}



#pragma mark - SangforAuthDelegate
/**
 认证失败

 @param error 错误信息
 */
- (void)onLoginFailed:(NSError *)error
{
    //[MBProgressHUD hideHUDForView:self.view animated:YES];

    if(error.code == SF_ERROR_CONNECT_VPN_FAILED && _authType == VPNAuthTypeTicket) {
        // [self showTicketAlertView:@"认证失败" message:error.domain];
        [self fireDocumentEvent:@"onLogin"
                                    jsString:error.domain];

    } else {
//        [self showAlertView:@"认证失败" message:[NSString stringWithFormat:@"%@,code=%ld",error.domain,(long)error.code]];
        [self fireDocumentEvent:@"onLogin"
                                    jsString:[NSString stringWithFormat:@"%@,code=%ld",error.domain,(long)error.code]];
    }


}

/**
 认证成功
 */
- (void)onLoginSuccess
{
    //[MBProgressHUD hideHUDForView:self.view animated:YES];

 //   [self saveUserConf];

//    if (!_networkController) {
//        self.networkController = [[NetworkViewController alloc] initWithNibName:@"NetworkViewController" bundle:nil];
//    }
//
//    [self.navigationController pushViewController:_networkController animated:YES];
    // sendMsg(errorStr,"onLogin"); sendMsg("success","onLogin");
    [self fireDocumentEvent:@"onLogin"
                          jsString:@"success"];
//    NSString *jsStr = [NSString stringWithFormat:@"cordova.plugins.sangforvpn.onLoginInAndroidCallback('%@')",@"success"];
//    [self.commandDelegate evalJs:jsStr];

}

-(void)fireDocumentEvent:(NSString*)eventName jsString:(NSString*)jsString{

    NSString *jsStr = [NSString stringWithFormat:@"cordova.plugins.sangforvpn.%@Callback('%@')",eventName,jsString];
    [self.commandDelegate evalJs:jsStr];
//  if (SharedSangforvpnPlugin) {
//    dispatch_async(dispatch_get_main_queue(), ^{
//      [SharedSangforvpnPlugin.commandDelegate evalJs:[NSString stringWithFormat:@"cordova.fireDocumentEvent('sangforvpn.%@',%@)", eventName, jsString]];
//    });
//    return;
//  }
//
//  if (!_sangforEventCache) {
//    _sangforEventCache = @{}.mutableCopy;
//  }
//
//  if (!_sangforEventCache[eventName]) {
//    _sangforEventCache[eventName] = @[].mutableCopy;
//  }

//  [_sangforEventCache[eventName] addObject: jsString];
}

-(NSString*)toJsonString{
    NSError  *error;
    NSData   *data       = [NSJSONSerialization dataWithJSONObject:self options:0 error:&error];
    NSString *jsonString = [[NSString alloc]initWithData:data encoding:NSUTF8StringEncoding];
    return jsonString;
}

@end
