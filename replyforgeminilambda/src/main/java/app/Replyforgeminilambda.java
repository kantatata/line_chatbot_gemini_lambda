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

public class Replyforgeminilambda implements RequestStreamHandler {
    private static final String CHANNEL_ACCESS_TOKEN = System.getenv("LINE_ACCESS_TOKEN");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String REPLY_API = "https://api.line.me/v2/bot/message/push";

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        try {
            //受け取ったデータを表示
            context.getLogger().log("呼び出し元から送られてきたデータ：" + input.toString());
            //呼び出し元から受け取ったデータの解析、データの取得
            //0:userID, 1:メッセージの種類(テキストやスタンプ等), 2:メッセージ本文
            String[] content = extractJsonMsg(input, context);

            //メッセージの種類に応じた返信文作成(テキストだった場合はgeminiへ)
            String replyMessage = "";
            context.getLogger().log("userIDの確認：" + content[0]);
            if (content[0] != null) {
                switch (content[1]){
                    case "text"     ->  replyMessage = new ContactForGemini().talkforgemini(content[2], context);
                    case "sticker"  ->  replyMessage = "スタンプありがとう";
                    default         ->  replyMessage = "ファイルはいやよ";
                } 
                sendReplyMessage(content, replyMessage, context);   // 抽出したトークンからメッセージを返信
            } else {
                context.getLogger().log("Warning: 有効なuserIDが見つかりませんでした。");
            } 
        }catch (Exception e) {
            context.getLogger().log("送信に失敗しました " + e.getMessage());
            e.printStackTrace();
        }
    }   

    private String[] extractJsonMsg(InputStream input, Context context) throws IOException {

        //呼び出し元からのJSONストリームをJsonNodeにする
        JsonNode lambdaNode = null;
        try {
            // ObjectMapperクラスのreedTreeメソッドでJSON文字列ををJsonNodeへ変換
            lambdaNode = objectMapper.readTree(input);
        } catch (IOException e) {
            context.getLogger().log("LINEからjsonデータを正確に受け取れませんでした: " + e.getMessage());
        }
        //JsonNodeを表示
        context.getLogger().log("呼び出し元からのJSONストリームをJsonNodeにした：" + lambdaNode.toString());
        //呼び出し元関数によってラップされているJsonNodeから、LineからのJSON文字列を取り出す
        JsonNode bodyJson = lambdaNode.path("body");
        //LineからのJSONを表示
        context.getLogger().log("lineからのJsonNodeを表示" + bodyJson.toString());
        //bodyフィールドのnullチェック
        if (bodyJson.isMissingNode() || !bodyJson.isTextual()) {
            context.getLogger().log("Error: API Gatewayの 'body' フィールドが見つからないか、文字列ではありません。");
            return null;
        }

        //reedTreeをするために、一度String型にする
        String linePayloadString = bodyJson.asText();
        //Lineからのメッセージの表示
        context.getLogger().log("lineからのJsonNodeをStringに変換して表示(大本であるLINEから送られてくるデータの形)：" + linePayloadString.toString());

        try {
            // 
            JsonNode lineRootNode = objectMapper.readTree(linePayloadString);
            //LINEからのデータをJsonNode化したものを表示
            context.getLogger().log("大本であるLINEから送られてくるJson文字列をJsonNode化したもの：" + linePayloadString.toString());
            
            // 解析したLINEペイロードから eventsフィールドを取得
            JsonNode eventsNode = lineRootNode.path("events");
            //eventsフィールドを表示
            context.getLogger().log("eventsフィールド(返信先やメッセージの種類やメッセージ本文等)を表示：" + eventsNode.toString());
            
            String[] content = new String[3];
            if (eventsNode.isArray() && eventsNode.size() > 0) {
                content[0] = eventsNode.get(0).path("source").path("userId").asText();
                content[1] = eventsNode.get(0).path("message").path("type").asText("not massege");
                content[2] = eventsNode.get(0).path("message").path("text").asText("not message");
                return content;
            } else if (eventsNode.isArray() && eventsNode.size() == 0){
                context.getLogger().log("events画からです。LINE Webhook検証のメッセージの可能性があります。");
            }
            
        } catch (IOException e) {
            context.getLogger().log("Error: LINEペイロードのJSONパースに失敗しました: " + e.getMessage());
        }

        return null;
    }

    private void sendReplyMessage(String[] content, String message, Context context) {
        try {
            URL url = new URL(REPLY_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();      //LINEDeveloperへ通信するためのインスタンスを作成。
            conn.setRequestMethod("POST");     //GET または POST。今回は送信のためPOST
            conn.setRequestProperty("Content-Type", "application/json");    //送信データはJSON
            conn.setRequestProperty("Authorization", "Bearer " + CHANNEL_ACCESS_TOKEN);    //認証
            conn.setDoOutput(true);
            //jsonを作成
             String jsonPayload = objectMapper.createObjectNode()
                .put("to", content[0])
                .set("messages", objectMapper.createArrayNode()
                    .add(objectMapper.createObjectNode()
                        .put("type", "text")
                        .put("text", message)))
                .toString();
            context.getLogger().log("送信予定データ: " + jsonPayload.toString());

            // データの送信
            try (OutputStream os = conn.getOutputStream()) {    //http通信先へのパイプ作成
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));     //パイプにメッセージをセット。このパイプが閉じられるときにメッセージを送信。
            }

            int responseCode = conn.getResponseCode();  //メッセージ送信に対する応答メッセージを取得
            context.getLogger().log("lambdaからの自動返信に対する返信: " + responseCode);   //lambdaに表示

        } catch (Exception e) {
            context.getLogger().log("lambdaからの自動返信に関して問題発生。LINE連携エラー: " + e.getMessage());
        }
    }
}