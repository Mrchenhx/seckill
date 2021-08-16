package org.example.controller;

import com.google.common.util.concurrent.RateLimiter;
import org.apache.commons.lang3.StringUtils;
import org.example.annotation.JwtToken;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.mq.MqProducer;
import org.example.response.CommonReturnType;
import org.example.service.ItemService;
import org.example.service.OrderService;
import org.example.service.PromoService;
import org.example.service.model.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.*;


@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class OrderController extends BaseController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Resource
    private MqProducer mqProducer;

    @Resource
    private ItemService itemService;
    @Resource
    private PromoService promoService;

    private ExecutorService executorService;

    private RateLimiter orderCreateRateLimiter;


    @PostConstruct
    public void init(){
        // executorService = Executors.newFixedThreadPool(20);
        executorService = new ThreadPoolExecutor(20, 30,
                10,TimeUnit.MINUTES, new ArrayBlockingQueue<>(50));
        orderCreateRateLimiter = RateLimiter.create(1);
    }

    //封装下单请求
    @JwtToken
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name="itemId")Integer itemId,
                                        @RequestParam(name="amount")Integer amount,
                                        @RequestParam(name="promoId",required = false)Integer promoId,
                                        @RequestParam(name="promoToken",required = false)String promoToken) throws BusinessException {
        // 完善系统：非秒杀商品走正常价格，再写一个controller处理下单逻辑

        // 令牌桶算法，限流
        if(!orderCreateRateLimiter.tryAcquire()){
            throw new BusinessException(EmBusinessError.RATELIMIT);
        }
        // 用户token拼接在url上，直接再url上进行解析获取
        String token = httpServletRequest.getParameterMap().get("token")[0];

        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        }

        //获取用户的登陆信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        }

        // 校验秒杀令牌是否正确
        if(promoId != null){
            String inRedisPromoToken = (String) redisTemplate.opsForValue()
                    .get("promo_token_" + promoId + "_userid_"  +
                            userModel.getId() + "_itemid_"+ itemId);
            if(inRedisPromoToken == null){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,
                        "秒杀令牌校验失败");
            }
            if(!StringUtils.equals(promoToken, inRedisPromoToken)){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,
                        "秒杀令牌校验失败");
            }
        }
        // 已售罄则不执行下单逻辑
        if(redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }


        // 同步调用线程池的submit方法
        // 拥塞窗口为20的等待队列，用来队列化泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //加入库存流水init状态
                String stockLogId = itemService.initStockLog(itemId,amount);
                // 再完成事对应的下单务型消息机制
                if(!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)){
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"下单失败");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }

        return CommonReturnType.create(null);
    }
    //生成秒杀令牌
    @JwtToken
    @RequestMapping(value = "/generatetoken",method = {RequestMethod.POST},consumes={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generatetoken(@RequestParam(name="itemId")Integer itemId,
                                          @RequestParam(name="promoId")Integer promoId) throws BusinessException {
        // 完善系统：非秒杀商品，在redis中查是否存在，不存在则返回空

        // 根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }
        // 获取用户登录信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        }
        // 获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());
        if(promoToken == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");
        }

        return CommonReturnType.create(promoToken);
    }
}
