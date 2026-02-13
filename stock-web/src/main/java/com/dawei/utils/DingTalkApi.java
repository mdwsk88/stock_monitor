package com.dawei.utils;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.dawei.entity.USStockMsg;
import com.taobao.api.ApiException;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @ClassName DingTalkApi
 * @Author 风间影月
 * @Version 1.0
 * @Description DingTalkApi
 **/
@Service
public class DingTalkApi {

    @Value("${dingding.token}")
    private String customRobotToken;
    @Value("${dingding.secret}")
    private String secret;
    @Value("${dingding.userid}")
    private String userId;

    // 添加了一个无参构造器，不然无法注入这个bean，否则需要额外的配置。
    public DingTalkApi() {}

    public DingTalkApi(String customRobotToken, String secret, String userId) {
        this.customRobotToken = customRobotToken;
        this.secret = secret;
        this.userId = userId;
    }

    /**
     * @Description: 格式化消息的内容
     * @Author 风间影月
     * @param stockMsg
     */
    public String formatStockInfo(USStockMsg stockMsg) {
        return
                "📌 代码：" + stockMsg.getStockCode() + "\n" +
                "📅 时间：" + stockMsg.getPubDateBj() + "\n" +
                "📰 标题（英文）：" + stockMsg.getTitle() + "\n" +
                "📰 标题（中文）：" + stockMsg.getTitleZh() + "\n" +
                "🏷️ 标签：" + stockMsg.getTags() + "\n" +
                "📊 统计：24小时异动=" + stockMsg.getCounts24Hour() + "次; " +
                "3天内异动=" + stockMsg.getCounts3Day() + "次; " +
                "1周内异动=" + stockMsg.getCounts1Week() + "次"
                ;
    }

    public String formatStockInfoFromList(List<USStockMsg> stocks) {
        return stocks.stream().map(this::formatStockInfo).collect(Collectors.joining("\n\n----------\n\n"));
    }

    public void sendTextMessage(String textContent) {
        try {
            Long timestamp = System.currentTimeMillis();
            System.out.println(timestamp);
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
            String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), "UTF-8");
            System.out.println(sign);

            //sign字段和timestamp字段必须拼接到请求URL上，否则会出现 310000 的错误信息
            DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/robot/send?sign=" + sign + "&timestamp=" + timestamp);
            OapiRobotSendRequest req = new OapiRobotSendRequest();
            /**
             * 发送文本消息
             */
            //定义文本内容
            OapiRobotSendRequest.Text text = new OapiRobotSendRequest.Text();
            text.setContent(textContent);
            //定义 @ 对象
            OapiRobotSendRequest.At at = new OapiRobotSendRequest.At();
            at.setAtUserIds(Arrays.asList(userId));
            //设置消息类型
            req.setMsgtype("text");
            req.setText(text);
            req.setAt(at);
            OapiRobotSendResponse rsp = client.execute(req, customRobotToken);
            System.out.println(rsp.getBody());
        } catch (ApiException e) {
            // 钉钉API调用异常，可能是网络问题、token无效或请求参数错误
            throw new RuntimeException("钉钉机器人消息发送失败，API调用异常: " + e.getErrCode() + " - " + e.getErrMsg(), e);
        } catch (UnsupportedEncodingException e) {
            // 字符编码异常，UTF-8编码不被支持（理论上不会发生，因为UTF-8是Java标准支持编码）
            throw new RuntimeException("钉钉消息签名失败，不支持的字符编码: UTF-8", e);
        } catch (NoSuchAlgorithmException e) {
            // HmacSHA256算法不可用（理论上不会发生，因为HmacSHA256是Java标准算法）
            throw new RuntimeException("钉钉消息签名失败，HmacSHA256算法不可用", e);
        } catch (InvalidKeyException e) {
            // 密钥格式无效，可能是secret为空或格式不正确
            throw new RuntimeException("钉钉消息签名失败，密钥无效，请检查secret配置", e);
        }
    }
}
