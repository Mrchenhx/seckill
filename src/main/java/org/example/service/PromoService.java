package org.example.service;


import org.example.service.model.PromoModel;



public interface PromoService {
    //根据itemid获取即将进行的或正在进行的秒杀活动
    PromoModel getPromoByItemId(Integer itemId);
    // 发布秒杀活动
    void publishPromo(Integer promoId);
    //生成秒杀用的令牌
    String generateSecondKillToken(Integer promoId, Integer itemId, Integer userId);
}
