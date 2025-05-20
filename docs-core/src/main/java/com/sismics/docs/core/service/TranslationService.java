// A1
package com.sismics.docs.core.service;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Translation service using Baidu Cloud Translation API.
 */
public class TranslationService {
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    
    private static final String BAIDU_TRANSLATE_API_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";
    private static final String BAIDU_APP_ID = "20250513002355906";
    private static final String BAIDU_SECRET_KEY = "XgMt3CWruNY_ycgmGTh0";
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final double MIN_VALID_CHAR_RATIO = 0.3; // 降低有效字符比例要求
    private static final int MIN_TEXT_LENGTH = 10;
    private static final double MAX_BINARY_RATIO = 0.7; // 二进制字符比例阈值

    private String preprocessText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 检查二进制字符比例
        int binaryChars = 0;
        for (char c : text.toCharArray()) {
            if (c < 32 || c > 126) {
                binaryChars++;
            }
        }
        double binaryRatio = (double) binaryChars / text.length();
        log.info("[translate] Binary character ratio: {}", binaryRatio);

        if (binaryRatio > MAX_BINARY_RATIO) {
            log.error("[translate] Too many binary characters, ratio: {}", binaryRatio);
            return null;
        }

        // 移除控制字符和规范化空白字符
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                  .replaceAll("\\s+", " ")
                  .trim();

        // 验证文本质量
        if (!isValidText(text)) {
            log.error("[translate] Text validation failed after preprocessing");
            return null;
        }

