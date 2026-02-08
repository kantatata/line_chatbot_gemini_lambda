//現在の呼び出す関数名「」(P21に記述)
package app;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Callreplylambda implements RequestStreamHandler {
    private final LambdaClient lambdaClient = LambdaClient.builder().build();

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        String requestBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);

        // 別の関数を呼び出す
        InvokeRequest invokeRequest = InvokeRequest.builder()   //builderクラス。
                .functionName("LineChatBotByGemini_push")       //呼び出す関数名
                .invocationType("Event")                        //Event:非同期
                .payload(SdkBytes.fromUtf8String(requestBody))  //呼び出し関数に渡すデータ
                .build();

        lambdaClient.invoke(invokeRequest);

        // LINEサーバーへ200のレスポンス
        output.write("{\"statusCode\": 200}".getBytes());
    }
}