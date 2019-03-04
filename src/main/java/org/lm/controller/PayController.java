package org.lm.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: LM
 * @create: 2019-03-03 20:45
 **/
@RestController
public class PayController {
    MyWXPayUtils payUtils=new MyWXPayUtils();
    // 生成二维码支付链接
    @RequestMapping("/a1")
    public Result createNative(String orderId){
        Result result=payUtils.generateOrder(orderId, "1");
//        System.out.println("后台-获得支付链接:"+result.getSuccess()+"-----"+result.getMsg());
        return result;
    }
    @RequestMapping("/a2")
    public Result queryPayStatus(String orderId){
        Result result=payUtils.queryOrder(orderId);
//        System.out.println(new SimpleDateFormat("mm分ss秒SSS毫秒").format(new Date())+"后台-查询支付状态:"+result.getSuccess()+"-----"+result.getMsg());
        return result;
    }


}
