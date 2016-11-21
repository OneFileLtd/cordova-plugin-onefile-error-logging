#import "SendRequest.h"

@interface SendRequest ()
{
    NSURLConnection *_uploadConnection;
}

@property (nonatomic, retain) NSURLConnection *uploadConnection;
@end

@implementation SendRequest

@synthesize uploadConnection = _uploadConnection;

+ (NSDictionary *)makeRequest:(NSDictionary *)config
{
    NSURLResponse *response;
    NSError *error;
    NSString *userIdString;
    NSString *currentPlatformVersionString;

    NSDictionary *config_headers = [config objectForKey:@"headers"];
    NSDictionary *config_error = [config objectForKey:@"error"];
	NSString *config_endPoint = [config objectForKey:@"endpoint"];

    NSURL *url = [NSURL URLWithString:config_endPoint];

    NSMutableDictionary *JSON = [[NSMutableDictionary alloc] init];
    [JSON setValue:[config_error objectForKey:@"name"] forKey:@"Name"];
    [JSON setValue:[config_error objectForKey:@"message"] forKey:@"Message"];
    [JSON setValue:[config_error objectForKey:@"cause"] forKey:@"Cause"];
    [JSON setValue:[config_error objectForKey:@"stackTrace"] forKey:@"StackTrace"];
    [JSON setValue:[config objectForKey:@"currentUsername"] forKey:@"CurrentUsername"];

    NSData *body = [NSJSONSerialization dataWithJSONObject:JSON options:NSJSONWritingPrettyPrinted error:&error];
    NSString *postLength = [NSString stringWithFormat:@"%lu", (unsigned long)[body length]];

    // Create Request
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL: url];

    [request setCachePolicy:NSURLRequestReloadIgnoringCacheData];
    [request setValue:postLength forHTTPHeaderField:@"Content-Length"];

    if([config_headers objectForKey:@"userId"] && [[config_headers objectForKey:@"userId"] isKindOfClass: [NSNumber class]])
        userIdString = [NSString stringWithFormat:@"%lu", (unsigned long)[[config_headers objectForKey:@"userId"] longValue]];
    else
        userIdString = [config_headers objectForKey:@"userId"];

    if([config_headers objectForKey:@"currentPlatformVersion"] && [[config_headers objectForKey:@"currentPlatformVersion"] isKindOfClass: [NSNumber class]])
        currentPlatformVersionString = [NSString stringWithFormat:@"%lu", (unsigned long)[[config_headers objectForKey:@"currentPlatformVersion"] longValue]];
    else
        currentPlatformVersionString = [config_headers objectForKey:@"currentPlatformVersion"];

    [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Accept"];
    [request setHTTPMethod:@"POST"];
    [request setValue:userIdString forHTTPHeaderField:@"X-UserID"];
    [request setValue:[config_headers objectForKey:@"currentPlatform"] forHTTPHeaderField:@"X-Current-Platform"];
    [request setValue:currentPlatformVersionString forHTTPHeaderField:@"X-Current-Platform-Version"];
    [request setHTTPBody:body];

    // Send Request
    [NSURLConnection sendSynchronousRequest:request returningResponse:&response error:&error];

    NSDictionary *jSON = NULL;
    // Process Response
    NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
    if ([response respondsToSelector:@selector(allHeaderFields)]) {
        jSON = @
        {
            @"status": [NSNumber numberWithInteger:[httpResponse statusCode]],
            @"headers": [httpResponse allHeaderFields]
        };
        // [self pluginSuccess:jSON];
    }
    return jSON;
}
@end
