#import <Cordova/CDVPlugin.h>

@interface SendRequest : NSObject
{
}
+ (NSDictionary *)makeRequest:(NSDictionary *)config;
@end