        return text;
    }

    private boolean isValidText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // 计算有效字符的比例
        int validChars = 0;
        int totalChars = text.length();
        
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || 
                Character.isWhitespace(c) || 
                (c >= 0x4E00 && c <= 0x9FFF) || // 中文字符
                (c >= 0xFF00 && c <= 0xFFEF) || // 全角字符
                (c >= 0x3000 && c <= 0x303F) || // 中文标点
                (c >= 0x2000 && c <= 0x206F) || // 通用标点
                (c >= 32 && c <= 126)) { // 可打印ASCII字符
                validChars++;
            }
        }
        
        double ratio = (double) validChars / totalChars;
        log.info("[translate] Text validation - Valid chars: {}, Total chars: {}, Ratio: {}", 
                validChars, totalChars, ratio);
        
        return ratio >= MIN_VALID_CHAR_RATIO && text.length() >= MIN_TEXT_LENGTH;
    }

    /**
     * Translate text using Baidu Cloud Translation API.
     *
     * @param text Text to translate
     * @param targetLanguage Target language code (e.g., "en", "zh")
     * @return Translated text
     * @throws Exception If translation fails
     */
    public String translate(String text, String targetLanguage) throws Exception {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 预处理文本
        text = preprocessText(text);
        if (text == null) {
            throw new Exception("Invalid text content - text validation failed");
        }

        log.info("[translate] Translating text length: {}, target language: {}", text.length(), targetLanguage);

        // 如果文本长度超过限制，分段翻译
        if (text.length() > MAX_TEXT_LENGTH) {
            log.info("[translate] Text exceeds maximum length, splitting into chunks");
            StringBuilder result = new StringBuilder();
            int startIndex = 0;
            
            while (startIndex < text.length()) {
                int endIndex = Math.min(startIndex + MAX_TEXT_LENGTH, text.length());
                // 找到最后一个完整句子的结束位置
                if (endIndex < text.length()) {
                    int lastPeriod = text.lastIndexOf(".", endIndex);
                    int lastQuestion = text.lastIndexOf("?", endIndex);
                    int lastExclamation = text.lastIndexOf("!", endIndex);
                    int lastNewline = text.lastIndexOf("\n", endIndex);
                    int lastChinesePeriod = text.lastIndexOf("。", endIndex);
                    int lastChineseQuestion = text.lastIndexOf("？", endIndex);
                    int lastChineseExclamation = text.lastIndexOf("！", endIndex);
                    
                    int lastBreak = Math.max(
                        Math.max(Math.max(lastPeriod, lastQuestion), Math.max(lastExclamation, lastNewline)),
                        Math.max(Math.max(lastChinesePeriod, lastChineseQuestion), lastChineseExclamation)
                    );
                    
                    if (lastBreak > startIndex) {
                        endIndex = lastBreak + 1;
                    }
                }
                
                String chunk = text.substring(startIndex, endIndex);
                String translatedChunk = translateChunk(chunk, targetLanguage);
                result.append(translatedChunk);
                
                startIndex = endIndex;
                
                // 添加适当的间隔
                if (startIndex < text.length()) {
                    result.append(" ");
                }
            }
            
            return result.toString();
        }
        
        return translateChunk(text, targetLanguage);
    }

    private String translateChunk(String text, String targetLanguage) throws Exception {
        // 生成签名
        String salt = String.valueOf(System.currentTimeMillis());
        String sign = generateSign(text, salt);

        // 构建请求参数
        Map<String, String> params = new HashMap<>();
        params.put("q", text);
        params.put("from", "auto");
        params.put("to", targetLanguage);
        params.put("appid", BAIDU_APP_ID);
        params.put("salt", salt);
        params.put("sign", sign);

        log.info("[translate] Sending request to Baidu API for chunk length: {}", text.length());
        
        // 发送请求
        String response = sendRequest(params);
        
        // 解析响应
        try {
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.has("error_code")) {
                String errorCode = jsonResponse.getString("error_code");
                String errorMsg = jsonResponse.getString("error_msg");
                log.error("[translate] Baidu API error: {} - {}", errorCode, errorMsg);
                throw new Exception("Translation error: " + errorCode + " - " + errorMsg);
            }

            if (!jsonResponse.has("trans_result")) {
                log.error("[translate] Invalid response format: missing trans_result. Response: {}", response);
                throw new Exception("Invalid response format from translation API");
            }

            JSONArray transResult = jsonResponse.getJSONArray("trans_result");
            if (transResult.length() == 0) {
                log.error("[translate] No translation result in response: {}", response);
                throw new Exception("No translation result");
            }

            String translatedText = transResult.getJSONObject(0).getString("dst");
            log.info("[translate] Successfully translated chunk, result length: {}", translatedText.length());
            return translatedText;
        } catch (JSONException e) {
            log.error("[translate] Error parsing response: {} - Response: {}", e.getMessage(), response);
            throw new Exception("Error parsing translation response: " + e.getMessage());
        }
    }

    private String generateSign(String text, String salt) throws Exception {
        String str = BAIDU_APP_ID + text + salt + BAIDU_SECRET_KEY;
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest(str.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String sendRequest(Map<String, String> params) throws IOException {
        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (postData.length() != 0) {
                postData.append('&');
            }
            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            postData.append('=');
            postData.append(URLEncoder.encode(param.getValue(), "UTF-8"));
        }

        URL url = new URL(BAIDU_TRANSLATE_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", String.valueOf(postData.length()));
        conn.setConnectTimeout(10000); // Increased timeout to 10 seconds
        conn.setReadTimeout(10000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData.toString().getBytes("UTF-8"));
            os.flush();
        } catch (IOException e) {
            log.error("[translate] Error writing request to server: {}", e.getMessage());
            throw new IOException("Error writing to server: " + e.getMessage(), e);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorMessage = "HTTP error code: " + responseCode;
            try {
                // Try to read error response
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    if (errorResponse.length() > 0) {
                        errorMessage += " - Response: " + errorResponse.toString();
                    }
                }
            } catch (Exception e) {
                log.warn("[translate] Could not read error response: {}", e.getMessage());
            }
            log.error("[translate] {}", errorMessage);
            throw new IOException(errorMessage);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        String responseString = response.toString().trim();
        log.info("[translate] Baidu API response: {}", responseString);

        if (responseString.isEmpty()) {
            throw new IOException("Empty response from translation API");
        }

        return responseString;
    }
} 