package org.lm.controller;

import com.github.wxpay.sdk.WXPayUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * <dependency>
 *    <groupId>com.github.wxpay</groupId>
 *    <artifactId>wxpay-sdk</artifactId>
 *    <version>3.0.9</version>  最新版本，去官网下载sdk安装到本地仓库
 * </dependency>
 * https://pay.weixin.qq.com/wiki/doc/api/index.html
 */
public class MyWXPayUtils {
    private final String GEN_ORDER_URL="https://api.mch.weixin.qq.com/pay/unifiedorder";	// 生成支付链接
    private final String QUERY_ORDER_URL="https://api.mch.weixin.qq.com/pay/orderquery";	// 查询支付状况
    private final String CLOSE_ORDER_URL="https://api.mch.weixin.qq.com/pay/closeorder";	// 关闭订单（一般用不上）
    // 公众账号ID
    private String appid="wx8397f8696b538317";
    // 商户号
    private String mch_id="1473426802";
    // 商户密钥
    private String partnerkey="T6m9iK73b0kn9g5v426MKfHQH7X8rKwb";
    // 回调URL(这里用不到，但是是个必填参数)
    private String notifyurl = "http://a31ef7db.ngrok.io/WeChatPay/WeChatPayNotify";
    // 订单失效时间(ms),wx规定了至少要1分钟
    private long timeout=1*60*1000;
    // 根据api将时间转换为规定的字符串格式
    private String convertTime(long time){
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date(time));
    }

    /**
     * 根据订单号和金额生成微信支付链接
     * @param out_trade_no  支付订单号，不要超过32位
     * @param money_fen     支付金额    单位为分!!!
     * @return  正常时：{success=true, msg='weixin://wxpay/bizpayurl?pr=HRJxQwO&groupid=00'}
     */
    public Result generateOrder(String out_trade_no,String money_fen) {
        // 1.封装参数
        Map param=new HashMap();
        param.put("appid", appid);
        param.put("mch_id",mch_id );
        param.put("nonce_str", WXPayUtil.generateNonceStr());  //随机字符串
        param.put("body", "LM微信订单");                        //商品描述
        param.put("out_trade_no", out_trade_no);
        param.put("total_fee", money_fen);
        param.put("spbill_create_ip","127.0.0.1");              //终端IP
        param.put("notify_url",notifyurl );
        param.put("trade_type","NATIVE" );                      //交易类型
        String begin=convertTime(System.currentTimeMillis());
        String end=convertTime(System.currentTimeMillis()+timeout);
        System.out.println("begin："+begin);
        System.out.println("end："+end);
        param.put("time_start", begin);    // 订单开始时间
        param.put("time_expire",end );    // 订单失效时间
        String signedXml=null;    //结合之前参数最终生成签名
        try {
            signedXml=WXPayUtil.generateSignedXml(param, partnerkey);
//            System.out.println("【signedXml】"+signedXml);
        } catch (Exception e) {
            return new Result(false, "签名出错");
        }
        System.out.println("生成的签名");
        // 2.发送信息
            HttpClient httpClient=new HttpClient(GEN_ORDER_URL);
            httpClient.setHttps(true);
            httpClient.setXmlParam(signedXml);
        Map<String, String> map=null;
        try {
            httpClient.post();
            // 3.获取返回结果
            String content=httpClient.getContent();
//            System.out.println("结果："+content);
            // 3.1 将xml字符串转换为map
            map=WXPayUtil.xmlToMap(content);
        } catch (Exception e) {
            return new Result(false, "网络故障");
        }
        if("FAIL".equals(map.get("return_code")))
            return new Result(false, map.get("return_msg"));
        if("FAIL".equals(map.get("result_code")))
            return new Result(false, map.get("err_code_des"));
        return new Result(true, map.get("code_url"));
    }

    /**
     * 查询订单支付状态
     * @param out_trade_no  支付订单号（长度32位以内）
     * @return
     */
    public Result queryOrder(String out_trade_no) {
        // 1.封装参数
        Map param=new HashMap();
        param.put("appid", appid);
        param.put("mch_id",mch_id );
        param.put("out_trade_no", out_trade_no);
        param.put("nonce_str", WXPayUtil.generateNonceStr());
        String signedXml=null;
        try {
            signedXml=WXPayUtil.generateSignedXml(param, partnerkey);
        } catch (Exception e) {
            return new Result(false, "签名出错");
        }
        // 2.发送信息
        HttpClient httpClient=new HttpClient(QUERY_ORDER_URL);
        httpClient.setHttps(true);
        httpClient.setXmlParam(signedXml);

        Map<String, String> map=null;
        try {
            httpClient.post();
            // 3.获取返回结果
            String content=httpClient.getContent();
            map=WXPayUtil.xmlToMap(content);
        } catch (Exception e) {
            return new Result(false, "网络故障");
        }
        // 3.1解析结果
        if("FAIL".equals(map.get("return_code")))
            return new Result(false, map.get("return_msg"));
        if("FAIL".equals(map.get("result_code")))
            return new Result(false, map.get("err_code_des"));
        if("SUCCESS".equals(map.get("trade_state")))
            return new Result(true, "支付成功");
        else
            return new Result(false, map.get("trade_state_desc"));

    }



    /**
     * 关闭对应支付订单 
     * 场景一：比如订单30分钟未支付，就关闭订单不再让用户能支付，同时不能再根据原支付订单号重新生成
     * 场景二：订单支付失败（不是未支付）,要关闭原订单	 
     * @param out_trade_no
     * @return 正常时：{success=true, msg='支付成功'}
	 * 注意一种情况：返回结果为{success=false, msg='订单已支付'}！！！
     */
    public Result closeOrder(String out_trade_no){
        // 1.封装参数
        Map param=new HashMap();
        param.put("appid", appid);
        param.put("mch_id",mch_id );
        param.put("out_trade_no", out_trade_no);
        param.put("nonce_str", WXPayUtil.generateNonceStr());  //随机字符串
        String signedXml=null;    //结合之前参数最终生成签名
        try {
            signedXml=WXPayUtil.generateSignedXml(param, partnerkey);
        } catch (Exception e) {
            return new Result(false, "签名出错");
        }
        // 2.发送信息
        HttpClient httpClient=new HttpClient(CLOSE_ORDER_URL);
        httpClient.setHttps(true);
        httpClient.setXmlParam(signedXml);
        Map<String, String> map=null;
        try {
            httpClient.post();
            // 3.获取返回结果
            String content=httpClient.getContent();
            // 3.1 将xml字符串转换为map
            map=WXPayUtil.xmlToMap(content);
        } catch (Exception e) {
            return new Result(false, "网络故障");
        }
        // 3.1解析结果
        if("FAIL".equals(map.get("return_code")))
            return new Result(false, map.get("return_msg"));
        if("FAIL".equals(map.get("result_code")))
            return new Result(false, map.get("err_code_des"));
		return new Result(true, "订单关闭了");
    }

