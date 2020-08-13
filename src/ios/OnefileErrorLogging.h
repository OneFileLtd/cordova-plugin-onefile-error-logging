#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
#import <QuartzCore/QuartzCore.h>
#import <Cordova/CDVPlugin.h>

enum CDVLogError {
    LOGGING_NOT_SUPPORTED = 0
};
typedef NSUInteger CDVLogError;

/************************************************************************************************************
 *      CDVAudioRecorder - Initialisation point of the plugin, creates a Navigation Controller and Pushes
 *      the main audio recorder view controller on to it.
 ************************************************************************************************************/
@interface OnefileErrorLogging : CDVPlugin
{
    BOOL _inUse;
    BOOL _allowSync;
}
@property BOOL inUse;
@property BOOL allowSync;

- (void)logError:(CDVInvokedUrlCommand*)command;
- (NSDictionary *)getOldestError;
- (void)scheduleUploads;
@end
