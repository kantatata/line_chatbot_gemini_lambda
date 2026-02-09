package app;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import app.lib.ContactForGemini;

/**
 * Java 21環境でJacksonライブラリを使用し、InputStreamから直接JSONを解析するハンドラ。
 */
public class LineChatBotByGemini implements RequestStreamHandler {

    //lambdaの環境変数からトークンを取得
    private static final String CHANNEL_ACCESS_TOKEN = System.getenv("LINE_ACCESS_TOKEN");
    private static final String REPLY_API = "https://api.line.me/v2/bot/message/reply";

    // JSON解析のインスタンス。
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        //Jsonデータをjavaで扱えるようにしたものがJsonNode
        JsonNode rootNode = null;
        try {
            // ObjectMapperメソッドでjsonデータをJsonNodeへ変換
            rootNode = objectMapper.readTree(inputStream);
            context.getLogger().log("LINEから受け取ったJSONデータ: " + rootNode.toString());
        } catch (IOException e) {
            context.getLogger().log("LINEからjsonデータを正確に受け取れませんでした: " + e.getMessage());
            outputStream.write("{}".getBytes(StandardCharsets.UTF_8));  //LINEに返すデータ。String型.メソッド。
            return; 
        }

        String[] content = extractReplyToken(rootNode, context);   // JsonNodeからreplyTokenのノードを取得
        String replyMessage = "";
        if (content[0] != null) {
             switch (content[1]){
                case "text"     ->  replyMessage = new ContactForGemini().talkforgemini(content[2], context);
                case "sticker"  ->  replyMessage = "スタンプありがとう";
                default         ->  replyMessage = "ファイルはいやよ";
            } 
            sendReplyMessage(content[0], replyMessage, context);   // 抽出したトークンからメッセージを返信
        } else {
            context.getLogger().log("Warning: 有効なreplyTokenが見つかりませんでした。");
        }
        // Lambdaからgatewayへ空jsonを送信。gatewayはそれを受けてlineにhttp 200を送信
        outputStream.write("{}".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JsonNodeからreplyTokenを抽出する
     * API GatewayによってラップされたJSONの 'body' フィールドから、LINEペイロードを抽出して処理する。
     */
    private String[] extractReplyToken(JsonNode rootNode, Context context) {
        
        // 1. API GatewayのJSONから 'body' フィールド（LINEのペイロード文字列）を取得
        JsonNode bodyNode = rootNode.path("body");
        if (bodyNode.isMissingNode() || !bodyNode.isTextual()) {
            context.getLogger().log("Error: API Gatewayの 'body' フィールドが見つからないか、文字列ではありません。");
            return null;
        }

        //Json文字列をJsonに変換
        String linePayloadString = bodyNode.asText();
        
        try {
            // JSONを再度解析してJsonNode(javaで扱いやすい形)にする
            JsonNode lineRootNode = objectMapper.readTree(linePayloadString);
            
            // 解析したLINEペイロードから eventsフィールドを取得
            JsonNode eventsNode = lineRootNode.path("events");
            String[] content = new String[3];
            if (eventsNode.isArray() && eventsNode.size() > 0) {
                content[0] = eventsNode.get(0).path("replyToken").asText("not massage");
                content[1] = eventsNode.get(0).path("message").path("type").asText("not massege");
                content[2] = eventsNode.get(0).path("message").path("text").asText("not message");
                return content;
            }
            
        } catch (IOException e) {
            context.getLogger().log("Error: LINEペイロードのJSONパースに失敗しました: " + e.getMessage());
        }

        return null;
    }

    /**
     * LINE Reply APIにメッセージを送信する
     */
    private void sendReplyMessage(String replyToken, String message, Context context) {
        try {
            URL url = new URL(REPLY_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();      //LINEDeveloperへ通信するためのインスタンスを作成。
            conn.setRequestMethod("POST");     //GET または POST。今回は送信のためPOST
            conn.setRequestProperty("Content-Type", "application/json");    //送信データはJSON
            conn.setRequestProperty("Authorization", "Bearer " + CHANNEL_ACCESS_TOKEN);    //認証
            conn.setDoOutput(true);

            // 返信用json文字列データの作成
            String jsonPayload = String.format(
                "{\"replyToken\":\"%s\",\"messages\":[{\"type\":\"text\",\"text\":\"%s\"}]}",
                replyToken,
                message
            );

            // データの送信 (try-with-resourcesで安全にクローズ)
            try (OutputStream os = conn.getOutputStream()) {    //http通信先へのパイプ作成
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));     //パイプにメッセージをセット。このパイプが閉じられるときにメッセージを送信。
            }

            int responseCode = conn.getResponseCode();  //メッセージ送信に対する応答メッセージを取得
            context.getLogger().log("lambdaからの自動返信に対する返信: " + responseCode);   //lambdaに表示

        } catch (Exception e) {
            context.getLogger().log("lambdaからの自動返信に関して問題発生。LINE連携エラー: " + e.getMessage());
        }
    }

    //geminiにメッセージを送る。別クラスにしたい。
    // public String contactForGemini(String message, Context context) {
        
    //     // Lambdaのログに開始メッセージを出力
    //     context.getLogger().log("geminiへメッセージを送信しました: メッセージ内容: " + message);
        
    //     String responseText;
    //     try {
    //         // APIクライアントの初期化
    //         Client client = new Client();
            
    //         // API呼び出し
    //         GenerateContentResponse response =
    //             client.models.generateContent(
    //                 "gemini-2.5-flash",
    //                 message,
    //                 null);
            
    //         responseText = response.text();
            
    //         context.getLogger().log("geminiからメッセージを受け取りました: " + responseText);
            
    //     } catch (Exception e) {
    //         // エラーログを出力し、エラーメッセージを返す
    //         context.getLogger().log("エラーが発生しました: " + e.getMessage());
    //         return "Error: " + e.getMessage();
    //     }
        
    //     // 成功した結果を返す
    //     return responseText;
    // }
}