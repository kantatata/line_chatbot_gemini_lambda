package app.lib;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

// geminiにメッセージを送り受け取る
public class  ContactForGemini {

    public String ContactForGemini(String message, Context context) {
        
        // Lambdaのログに開始メッセージを出力
        context.getLogger().log("geminiへメッセージを送信しました");
        
        String responseText;
        try {
            // APIクライアントの初期化
            Client client = new Client();
            
            // API呼び出し
            GenerateContentResponse response =
                client.models.generateContent(
                    "gemini-2.5-flash",
                    message,
                    null);
            
            responseText = response.text();
            
            context.getLogger().log("geminiからメッセージを受け取りました: " + responseText);
            
        } catch (Exception e) {
            // エラーログを出力し、エラーメッセージを返す
            context.getLogger().log("エラーが発生しました: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
        
        // 成功した結果を返す
        return responseText;
    }
}