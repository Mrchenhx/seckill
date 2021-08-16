package org.example.service.impl;


import org.example.dao.PromoDOMapper;
import org.example.dataobject.PromoDO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.service.ItemService;
import org.example.service.PromoService;
import org.example.service.UserService;
import org.example.service.model.ItemModel;
import org.example.service.model.PromoModel;
import org.example.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class PromoServiceImpl implements PromoService {

    @Resource
    private PromoDOMapper promoDOMapper;
    @Autowired
    private ItemService itemService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Resource
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }

        System.out.println(promoModel);
        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        // 通过活动id获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0) {
            return; // 无效活动
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
        // 同步库存到 redis 中
        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(), itemModel.getStock());
        // 将大闸的控制数字设到 redis 内
        redisTemplate.opsForValue().set("promo_door_count_"+promoId, itemModel.getStock()*5);
    }

    @Override
    public String generateSecondKillToken(Integer promoId, Integer itemId, Integer userId) {
        // 判断秒杀商品是否已售罄。存在则说明已售罄
        if(redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)){
            return null;
        }
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        PromoModel promoModel = convertFromDataObject(promoDO);
        // 秒杀商品不存在
        if(promoModel == null){
            return null;
        }
        // 判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        // 判断活动是否正在进行
        if(promoModel.getStatus().intValue() != 2) {
            return null;
        }
        // 判断item信息是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            return null;
        }
        //判断用户信息是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null){
            return null;
        }

        // 获取秒杀大闸的count数量，防止过多获取token浪费资源
        Long result = redisTemplate.opsForValue().increment("promo_door_count_" + promoId, -1);
        // 超过大闸数量
        if(result < 0){
            return null;
        }

        // 生成token并且存入redis内并给一个5分钟的有效期
        String token = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue()
                .set("promo_token_" + promoId + "_userid_"  + userId + "_itemid_"+ itemId,
                        token);
        redisTemplate.expire("promo_token_" + promoId + "_userid_"  + userId + "_itemid_"+ itemId,
                5, TimeUnit.MINUTES);
        return token;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO, promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }
}