/*    public static void main(String[] args) {
        MyWXPayUtils myWXPayUtils=new MyWXPayUtils();
        Result result=myWXPayUtils.generateOrder("20181221034900", "1");
        Result result3=myWXPayUtils.generateOrder("20181221034900", "1");
        Result result4=myWXPayUtils.queryOrder("20181221034900");
        Result result5=myWXPayUtils.closeOrder("20181221034900"); //关闭订单用不上
        System.out.println(result);
        System.out.println(result3);
        System.out.println(result4);
        System.out.println(result5);
        // weixin://wxpay/bizpayurl?pr=IYSluQd&groupid=00
        // weixin://wxpay/bizpayurl?pr=XkWsHBX&groupid=00
    }*/

}

/**
 * result结果类
 */
class Result{
    private Boolean success;
    private String msg;

    public Result() {}
    public Result(Boolean success, String msg) {
        this.success=success;
        this.msg=msg;
    }

    public Boolean getSuccess() {
        return success;
    }
    public void setSuccess(Boolean success) {
        this.success=success;
    }
    public String getMsg() {
        return msg;
    }
    public void setMsg(String msg) {
        this.msg=msg;
    }

    @Override
    public String toString() {
        return "Result{" +
                "success=" + success +
                ", msg='" + msg + '\'' +
                '}';
    }
}
